package com.dhpcs.liquidity

import java.util.{Currency, Locale}

import android.content.{ContentUris, ContentValues, Context}
import com.dhpcs.liquidity.MonopolyGame._
import com.dhpcs.liquidity.ServerConnection._
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

    def isBanker: Boolean

  }

  sealed trait Identity extends Player

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
                                                 isBanker: Boolean,
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

    def onPlayerAdded(addedPlayer: PlayerWithBalanceAndConnectionState)

    def onPlayersChanged(changedPlayers: Iterable[PlayerWithBalanceAndConnectionState])

    def onPlayersInitialized(players: Iterable[PlayerWithBalanceAndConnectionState])

    def onPlayerRemoved(removedPlayer: PlayerWithBalanceAndConnectionState)

    def onPlayersUpdated(players: Map[MemberId, PlayerWithBalanceAndConnectionState])

    def onQuit()

    def onTransferAdded(addedTransfer: TransferWithCurrency)

    def onTransfersChanged(changedTransfers: Iterable[TransferWithCurrency])

    def onTransfersInitialized(transfers: Iterable[TransferWithCurrency])

    def onTransfersUpdated(transfers: Map[TransactionId, TransferWithCurrency])

  }

  private val ZoneTypeKey = "type"
  private val CurrencyCodeKey = "currency"
  private val HiddenFlagKey = "hidden"

  private def currencyFromMetadata(metadata: Option[JsObject]) =
    metadata.flatMap(
      _.value.get(CurrencyCodeKey).flatMap(
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
                                            equityAccountId: AccountId,
                                            clientPublicKey: PublicKey) =
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
      _.value.get(HiddenFlagKey).fold(false)(
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
                                         equityAccountId: AccountId,
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
          accountId == equityAccountId,
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

class MonopolyGame private(context: Context,
                           private var zoneId: Option[ZoneId],
                           private var gameId: Option[Future[Long]])
  extends ConnectionStateListener with NotificationListener {

  def this(context: Context) {
    this(context, None, None)
  }

  def this(context: Context, zoneId: ZoneId) {
    this(context, Some(zoneId), None)
  }

  def this(context: Context, zoneId: ZoneId, gameId: Long) {
    this(context, Some(zoneId), Some(Future.successful(gameId)))
  }

  private trait ResponseCallbackWithErrorForwarding extends ResponseCallback {

    override def onErrorReceived(errorResponse: ErrorResponse) =
      listener.foreach(_.onErrorResponse(errorResponse))

  }

  // TODO: Review all other project Scala classes for fields and methods that could be companion
  // object functions

  private val log = LoggerFactory.getLogger(getClass)

  private val serverConnection = new ServerConnection(context, this, this)
  private val noopResponseCallback = new ResponseCallback with ResponseCallbackWithErrorForwarding

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
    if (serverConnection.isDisconnected) {
      serverConnection.connect()
    }

  private def createAccount(owner: MemberId) =
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

  private def createAndThenJoinZone() =
    serverConnection.sendCommand(
      CreateZoneCommand(
        context.getString(R.string.new_monopoly_game_name),
        Member(
          context.getString(R.string.bank_member_name),
          ClientKey.getPublicKey(context)
        ),
        Account(
          context.getString(R.string.bank_member_name),
          Set.empty
        ),
        Some(
          Json.obj(
            ZoneTypeKey -> MONOPOLY.name,
            CurrencyCodeKey -> Currency.getInstance(Locale.getDefault).getCurrencyCode
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

  def createIdentity(isInitialPrompt: Boolean, name: String) {
    if (isInitialPrompt
      && identities.size == 1 && identities.values.head.accountId == zone.equityAccountId) {
      setGameName(context.getString(R.string.game_name_format_string, name))
    }
    serverConnection.sendCommand(
      CreateMemberCommand(
        zoneId.get,
        Member(
          name,
          ClientKey.getPublicKey(context)
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
            member.metadata.getOrElse(Json.obj()) ++ Json.obj(HiddenFlagKey -> true)
          )
        )
      ),
      noopResponseCallback
    )
  }

  private def disconnect() = serverConnection.disconnect()

  def getCurrency = currency

  def getGameName = zone.name

  def getHiddenIdentities = hiddenIdentities.values

  def getIdentities = identities.values

  def isPublicKeyConnectedAndImplicitlyValid(publicKey: PublicKey) =
    connectedClients.contains(publicKey)

  private def join(zoneId: ZoneId) =
    serverConnection.sendCommand(
      JoinZoneCommand(
        zoneId
      ),
      new ResponseCallbackWithErrorForwarding {

        override def onResultReceived(resultResponse: ResultResponse) {
          log.debug("resultResponse={}", resultResponse)

          val joinZoneResponse = resultResponse.asInstanceOf[JoinZoneResponse]

          joinZoneResponse.zone.metadata
            .flatMap(_.value.get(ZoneTypeKey)
            .flatMap(_.asOpt[String])).getOrElse("") match {

            case zoneTypeName if zoneTypeName != MONOPOLY.name =>
            // TODO

            case _ =>

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
                zone.equityAccountId,
                ClientKey.getPublicKey(context)
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
                zone.equityAccountId,
                connectedClients
              )

              players = updatedPlayers
              listener.foreach(_.onPlayersInitialized(players.values))
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

              listener.foreach(_.onTransfersInitialized(transfers.values))
              listener.foreach(_.onTransfersUpdated(transfers))

              val partiallyCreatedIdentityIds = zone.members.collect {
                case (memberId, member) if ClientKey.getPublicKey(context) == member.publicKey
                  && !zone.accounts.values.exists(_.owners == Set(memberId)) =>
                  memberId
              }

              partiallyCreatedIdentityIds.foreach(createAccount)

              /*
               * Since we must only prompt for a required identity if none exist yet and since
               * having one or more partially created identities implies that gameId would be set,
               * we can proceed here without checking that partiallyCreatedIdentityIds is non empty.
               *
               * The second condition isn't usually of significance but exists to prevent
               * incorrectly prompting for an identity if a user rejoins a game by scanning its
               * code again rather than by clicking its list item.
               */
              if (gameId.isEmpty && !(updatedIdentities ++ updatedHiddenIdentities).values.exists(
                _.accountId != zone.equityAccountId
              )) {
                listener.foreach(_.onIdentityRequired())
              }

              def checkAndUpdateGameName(name: String): Option[Long] = {
                val existingEntry = context.getContentResolver.query(
                  Games.CONTENT_URI,
                  Array(LiquidityContract.Games._ID, LiquidityContract.Games.NAME),
                  LiquidityContract.Games.ZONE_ID + " = ?",
                  Array(zoneId.id.toString),
                  null
                )
                if (!existingEntry.moveToFirst()) {
                  None
                } else {
                  val gameId = existingEntry.getLong(
                    existingEntry.getColumnIndexOrThrow(LiquidityContract.Games._ID)
                  )
                  if (zone.name != existingEntry.getString(
                    existingEntry.getColumnIndexOrThrow(LiquidityContract.Games.NAME)
                  )) {
                    updateGameName(gameId, name)
                  }
                  Some(gameId)
                }
              }

              /*
               * We don't set gameId until now as it also indicates above whether we've prompted
               * for the required identity - which we must do at most once.
               */
              gameId = gameId.fold(
                Some(Future(

                  /*
                   * This is in-case a user rejoins a game by scanning its code again rather than
                   * by clicking its list item - in such cases we mustn't attempt to insert an
                   * entry as that would silently fail (as it happens on the Future's worker
                   * thread), but we may need to update the existing entries name.
                   */
                  checkAndUpdateGameName(joinZoneResponse.zone.name).getOrElse {
                    val contentValues = new ContentValues
                    contentValues.put(Games.GAME_TYPE, MONOPOLY.name)
                    contentValues.put(Games.ZONE_ID, zoneId.id.toString)
                    contentValues.put(Games.NAME, joinZoneResponse.zone.name)
                    contentValues.put(Games.CREATED, joinZoneResponse.zone.created: java.lang.Long)
                    ContentUris.parseId(
                      context.getContentResolver.insert(
                        Games.CONTENT_URI,
                        contentValues
                      )
                    )
                  }
                ))
              ) { id =>
                id.onSuccess { case _ =>
                  Future(
                    checkAndUpdateGameName(joinZoneResponse.zone.name)
                  )
                }
                Some(id)
              }

          }

        }

      }
    )

  override def onNotificationReceived(notification: Notification) {
    log.debug("notification={}", notification)

    notification match {

      case zoneNotification: ZoneNotification =>

        val zoneId = this.zoneId.get

        if (zoneId != zoneNotification.zoneId) {
          sys.error(s"zoneId != zoneNotification.zoneId (${zoneNotification.zoneId} != $zoneId)")
        }

        def updatePlayersAndTransactions() {
          val (updatedPlayers, updatedHiddenPlayers) = playersFromMembersAccounts(
            memberIdsToAccountIds,
            zone.accounts,
            balances,
            currency,
            zone.members,
            zone.equityAccountId,
            connectedClients
          )

          if (updatedPlayers != players) {
            val addedPlayers = updatedPlayers -- players.keys
            val changedPlayers = updatedPlayers.filter { case (memberId, player) =>
              players.get(memberId).fold(false)(_ != player)
            }
            val removedPlayers = players -- updatedPlayers.keys
            if (addedPlayers.nonEmpty) {
              listener.foreach(listener =>
                addedPlayers.values.foreach(listener.onPlayerAdded)
              )
            }
            if (changedPlayers.nonEmpty) {
              listener.foreach(_.onPlayersChanged(changedPlayers.values))
            }
            if (removedPlayers.nonEmpty) {
              listener.foreach(listener =>
                removedPlayers.values.foreach(listener.onPlayerRemoved)
              )
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
              zone.equityAccountId,
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
              zone.equityAccountId,
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

            listener.foreach(_.onGameNameChanged(zone.name))

            gameId.foreach(_.onSuccess { case id =>
              Future {
                updateGameName(id, zoneNameSetNotification.name)
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
              zone.equityAccountId,
              ClientKey.getPublicKey(context)
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

            updatePlayersAndTransactions()

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
              zone.equityAccountId,
              ClientKey.getPublicKey(context)
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
              zone.equityAccountId,
              connectedClients
            )

            if (createdPlayer.nonEmpty) {
              players = players ++ createdPlayer
              listener.foreach(listener =>
                createdPlayer.values.foreach(listener.onPlayerAdded)
              )
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
              zone.equityAccountId,
              ClientKey.getPublicKey(context)
            )

            if (updatedIdentities != identities) {
              identities = updatedIdentities
              listener.foreach(_.onIdentitiesUpdated(identities))
            }

            if (updatedHiddenIdentities != hiddenIdentities) {
              hiddenIdentities = updatedHiddenIdentities
            }

            updatePlayersAndTransactions()

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
              zone.equityAccountId,
              ClientKey.getPublicKey(context)
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
              zone.equityAccountId,
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
              listener.foreach(listener =>
                createdTransfer.values.foreach(listener.onTransferAdded)
              )
              listener.foreach(_.onTransfersUpdated(transfers))
            }

        }

    }

  }

  override def onStateChanged(connectionState: ConnectionState) {
    log.debug("connectionState={}", connectionState)
    connectionState match {

      case CONNECTING =>

      case CONNECTED =>
        zoneId.fold(createAndThenJoinZone())(join)

      case DISCONNECTING =>

      case DISCONNECTED =>

      // TODO

    }
  }

  def quitAndOrDisconnect() =
    if (!serverConnection.isDisconnected) {
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
          metadata = member.metadata.map(_ - HiddenFlagKey)
        )
      ),
      noopResponseCallback
    )
  }

  def setGameName(name: String) =
    serverConnection.sendCommand(
      SetZoneNameCommand(
        zoneId.get,
        name
      ),
      noopResponseCallback
    )

  def setIdentityName(identity: Identity, name: String) =
    serverConnection.sendCommand(
      UpdateMemberCommand(
        zoneId.get,
        identity.memberId,
        zone.members(identity.memberId).copy(name = name)
      ),
      noopResponseCallback
    )

  def setListener(listener: Listener) {
    this.listener = Option(listener)
    this.listener.foreach(listener =>
      if (zone == null) {
        listener.onQuit()
      } else {
        listener.onJoined(zoneId.get)
        listener.onGameNameChanged(zone.name)
        listener.onIdentitiesUpdated(identities)
        listener.onPlayersInitialized(players.values)
        listener.onPlayersUpdated(players)
        listener.onTransfersInitialized(transfers.values)
        listener.onTransfersUpdated(transfers)
      }
    )
  }

  def transferIdentity(identityId: MemberId, to: PublicKey) {
    val identity = identities(identityId)
    serverConnection.sendCommand(
      UpdateMemberCommand(
        zoneId.get,
        identity.memberId,
        identity.member.copy(publicKey = to)
      ),
      noopResponseCallback
    )
  }

  def transfer(actingAs: Identity, from: Identity, to: Player, value: BigDecimal) =
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

  private def updateGameName(gameId: Long, name: String) {
    val contentValues = new ContentValues
    contentValues.put(LiquidityContract.Games.NAME, name)
    context.getContentResolver.update(
      ContentUris.withAppendedId(Games.CONTENT_URI, gameId),
      contentValues,
      null,
      null
    )
  }

}
