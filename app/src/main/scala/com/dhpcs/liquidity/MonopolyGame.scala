package com.dhpcs.liquidity

import java.util.{Currency, Locale}

import android.content.{ContentUris, ContentValues, Context}
import com.dhpcs.liquidity.MonopolyGame._
import com.dhpcs.liquidity.ServerConnection.{ConnectionStateListener, NotificationListener, ResponseCallback}
import com.dhpcs.liquidity.models._
import com.dhpcs.liquidity.provider.LiquidityContract
import com.dhpcs.liquidity.provider.LiquidityContract.Games
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

object MonopolyGame {

  sealed trait Player extends Serializable {

    def memberId: MemberId

    def member: Member

    def accountId: AccountId

    def account: Account

  }

  sealed trait Identity extends Player {

    def isBanker: Boolean

  }

  sealed trait Transfer extends Serializable {

    def creator: Either[(MemberId, Member), Player]

    def from: Either[(AccountId, Account), Player]

    def to: Either[(AccountId, Account), Player]

    def transactionId: TransactionId

    def transaction: Transaction

  }

  case class PlayerWithBalanceAndConnectionState(memberId: MemberId,
                                                 member: Member,
                                                 accountId: AccountId,
                                                 account: Account,
                                                 balanceWithCurrency:
                                                 (BigDecimal, Option[Either[String, Currency]]),
                                                 isConnected: Boolean) extends Player

  case class IdentityWithBalance(memberId: MemberId,
                                 member: Member,
                                 accountId: AccountId,
                                 account: Account,
                                 balanceWithCurrency:
                                 (BigDecimal, Option[Either[String, Currency]]),
                                 isBanker: Boolean)
    extends Identity

  case class TransferWithCurrency(from: Either[(AccountId, Account), Player],
                                  to: Either[(AccountId, Account), Player],
                                  creator: Either[(MemberId, Member), Player],
                                  transactionId: TransactionId,
                                  transaction: Transaction,
                                  currency: Option[Either[String, Currency]])
    extends Transfer

  trait Listener {

    def onErrorResponse(errorResponse: ErrorResponse)

    def onGameNameChanged(name: String)

    def onIdentitiesUpdated(identities: Map[MemberId, IdentityWithBalance])

    def onIdentityCreated(identity: IdentityWithBalance)

    def onIdentityReceived(identity: IdentityWithBalance)

    def onIdentityRequired()

    def onIdentityRestored(identity: IdentityWithBalance)

    def onJoined(zoneId: ZoneId)

    def onPlayersAdded(addedPlayers: Iterable[PlayerWithBalanceAndConnectionState])

    def onPlayersChanged(changedPlayers: Iterable[PlayerWithBalanceAndConnectionState])

    def onPlayersRemoved(removedPlayers: Iterable[PlayerWithBalanceAndConnectionState])

    def onPlayersUpdated(players: Map[MemberId, PlayerWithBalanceAndConnectionState])

    def onQuit()

    def onTransfersAdded(addedTransfers: Iterable[TransferWithCurrency])

    def onTransfersChanged(changedTransfers: Iterable[TransferWithCurrency])

    def onTransfersUpdated(transfers: Map[TransactionId, TransferWithCurrency])

  }

  private val ZoneType = MONOPOLY

  private def currencyFromMetadata(metadata: Option[JsObject]) =
    metadata.flatMap(
      _.value.get("currency").flatMap(
        _.asOpt[String].map(currencyCode =>
          Try(
            Currency.getInstance(currencyCode)
          ).toOption.fold[Either[String, Currency]](Left(currencyCode))(Right(_))
        )
      )
    )

  private def identitiesFromMembersAccounts(membersAccounts: Map[MemberId, AccountId],
                                            accounts: Map[AccountId, Account],
                                            balances: Map[AccountId, BigDecimal],
                                            currency: Option[Either[String, Currency]],
                                            members: Map[MemberId, Member],
                                            clientPublicKey: PublicKey,
                                            equityAccountId: AccountId) =
    membersAccounts.collect {
      case (memberId, accountId) if members(memberId).publicKey == clientPublicKey =>

        memberId -> IdentityWithBalance(
          memberId,
          members(memberId),
          accountId,
          accounts(accountId),
          (balances(accountId).bigDecimal, currency),
          accountId == equityAccountId
        )

    }.partition { case (_, identity) =>
      !isHidden(identity.member)
    }

  private def isHidden(member: Member) =
    member.metadata.fold(false)(
      _.value.get("hidden").fold(false)(
        _.asOpt[Boolean].getOrElse(false)
      )
    )

  private def membersAccountsFromAccounts(accounts: Map[AccountId, Account]) =
    accounts.filter { case (_, account) =>
      account.owners.size == 1
    }.groupBy { case (_, account) =>
      account.owners.head
    }.collect { case (memberId, memberAccounts) if memberAccounts.size == 1 =>
      val (accountId, _) = memberAccounts.head
      memberId -> accountId
    }

  private def playersFromMembersAccounts(membersAccounts: Map[MemberId, AccountId],
                                         accounts: Map[AccountId, Account],
                                         balances: Map[AccountId, BigDecimal],
                                         currency: Option[Either[String, Currency]],
                                         members: Map[MemberId, Member],
                                         connectedPublicKeys: Set[PublicKey]) =
    membersAccounts.map {
      case (memberId, accountId) =>

        val member = members(memberId)
        memberId -> PlayerWithBalanceAndConnectionState(
          memberId,
          member,
          accountId,
          accounts(accountId),
          (balances(accountId).bigDecimal, currency),
          connectedPublicKeys.contains(member.publicKey)
        )

    }.partition { case (_, identity) =>
      !isHidden(identity.member)
    }

  private def transfersFromTransactions(transactions: Map[TransactionId, Transaction],
                                        currency: Option[Either[String, Currency]],
                                        accountsMembers: Map[AccountId, MemberId],
                                        players: Map[MemberId, Player],
                                        accounts: Map[AccountId, Account],
                                        members: Map[MemberId, Member]) =
    transactions.map {
      case (transactionId, transaction) =>

        val from = accountsMembers.get(transaction.from)
          .fold[Either[(AccountId, Account), Player]](
            Left(transaction.from -> accounts(transaction.from))
          )(memberId => Right(players(memberId)))
        val to = accountsMembers.get(transaction.to)
          .fold[Either[(AccountId, Account), Player]](
            Left(transaction.to -> accounts(transaction.to))
          )(memberId => Right(players(memberId)))
        val creator = players.get(transaction.creator)
          .fold[Either[(MemberId, Member), Player]](
            Left(transaction.creator -> members(transaction.creator))
          )(Right(_))
        transactionId -> TransferWithCurrency(
          from,
          to,
          creator,
          transactionId,
          transaction,
          currency
        )

    }

}

class MonopolyGame(context: Context)
  extends ConnectionStateListener with NotificationListener {

  private trait ResponseCallbackWithErrorForwarding extends ServerConnection.ResponseCallback {

    override def onErrorReceived(errorResponse: ErrorResponse) =
      listener.foreach(_.onErrorResponse(errorResponse))

  }

  // TODO: Review all other project Scala classes for non-private fields and class methods that
  // could be companion object functions

  private val log = LoggerFactory.getLogger(getClass)

  private val serverConnection = new ServerConnection(context, this, this)
  private val noopResponseCallback = new ResponseCallback with ResponseCallbackWithErrorForwarding

  // TODO
  private var gameId = Option.empty[Future[Long]]
  private var zoneId = Option.empty[ZoneId]

  // TODO
  private var zone: Zone = _
  private var connectedClients: Set[PublicKey] = _
  private var balances: Map[AccountId, BigDecimal] = _
  private var currency: Option[Either[String, Currency]] = _
  private var memberIdsToAccountIds: Map[MemberId, AccountId] = _
  private var accountIdsToMemberIds: Map[AccountId, MemberId] = _
  private var identities: Map[MemberId, IdentityWithBalance] = _
  private var hiddenIdentities: Map[MemberId, IdentityWithBalance] = _
  private var players: Map[MemberId, PlayerWithBalanceAndConnectionState] = _
  private var hiddenPlayers: Map[MemberId, PlayerWithBalanceAndConnectionState] = _
  private var transfers: Map[TransactionId, TransferWithCurrency] = _

  private var listener = Option.empty[Listener]

  def connectCreateAndOrJoinZone() =
    if (!serverConnection.isConnectingOrConnected) {
      serverConnection.connect()
    }

  private def createAccount(owner: MemberId) {
    serverConnection.sendCommand(
      CreateAccountCommand(
        zoneId.get,
        Account(
          "capital",
          Set(owner)
        )
      ),
      noopResponseCallback
    )
  }

  private def createAndThenJoinZone() {
    serverConnection.sendCommand(
      CreateZoneCommand(
        context.getString(R.string.new_monopoly_game_name),
        Member(
          context.getString(R.string.bank_member_name),
          ClientKey.getInstance(context).getPublicKey
        ),
        Account(
          context.getString(R.string.bank_member_name),
          Set.empty
        ),
        Some(
          Json.obj(
            "type" -> ZoneType.name,
            "currency" -> Currency.getInstance(Locale.getDefault).getCurrencyCode
          )
        )
      ),
      new ResponseCallbackWithErrorForwarding {

        override def onResultReceived(resultResponse: ResultResponse) {
          log.debug("resultResponse={}", resultResponse)

          val createZoneResponse = resultResponse.asInstanceOf[CreateZoneResponse]

          zoneId = Some(createZoneResponse.zoneId)

          join(createZoneResponse.zoneId)

        }

      }
    )
  }

  def createIdentity(name: String) {
    if (identities.size == 1 && identities.values.head.accountId == zone.equityAccountId) {
      setGameName(context.getString(R.string.game_name_format_string, name))
    }
    serverConnection.sendCommand(
      CreateMemberCommand(
        zoneId.get,
        Member(
          name,
          ClientKey.getInstance(context).getPublicKey
        )
      ),
      new ResponseCallbackWithErrorForwarding {

        override def onResultReceived(resultResponse: ResultResponse) {
          log.debug("resultResponse={}", resultResponse)

          val createMemberResponse = resultResponse.asInstanceOf[CreateMemberResponse]

          createAccount(createMemberResponse.memberId)

        }

      }
    )
  }

  def deleteIdentity(identity: Identity) {
    val member = zone.members(identity.memberId)
    serverConnection.sendCommand(
      UpdateMemberCommand(
        zoneId.get,
        identity.memberId,
        member.copy(
          metadata = Some(
            member.metadata.getOrElse(Json.obj()) ++ Json.obj("hidden" -> true)
          )
        )
      ),
      noopResponseCallback
    )
  }

  private def disconnect() = serverConnection.disconnect()

  // TODO
  def getCurrency = currency

  def getHiddenIdentities = hiddenIdentities.values

  def getIdentities = identities.values

  def isPublicKeyConnectedAndImplicitlyValid(publicKey: PublicKey) =
    connectedClients.contains(publicKey)

  private def join(zoneId: ZoneId) {
    serverConnection.sendCommand(
      JoinZoneCommand(
        zoneId
      ),
      new ResponseCallbackWithErrorForwarding {

        override def onResultReceived(resultResponse: ResultResponse) {
          log.debug("resultResponse={}", resultResponse)

          val joinZoneResponse = resultResponse.asInstanceOf[JoinZoneResponse]

          if (joinZoneResponse.zone.metadata.fold(false)(metadata =>
            metadata.value.get("type").fold(false)(
              _.asOpt[String].contains(ZoneType.name)
            ))) {
            // TODO
          }

          listener.foreach(_.onJoined(zoneId))

          zone = joinZoneResponse.zone
          connectedClients = joinZoneResponse.connectedClients
          balances = Map.empty.withDefaultValue(BigDecimal(0))
          currency = currencyFromMetadata(zone.metadata)

          listener.foreach(_.onGameNameChanged(zone.name))

          val iterator = zone.transactions.valuesIterator
          while (iterator.hasNext) {
            val transaction = iterator.next()
            balances = balances +
              (transaction.from -> (balances(transaction.from) - transaction.value)) +
              (transaction.to -> (balances(transaction.to) + transaction.value))
          }

          memberIdsToAccountIds = membersAccountsFromAccounts(zone.accounts)
          accountIdsToMemberIds = memberIdsToAccountIds.map(_.swap)

          val (updatedIdentities, updatedHiddenIdentities) = identitiesFromMembersAccounts(
            memberIdsToAccountIds,
            zone.accounts,
            balances,
            currency,
            zone.members,
            ClientKey.getInstance(context).getPublicKey,
            zone.equityAccountId
          )

          identities = updatedIdentities
          listener.foreach(_.onIdentitiesUpdated(identities))

          hiddenIdentities = updatedHiddenIdentities

          val (updatedPlayers, updatedHiddenPlayers) = playersFromMembersAccounts(
            memberIdsToAccountIds,
            zone.accounts,
            balances,
            currency,
            zone.members,
            connectedClients
          )

          players = updatedPlayers
          listener.foreach(_.onPlayersAdded(players.values))
          listener.foreach(_.onPlayersUpdated(players))

          hiddenPlayers = updatedHiddenPlayers

          transfers = transfersFromTransactions(
            zone.transactions,
            currency,
            accountIdsToMemberIds,
            players ++ hiddenPlayers,
            zone.accounts,
            zone.members
          )

          listener.foreach(_.onTransfersAdded(transfers.values))
          listener.foreach(_.onTransfersUpdated(transfers))

          val partiallyCreatedIdentities = zone.members.filter { case (memberId, member) =>
            ClientKey.getInstance(context).getPublicKey == member.publicKey &&
              !zone.accounts.values.exists(_.owners == Set(memberId))
          }
          partiallyCreatedIdentities.keys.foreach(createAccount)

          // TODO: Chain this so it only happens after partially created identities have had their
          // accounts created.
          // TODO: Revert gameId check but also check for deleted identities? Restore persistence of
          // gameId to join response handler?
          if (gameId.isEmpty && !identities.values.exists(_.accountId != zone.equityAccountId)) {
            listener.foreach(_.onIdentityRequired())
          }

          gameId = gameId.fold(
            Some(
              Future {
                val zone = joinZoneResponse.zone
                val contentValues = new ContentValues
                contentValues.put(Games.GAME_TYPE, MONOPOLY.name)
                contentValues.put(Games.ZONE_ID, zoneId.id.toString)
                contentValues.put(Games.NAME, zone.name)
                contentValues.put(Games.CREATED, zone.created: java.lang.Long)
                ContentUris.parseId(
                  context.getContentResolver.insert(
                    Games.CONTENT_URI,
                    contentValues
                  )
                )
              }
            )
          ) { gameId =>
            if (zone == null || zone.name != joinZoneResponse.zone.name) {
              gameId.onSuccess { case id =>
                Future {
                  val contentValues = new ContentValues
                  contentValues.put(Games.NAME, joinZoneResponse.zone.name)
                  context.getContentResolver.update(
                    ContentUris.withAppendedId(Games.CONTENT_URI, id),
                    contentValues,
                    null,
                    null
                  )
                }
              }
            }
            Some(gameId)
          }

        }

      }
    )
  }

  override def onNotificationReceived(notification: Notification) {
    log.debug("notification={}", notification)

    notification match {

      case zoneNotification: ZoneNotification =>

        val zoneId = this.zoneId.get

        if (zoneId != zoneNotification.zoneId) {
          sys.error(s"zoneId != zoneNotification.zoneId (${zoneNotification.zoneId} != $zoneId)")
        }

        zoneNotification match {

          case clientJoinedZoneNotification: ClientJoinedZoneNotification =>

            connectedClients = connectedClients + clientJoinedZoneNotification.publicKey

            val (joinedPlayers, joinedHiddenPlayers) = playersFromMembersAccounts(
              memberIdsToAccountIds.filterKeys(
                zone.members(_).publicKey == clientJoinedZoneNotification.publicKey
              ),
              zone.accounts,
              balances,
              currency,
              zone.members,
              Set(clientJoinedZoneNotification.publicKey)
            )

            if (joinedPlayers.nonEmpty) {
              players = players ++ joinedPlayers
              listener.foreach(_.onPlayersChanged(joinedPlayers.values))
              listener.foreach(_.onPlayersUpdated(players))
            }

            if (joinedHiddenPlayers.nonEmpty) {
              hiddenPlayers = hiddenPlayers ++ joinedHiddenPlayers
            }

          case clientQuitZoneNotification: ClientQuitZoneNotification =>

            connectedClients = connectedClients - clientQuitZoneNotification.publicKey

            val (quitPlayers, quitHiddenPlayers) = playersFromMembersAccounts(
              memberIdsToAccountIds.filterKeys(
                zone.members(_).publicKey == clientQuitZoneNotification.publicKey
              ),
              zone.accounts,
              balances,
              currency,
              zone.members,
              Set.empty
            )

            if (quitPlayers.nonEmpty) {
              players = players ++ quitPlayers
              listener.foreach(_.onPlayersChanged(quitPlayers.values))
              listener.foreach(_.onPlayersUpdated(players))
            }

            if (quitHiddenPlayers.nonEmpty) {
              hiddenPlayers = hiddenPlayers ++ quitHiddenPlayers
            }

          case zoneTerminatedNotification: ZoneTerminatedNotification =>

            zone = null
            connectedClients = null
            balances = null
            currency = null
            identities = null
            players = null

            listener.foreach(_.onQuit())

            join(zoneId)

          case zoneNameSetNotification: ZoneNameSetNotification =>

            zone = zone.copy(name = zoneNameSetNotification.name)

            listener.foreach(_.onGameNameChanged(zoneNameSetNotification.name))

            gameId.foreach(_.onSuccess { case id =>
              Future {
                val contentValues = new ContentValues
                contentValues.put(LiquidityContract.Games.NAME, zoneNameSetNotification.name)
                context.getContentResolver.update(
                  ContentUris.withAppendedId(Games.CONTENT_URI, id),
                  contentValues,
                  null,
                  null
                )
              }
            })

          case memberCreatedNotification: MemberCreatedNotification =>

            zone = zone.copy(
              members = zone.members
                + (memberCreatedNotification.memberId ->
                memberCreatedNotification.member)
            )

          case memberUpdatedNotification: MemberUpdatedNotification =>

            zone = zone.copy(
              members = zone.members
                + (memberUpdatedNotification.memberId ->
                memberUpdatedNotification.member)
            )

            val (updatedIdentities, updatedHiddenIdentities) = identitiesFromMembersAccounts(
              memberIdsToAccountIds,
              zone.accounts,
              balances,
              currency,
              zone.members,
              ClientKey.getInstance(context).getPublicKey,
              zone.equityAccountId
            )

            if (updatedIdentities != identities) {

              val receivedIdentity = if (!identities.contains(memberUpdatedNotification.memberId) &&
                !hiddenIdentities.contains(memberUpdatedNotification.memberId)) {
                updatedIdentities.get(memberUpdatedNotification.memberId)
              } else {
                None
              }

              val restoredIdentity = if (!identities.contains(memberUpdatedNotification.memberId) &&
                hiddenIdentities.contains(memberUpdatedNotification.memberId)) {
                updatedIdentities.get(memberUpdatedNotification.memberId)
              } else {
                None
              }

              identities = updatedIdentities
              listener.foreach(_.onIdentitiesUpdated(identities))

              receivedIdentity.foreach(receivedIdentity =>
                listener.foreach(_.onIdentityReceived(receivedIdentity))
              )

              restoredIdentity.foreach(restoredIdentity =>
                listener.foreach(_.onIdentityRestored(restoredIdentity))
              )

            }

            if (updatedHiddenIdentities != hiddenIdentities) {
              hiddenIdentities = updatedHiddenIdentities
            }

            val (updatedPlayers, updatedHiddenPlayers) = playersFromMembersAccounts(
              memberIdsToAccountIds,
              zone.accounts,
              balances,
              currency,
              zone.members,
              connectedClients
            )

            if (updatedPlayers != players) {
              val addedPlayers = updatedPlayers -- players.keys
              val changedPlayers = updatedPlayers.filter { case (memberId, player) =>
                players.get(memberId).fold(false)(_ != player)
              }
              val removedPlayers = players -- updatedPlayers.keys
              if (addedPlayers.nonEmpty) {
                listener.foreach(_.onPlayersAdded(addedPlayers.values))
              }
              if (changedPlayers.nonEmpty) {
                listener.foreach(_.onPlayersChanged(changedPlayers.values))
              }
              if (removedPlayers.nonEmpty) {
                listener.foreach(_.onPlayersRemoved(removedPlayers.values))
              }
              players = updatedPlayers
              listener.foreach(_.onPlayersUpdated(players))
            }

            if (updatedHiddenPlayers != hiddenPlayers) {
              hiddenPlayers = updatedHiddenPlayers
            }

            val updatedTransfers = transfersFromTransactions(
              zone.transactions,
              currency,
              accountIdsToMemberIds,
              players ++ hiddenPlayers,
              zone.accounts,
              zone.members
            )

            if (updatedTransfers != transfers) {
              val changedTransfers = updatedTransfers.filter { case (transactionId, transfer) =>
                transfers.get(transactionId).fold(false)(_ == transfer)
              }
              if (changedTransfers.nonEmpty) {
                listener.foreach(_.onTransfersChanged(changedTransfers.values))
              }
              transfers = updatedTransfers
              listener.foreach(_.onTransfersUpdated(transfers))
            }

          case accountCreatedNotification: AccountCreatedNotification =>

            zone = zone.copy(
              accounts = zone.accounts
                + (accountCreatedNotification.accountId ->
                accountCreatedNotification.account)
            )

            val createdMembersAccounts = membersAccountsFromAccounts(
              Map(
                accountCreatedNotification.accountId ->
                  zone.accounts(accountCreatedNotification.accountId)
              )
            )

            memberIdsToAccountIds = memberIdsToAccountIds ++ createdMembersAccounts
            accountIdsToMemberIds = accountIdsToMemberIds ++ createdMembersAccounts.map(_.swap)

            val (createdIdentity, createdHiddenIdentity) = identitiesFromMembersAccounts(
              createdMembersAccounts,
              zone.accounts,
              balances,
              currency,
              zone.members,
              ClientKey.getInstance(context).getPublicKey,
              zone.equityAccountId
            )

            if (createdIdentity.nonEmpty) {
              identities = identities ++ createdIdentity
              listener.foreach(_.onIdentitiesUpdated(identities))
              listener.foreach(
                _.onIdentityCreated(identities(accountCreatedNotification.account.owners.head))
              )
            }

            if (createdHiddenIdentity.nonEmpty) {
              hiddenIdentities = hiddenIdentities ++ createdHiddenIdentity
            }

            val (createdPlayer, createdHiddenPlayer) = playersFromMembersAccounts(
              createdMembersAccounts,
              zone.accounts,
              balances,
              currency,
              zone.members,
              connectedClients
            )

            if (createdPlayer.nonEmpty) {
              players = players ++ createdPlayer
              listener.foreach(_.onPlayersAdded(createdPlayer.values))
              listener.foreach(_.onPlayersUpdated(players))
            }

            if (createdHiddenPlayer.nonEmpty) {
              hiddenPlayers = hiddenPlayers ++ createdHiddenPlayer
            }

          case accountUpdatedNotification: AccountUpdatedNotification =>

            zone = zone.copy(
              accounts = zone.accounts
                + (accountUpdatedNotification.accountId ->
                accountUpdatedNotification.account)
            )

            memberIdsToAccountIds = membersAccountsFromAccounts(zone.accounts)
            accountIdsToMemberIds = memberIdsToAccountIds.map(_.swap)

            val (updatedIdentities, updatedHiddenIdentities) = identitiesFromMembersAccounts(
              memberIdsToAccountIds,
              zone.accounts,
              balances,
              currency,
              zone.members,
              ClientKey.getInstance(context).getPublicKey,
              zone.equityAccountId
            )

            if (updatedIdentities != identities) {
              identities = updatedIdentities
              listener.foreach(_.onIdentitiesUpdated(identities))
            }

            if (updatedHiddenIdentities != hiddenIdentities) {
              hiddenIdentities = updatedHiddenIdentities
            }

            val (updatedPlayers, updatedHiddenPlayers) = playersFromMembersAccounts(
              memberIdsToAccountIds,
              zone.accounts,
              balances,
              currency,
              zone.members,
              connectedClients
            )

            if (updatedPlayers != players) {
              val addedPlayers = updatedPlayers -- players.keys
              val changedPlayers = updatedPlayers.filter { case (memberId, player) =>
                players.get(memberId).fold(false)(_ != player)
              }
              val removedPlayers = players -- updatedPlayers.keys
              if (addedPlayers.nonEmpty) {
                listener.foreach(_.onPlayersAdded(addedPlayers.values))
              }
              if (changedPlayers.nonEmpty) {
                listener.foreach(_.onPlayersChanged(changedPlayers.values))
              }
              if (removedPlayers.nonEmpty) {
                listener.foreach(_.onPlayersRemoved(removedPlayers.values))
              }
              players = updatedPlayers
              listener.foreach(_.onPlayersUpdated(players))
            }

            if (updatedHiddenPlayers != hiddenPlayers) {
              hiddenPlayers = updatedHiddenPlayers
            }

            val updatedTransfers = transfersFromTransactions(
              zone.transactions,
              currency,
              accountIdsToMemberIds,
              players ++ hiddenPlayers,
              zone.accounts,
              zone.members
            )

            if (updatedTransfers != transfers) {
              val changedTransfers = updatedTransfers.filter { case (transactionId, transfer) =>
                transfers.get(transactionId).fold(false)(_ == transfer)
              }
              if (changedTransfers.nonEmpty) {
                listener.foreach(_.onTransfersChanged(changedTransfers.values))
              }
              transfers = updatedTransfers
              listener.foreach(_.onTransfersUpdated(transfers))
            }

          case transactionAddedNotification: TransactionAddedNotification =>

            zone = zone.copy(
              transactions = zone.transactions
                + (transactionAddedNotification.transactionId ->
                transactionAddedNotification.transaction)
            )

            val transaction = transactionAddedNotification.transaction

            balances = balances +
              (transaction.from -> (balances(transaction.from) - transaction.value)) +
              (transaction.to -> (balances(transaction.to) + transaction.value))

            val changedMembersAccounts = membersAccountsFromAccounts(
              Map(
                transaction.from -> zone.accounts(transaction.from),
                transaction.to -> zone.accounts(transaction.to)
              )
            )

            val (changedIdentities, changedHiddenIdentities) = identitiesFromMembersAccounts(
              changedMembersAccounts,
              zone.accounts,
              balances,
              currency,
              zone.members,
              ClientKey.getInstance(context).getPublicKey,
              zone.equityAccountId
            )

            if (changedIdentities.nonEmpty) {
              identities = identities ++ changedIdentities
              listener.foreach(_.onIdentitiesUpdated(identities))
            }

            if (changedHiddenIdentities.nonEmpty) {
              hiddenIdentities = hiddenIdentities ++ changedHiddenIdentities
            }

            val (changedPlayers, changedHiddenPlayers) = playersFromMembersAccounts(
              changedMembersAccounts,
              zone.accounts,
              balances,
              currency,
              zone.members,
              connectedClients
            )

            if (changedPlayers.nonEmpty) {
              players = players ++ changedPlayers
              listener.foreach(_.onPlayersChanged(changedPlayers.values))
              listener.foreach(_.onPlayersUpdated(players))
            }

            if (changedHiddenPlayers.nonEmpty) {
              hiddenPlayers = hiddenPlayers ++ changedHiddenPlayers
            }

            val createdTransfer = transfersFromTransactions(
              Map(
                transactionAddedNotification.transactionId ->
                  transactionAddedNotification.transaction
              ),
              currency,
              accountIdsToMemberIds,
              players ++ hiddenPlayers,
              zone.accounts,
              zone.members
            )

            if (createdTransfer.nonEmpty) {
              transfers = transfers ++ createdTransfer
              listener.foreach(_.onTransfersAdded(createdTransfer.values))
              listener.foreach(_.onTransfersUpdated(transfers))
            }

        }

    }

  }

  override def onStateChanged(connectionState: ServerConnection.ConnectionState) {
    log.debug("connectionState={}", connectionState)
    connectionState match {

      case ServerConnection.CONNECTING =>

      case ServerConnection.CONNECTED =>

        zoneId.fold(createAndThenJoinZone())(join)

      case ServerConnection.DISCONNECTING =>

      case ServerConnection.DISCONNECTED =>

      // TODO

    }
  }

  def quitAndOrDisconnect() =
    if (serverConnection.isConnectingOrConnected) {
      if (zone == null) {
        disconnect()
      } else {
        serverConnection.sendCommand(
          QuitZoneCommand(
            zoneId.get
          ),
          new ResponseCallbackWithErrorForwarding {

            override def onResultReceived(resultResponse: ResultResponse) {
              log.debug("resultResponse={}", resultResponse)

              zone = null
              connectedClients = null
              balances = null
              currency = null
              identities = null
              players = null

              listener.foreach(_.onQuit())

              disconnect()

            }

          }
        )
      }
    }

  def restoreIdentity(identity: Identity) {
    val member = zone.members(identity.memberId)
    serverConnection.sendCommand(
      UpdateMemberCommand(
        zoneId.get,
        identity.memberId,
        member.copy(
          metadata = member.metadata.map(_ - "hidden")
        )
      ),
      noopResponseCallback
    )
  }

  def setGameName(name: String) {
    serverConnection.sendCommand(
      SetZoneNameCommand(
        zoneId.get,
        name
      ),
      noopResponseCallback
    )
  }

  def setGameId(gameId: Long) {
    this.gameId = Option(gameId).fold[Option[Future[Long]]](
      None
    )(gameId =>
      Some(Future(gameId))
      )
  }

  def setIdentityName(identity: Identity, name: String) {
    serverConnection.sendCommand(
      UpdateMemberCommand(
        zoneId.get,
        identity.memberId,
        zone.members(identity.memberId).copy(name = name)
      ),
      noopResponseCallback
    )
  }

  def setListener(listener: Listener) {
    this.listener = Option(listener)
    this.listener.foreach(listener =>
      if (zone == null) {
        listener.onQuit()
      } else {
        listener.onJoined(zoneId.get)
        listener.onGameNameChanged(zone.name)
        listener.onIdentitiesUpdated(identities)
        listener.onPlayersAdded(players.values)
        listener.onPlayersUpdated(players)
        listener.onTransfersAdded(transfers.values)
        listener.onTransfersUpdated(transfers)
      }
    )
  }

  def setZoneId(zoneId: ZoneId) {
    this.zoneId = Option(zoneId)
  }

  // TODO: Names
  def transfer(identityId: MemberId,
               newOwnerPublicKey: PublicKey) {
    val identity = identities(identityId)
    serverConnection.sendCommand(
      UpdateMemberCommand(
        zoneId.get,
        identity.memberId,
        identity.member.copy(publicKey = newOwnerPublicKey)
      ),
      noopResponseCallback
    )
  }

  def transfer(actingAs: Identity,
               from: Identity,
               to: Player,
               value: BigDecimal) {
    serverConnection.sendCommand(
      AddTransactionCommand(
        zoneId.get,
        actingAs.memberId,
        "transfer",
        from.accountId,
        to.accountId,
        value
      ),
      noopResponseCallback
    )
  }

}
