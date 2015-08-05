package com.dhpcs.liquidity

import java.util.{Currency, Locale}

import android.content.{ContentUris, ContentValues, Context}
import com.dhpcs.liquidity.MonopolyGame.{State, _}
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

  sealed trait JoinState

  case object UNAVAILABLE extends JoinState

  case object AVAILABLE extends JoinState

  case object CONNECTING extends JoinState

  case object JOINING extends JoinState

  case object JOINED extends JoinState

  case object QUITTING extends JoinState

  case object DISCONNECTING extends JoinState

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

  trait JoinStateListener {

    def onJoinStateChanged(joinState: JoinState)

  }

  trait GameActionListener {

    def onCreateError()

    def onGameNameChanged(name: String)

    def onIdentitiesUpdated(identities: Map[MemberId, IdentityWithBalance])

    def onIdentityCreated(identity: IdentityWithBalance)

    def onIdentityReceived(identity: IdentityWithBalance)

    def onIdentityRequired()

    def onIdentityRestored(identity: IdentityWithBalance)

    def onJoinError()

    def onPlayerAdded(addedPlayer: PlayerWithBalanceAndConnectionState)

    def onPlayerChanged(changedPlayer: PlayerWithBalanceAndConnectionState)

    def onPlayersInitialized(players: Iterable[PlayerWithBalanceAndConnectionState])

    def onPlayerRemoved(removedPlayer: PlayerWithBalanceAndConnectionState)

    def onPlayersUpdated(players: Map[MemberId, PlayerWithBalanceAndConnectionState])

    def onTransferAdded(addedTransfer: TransferWithCurrency)

    def onTransfersChanged(changedTransfers: Iterable[TransferWithCurrency])

    def onTransfersInitialized(transfers: Iterable[TransferWithCurrency])

    def onTransfersUpdated(transfers: Map[TransactionId, TransferWithCurrency])

  }

  class JoinRequestToken

  private class State(var zone: Zone,
                      var connectedClients: Set[PublicKey],
                      var balances: Map[AccountId, BigDecimal],
                      var currency: Option[Either[String, Currency]],
                      var memberIdsToAccountIds: Map[MemberId, AccountId],
                      var accountIdsToMemberIds: Map[AccountId, MemberId],
                      var identities: Map[MemberId, IdentityWithBalance],
                      var hiddenIdentities: Map[MemberId, IdentityWithBalance],
                      var players: Map[MemberId, PlayerWithBalanceAndConnectionState],
                      var hiddenPlayers: Map[MemberId, PlayerWithBalanceAndConnectionState],
                      var transfers: Map[TransactionId, TransferWithCurrency])

  private val CurrencyCodeKey = "currency"
  private val HiddenFlagKey = "hidden"

  private var instances = Map.empty[ZoneId, MonopolyGame]

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

  def getInstance(zoneId: ZoneId) = instances.get(zoneId).orNull

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
                           serverConnection: ServerConnection,
                           private var zoneId: Option[ZoneId],
                           private var gameId: Option[Future[Long]])
  extends ServerConnection.ConnectionStateListener
  with ServerConnection.NotificationReceiptListener {

  private trait ResponseCallbackWithDefaultErrorHandling extends ResponseCallback {

    override def onErrorReceived(errorResponse: ErrorResponse) =
      log.debug(s"errorResponse=$errorResponse")

  }

  private val log = LoggerFactory.getLogger(getClass)

  private val noopResponseCallback = new ResponseCallback
    with ResponseCallbackWithDefaultErrorHandling
  private val connectionRequestToken = new ConnectionRequestToken

  private var state: State = _

  private var _joinState: JoinState = MonopolyGame.UNAVAILABLE

  private var joinStateListeners = Set.empty[JoinStateListener]
  private var joinRequestTokens = Set.empty[JoinRequestToken]

  private var gameActionListeners = Set.empty[GameActionListener]

  def this(context: Context, serverConnection: ServerConnection) {
    this(context, serverConnection, None, None)
  }

  def this(context: Context, serverConnection: ServerConnection, zoneId: ZoneId) {
    this(context, serverConnection, Some(zoneId), None)
  }

  def this(context: Context, serverConnection: ServerConnection, zoneId: ZoneId, gameId: Long) {
    this(context, serverConnection, Some(zoneId), Some(Future.successful(gameId)))
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
            CurrencyCodeKey -> Currency.getInstance(Locale.getDefault).getCurrencyCode
          )
        )
      ),
      new ResponseCallback {

        override def onErrorReceived(errorResponse: ErrorResponse) {
          log.debug(s"errorResponse=$errorResponse")

          gameActionListeners.foreach(_.onCreateError())

        }

        override def onResultReceived(resultResponse: ResultResponse) {
          log.debug(s"resultResponse=$resultResponse")

          val createZoneResponse = resultResponse.asInstanceOf[CreateZoneResponse]

          instances = instances + (createZoneResponse.zoneId -> MonopolyGame.this)

          zoneId = Some(createZoneResponse.zoneId)

          join(createZoneResponse.zoneId)

        }

      }
    )

  def createIdentity(isInitialPrompt: Boolean, name: String) {
    if (isInitialPrompt
      && state.identities.size == 1
      && state.identities.values.head.accountId == state.zone.equityAccountId) {
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
      new ResponseCallbackWithDefaultErrorHandling {

        override def onResultReceived(resultResponse: ResultResponse) {
          log.debug(s"resultResponse=$resultResponse")

          val createMemberResponse = resultResponse.asInstanceOf[CreateMemberResponse]

          createAccount(createMemberResponse.memberId)

        }

      }
    )
  }

  def deleteIdentity(identity: Identity) {
    val member = state.zone.members(identity.memberId)
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

  def getCurrency = state.currency

  def getGameName = state.zone.name

  def getHiddenIdentities = state.hiddenIdentities.values

  def getIdentities = state.identities.values

  def getJoinState = _joinState

  def getPlayers = state.players.values

  def getZoneId = zoneId.orNull

  def isPublicKeyConnectedAndImplicitlyValid(publicKey: PublicKey) =
    state.connectedClients.contains(publicKey)

  private def join(zoneId: ZoneId) = {
    serverConnection.sendCommand(
      JoinZoneCommand(
        zoneId
      ),
      new ResponseCallback {

        override def onErrorReceived(errorResponse: ErrorResponse) {
          log.debug(s"errorResponse=$errorResponse")

          gameActionListeners.foreach(_.onJoinError())

        }

        override def onResultReceived(resultResponse: ResultResponse) {
          log.debug(s"resultResponse=$resultResponse")

          val joinZoneResponse = resultResponse.asInstanceOf[JoinZoneResponse]

          var balances = Map.empty[AccountId, BigDecimal].withDefaultValue(BigDecimal(0))
          for (transaction <- joinZoneResponse.zone.transactions.values) {
            balances = balances +
              (transaction.from -> (balances(transaction.from) - transaction.value)) +
              (transaction.to -> (balances(transaction.to) + transaction.value))
          }

          val currency = currencyFromMetadata(joinZoneResponse.zone.metadata)

          val memberIdsToAccountIds = membersAccountsFromAccounts(
            joinZoneResponse.zone.accounts
          )
          val accountIdsToMemberIds = memberIdsToAccountIds.map(_.swap)

          val (identities, hiddenIdentities) = identitiesFromMembersAccounts(
            memberIdsToAccountIds,
            joinZoneResponse.zone.accounts,
            balances,
            currency,
            joinZoneResponse.zone.members,
            joinZoneResponse.zone.equityAccountId,
            ClientKey.getPublicKey(context)
          )

          val (players, hiddenPlayers) = playersFromMembersAccounts(
            memberIdsToAccountIds,
            joinZoneResponse.zone.accounts,
            balances,
            currency,
            joinZoneResponse.zone.members,
            joinZoneResponse.zone.equityAccountId,
            joinZoneResponse.connectedClients
          )

          val transfers = transfersFromTransactions(
            joinZoneResponse.zone.transactions,
            currency,
            accountIdsToMemberIds,
            players ++ hiddenPlayers,
            joinZoneResponse.zone.accounts,
            joinZoneResponse.zone.members
          )

          state = new State(
            joinZoneResponse.zone,
            joinZoneResponse.connectedClients,
            balances,
            currency,
            memberIdsToAccountIds,
            accountIdsToMemberIds,
            identities,
            hiddenIdentities,
            players,
            hiddenPlayers,
            transfers
          )

          _joinState = JOINED
          joinStateListeners.foreach(_.onJoinStateChanged(_joinState))

          gameActionListeners.foreach(_.onGameNameChanged(joinZoneResponse.zone.name))
          gameActionListeners.foreach(_.onIdentitiesUpdated(identities))
          gameActionListeners.foreach(_.onPlayersInitialized(players.values))
          gameActionListeners.foreach(_.onPlayersUpdated(players))
          gameActionListeners.foreach(_.onTransfersInitialized(transfers.values))
          gameActionListeners.foreach(_.onTransfersUpdated(transfers))

          val partiallyCreatedIdentityIds = joinZoneResponse.zone.members.collect {
            case (memberId, member) if ClientKey.getPublicKey(context) == member.publicKey
              && !joinZoneResponse.zone.accounts.values.exists(_.owners == Set(memberId)) =>
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
          if (gameId.isEmpty && !(identities ++ hiddenIdentities).values.exists(
            _.accountId != joinZoneResponse.zone.equityAccountId
          )) {
            gameActionListeners.foreach(_.onIdentityRequired())
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
              if (existingEntry.getString(
                existingEntry.getColumnIndexOrThrow(LiquidityContract.Games.NAME)
              ) != name) {
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
               * This is in case a user rejoins a game by scanning its code again rather than
               * by clicking its list item - in such cases we mustn't attempt to insert an
               * entry as that would silently fail (as it happens on the Future's worker
               * thread), but we may need to update the existing entries name.
               */
              checkAndUpdateGameName(joinZoneResponse.zone.name).getOrElse {
                val contentValues = new ContentValues
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
          ) { gameId =>
            gameId.foreach(_ =>
              Future(
                checkAndUpdateGameName(joinZoneResponse.zone.name)
              )
            )
            Some(gameId)
          }

        }

      }
    )
    _joinState = JOINING
    joinStateListeners.foreach(_.onJoinStateChanged(_joinState))
  }

  override def onNotificationReceived(notification: Notification) {
    log.debug(s"notification=$notification")

    notification match {

      case zoneNotification: ZoneNotification =>

        if (zoneId.get == zoneNotification.zoneId) {

          def updatePlayersAndTransactions() {
            val (updatedPlayers, updatedHiddenPlayers) = playersFromMembersAccounts(
              state.memberIdsToAccountIds,
              state.zone.accounts,
              state.balances,
              state.currency,
              state.zone.members,
              state.zone.equityAccountId,
              state.connectedClients
            )

            if (updatedPlayers != state.players) {
              val addedPlayers = updatedPlayers -- state.players.keys
              val changedPlayers = updatedPlayers.filter { case (memberId, player) =>
                state.players.get(memberId).fold(false)(_ != player)
              }
              val removedPlayers = state.players -- updatedPlayers.keys
              if (addedPlayers.nonEmpty) {
                gameActionListeners.foreach(listener =>
                  addedPlayers.values.foreach(listener.onPlayerAdded)
                )
              }
              if (changedPlayers.nonEmpty) {
                gameActionListeners.foreach(listener =>
                  changedPlayers.values.foreach(listener.onPlayerChanged)
                )
              }
              if (removedPlayers.nonEmpty) {
                gameActionListeners.foreach(listener =>
                  removedPlayers.values.foreach(listener.onPlayerRemoved)
                )
              }
              state.players = updatedPlayers
              gameActionListeners.foreach(_.onPlayersUpdated(updatedPlayers))
            }

            if (updatedHiddenPlayers != state.hiddenPlayers) {
              state.hiddenPlayers = updatedHiddenPlayers
            }

            val updatedTransfers = transfersFromTransactions(
              state.zone.transactions,
              state.currency,
              state.accountIdsToMemberIds,
              state.players ++ state.hiddenPlayers,
              state.zone.accounts,
              state.zone.members
            )

            if (updatedTransfers != state.transfers) {
              val changedTransfers = updatedTransfers.filter { case (transactionId, transfer) =>
                state.transfers.get(transactionId).fold(false)(_ != transfer)
              }
              if (changedTransfers.nonEmpty) {
                gameActionListeners.foreach(_.onTransfersChanged(changedTransfers.values))
              }
              state.transfers = updatedTransfers
              gameActionListeners.foreach(_.onTransfersUpdated(updatedTransfers))
            }
          }

          zoneNotification match {

            case clientJoinedZoneNotification: ClientJoinedZoneNotification =>

              state.connectedClients = state.connectedClients +
                clientJoinedZoneNotification.publicKey

              val (joinedPlayers, joinedHiddenPlayers) = playersFromMembersAccounts(
                state.memberIdsToAccountIds.filterKeys(
                  state.zone.members(_).publicKey == clientJoinedZoneNotification.publicKey
                ),
                state.zone.accounts,
                state.balances,
                state.currency,
                state.zone.members,
                state.zone.equityAccountId,
                Set(clientJoinedZoneNotification.publicKey)
              )

              if (joinedPlayers.nonEmpty) {
                state.players = state.players ++ joinedPlayers
                gameActionListeners.foreach(listener =>
                  joinedPlayers.values.foreach(listener.onPlayerChanged)
                )
                gameActionListeners.foreach(_.onPlayersUpdated(state.players))
              }

              if (joinedHiddenPlayers.nonEmpty) {
                state.hiddenPlayers = state.hiddenPlayers ++ joinedHiddenPlayers
              }

            case clientQuitZoneNotification: ClientQuitZoneNotification =>

              state.connectedClients = state.connectedClients - clientQuitZoneNotification.publicKey

              val (quitPlayers, quitHiddenPlayers) = playersFromMembersAccounts(
                state.memberIdsToAccountIds.filterKeys(
                  state.zone.members(_).publicKey == clientQuitZoneNotification.publicKey
                ),
                state.zone.accounts,
                state.balances,
                state.currency,
                state.zone.members,
                state.zone.equityAccountId,
                Set.empty
              )

              if (quitPlayers.nonEmpty) {
                state.players = state.players ++ quitPlayers
                gameActionListeners.foreach(listener =>
                  quitPlayers.values.foreach(listener.onPlayerChanged)
                )
                gameActionListeners.foreach(_.onPlayersUpdated(state.players))
              }

              if (quitHiddenPlayers.nonEmpty) {
                state.hiddenPlayers = state.hiddenPlayers ++ quitHiddenPlayers
              }

            case zoneTerminatedNotification: ZoneTerminatedNotification =>

              state = null
              join(zoneNotification.zoneId)

            case zoneNameSetNotification: ZoneNameSetNotification =>

              state.zone = state.zone.copy(name = zoneNameSetNotification.name)

              gameActionListeners.foreach(_.onGameNameChanged(zoneNameSetNotification.name))

              gameId.foreach(_.foreach { gameId =>
                Future {
                  updateGameName(gameId, zoneNameSetNotification.name)
                }
              })

            case memberCreatedNotification: MemberCreatedNotification =>

              state.zone = state.zone.copy(
                members = state.zone.members
                  + (memberCreatedNotification.memberId ->
                  memberCreatedNotification.member)
              )

            case memberUpdatedNotification: MemberUpdatedNotification =>

              state.zone = state.zone.copy(
                members = state.zone.members
                  + (memberUpdatedNotification.memberId ->
                  memberUpdatedNotification.member)
              )

              val (updatedIdentities, updatedHiddenIdentities) = identitiesFromMembersAccounts(
                state.memberIdsToAccountIds,
                state.zone.accounts,
                state.balances,
                state.currency,
                state.zone.members,
                state.zone.equityAccountId,
                ClientKey.getPublicKey(context)
              )

              if (updatedIdentities != state.identities) {

                val receivedIdentity =
                  if (!state.identities.contains(memberUpdatedNotification.memberId) &&
                    !state.hiddenIdentities.contains(memberUpdatedNotification.memberId)) {
                    updatedIdentities.get(memberUpdatedNotification.memberId)
                  } else {
                    None
                  }

                val restoredIdentity =
                  if (!state.identities.contains(memberUpdatedNotification.memberId) &&
                    state.hiddenIdentities.contains(memberUpdatedNotification.memberId)) {
                    updatedIdentities.get(memberUpdatedNotification.memberId)
                  } else {
                    None
                  }

                state.identities = updatedIdentities
                gameActionListeners.foreach(_.onIdentitiesUpdated(updatedIdentities))

                receivedIdentity.foreach(receivedIdentity =>
                  gameActionListeners.foreach(_.onIdentityReceived(receivedIdentity))
                )

                restoredIdentity.foreach(restoredIdentity =>
                  gameActionListeners.foreach(_.onIdentityRestored(restoredIdentity))
                )

              }

              if (updatedHiddenIdentities != state.hiddenIdentities) {
                state.hiddenIdentities = updatedHiddenIdentities
              }

              updatePlayersAndTransactions()

            case accountCreatedNotification: AccountCreatedNotification =>

              state.zone = state.zone.copy(
                accounts = state.zone.accounts
                  + (accountCreatedNotification.accountId ->
                  accountCreatedNotification.account)
              )

              val createdMembersAccounts = membersAccountsFromAccounts(
                Map(
                  accountCreatedNotification.accountId ->
                    state.zone.accounts(accountCreatedNotification.accountId)
                )
              )

              state.memberIdsToAccountIds = state.memberIdsToAccountIds ++ createdMembersAccounts
              state.accountIdsToMemberIds = state.accountIdsToMemberIds ++
                createdMembersAccounts.map(_.swap)

              val (createdIdentity, createdHiddenIdentity) = identitiesFromMembersAccounts(
                createdMembersAccounts,
                state.zone.accounts,
                state.balances,
                state.currency,
                state.zone.members,
                state.zone.equityAccountId,
                ClientKey.getPublicKey(context)
              )

              if (createdIdentity.nonEmpty) {
                state.identities = state.identities ++ createdIdentity
                gameActionListeners.foreach(_.onIdentitiesUpdated(state.identities))
                gameActionListeners.foreach(
                  _.onIdentityCreated(
                    state.identities(accountCreatedNotification.account.owners.head)
                  )
                )
              }

              if (createdHiddenIdentity.nonEmpty) {
                state.hiddenIdentities = state.hiddenIdentities ++ createdHiddenIdentity
              }

              val (createdPlayer, createdHiddenPlayer) = playersFromMembersAccounts(
                createdMembersAccounts,
                state.zone.accounts,
                state.balances,
                state.currency,
                state.zone.members,
                state.zone.equityAccountId,
                state.connectedClients
              )

              if (createdPlayer.nonEmpty) {
                state.players = state.players ++ createdPlayer
                gameActionListeners.foreach(listener =>
                  createdPlayer.values.foreach(listener.onPlayerAdded)
                )
                gameActionListeners.foreach(_.onPlayersUpdated(state.players))
              }

              if (createdHiddenPlayer.nonEmpty) {
                state.hiddenPlayers = state.hiddenPlayers ++ createdHiddenPlayer
              }

            case accountUpdatedNotification: AccountUpdatedNotification =>

              state.zone = state.zone.copy(
                accounts = state.zone.accounts
                  + (accountUpdatedNotification.accountId ->
                  accountUpdatedNotification.account)
              )

              state.memberIdsToAccountIds = membersAccountsFromAccounts(state.zone.accounts)
              state.accountIdsToMemberIds = state.memberIdsToAccountIds.map(_.swap)

              val (updatedIdentities, updatedHiddenIdentities) = identitiesFromMembersAccounts(
                state.memberIdsToAccountIds,
                state.zone.accounts,
                state.balances,
                state.currency,
                state.zone.members,
                state.zone.equityAccountId,
                ClientKey.getPublicKey(context)
              )

              if (updatedIdentities != state.identities) {
                state.identities = updatedIdentities
                gameActionListeners.foreach(_.onIdentitiesUpdated(updatedIdentities))
              }

              if (updatedHiddenIdentities != state.hiddenIdentities) {
                state.hiddenIdentities = updatedHiddenIdentities
              }

              updatePlayersAndTransactions()

            case transactionAddedNotification: TransactionAddedNotification =>

              state.zone = state.zone.copy(
                transactions = state.zone.transactions
                  + (transactionAddedNotification.transactionId ->
                  transactionAddedNotification.transaction)
              )

              val transaction = transactionAddedNotification.transaction

              state.balances = state.balances +
                (transaction.from -> (state.balances(transaction.from) - transaction.value)) +
                (transaction.to -> (state.balances(transaction.to) + transaction.value))

              val changedMembersAccounts = membersAccountsFromAccounts(
                Map(
                  transaction.from -> state.zone.accounts(transaction.from),
                  transaction.to -> state.zone.accounts(transaction.to)
                )
              )

              val (changedIdentities, changedHiddenIdentities) = identitiesFromMembersAccounts(
                changedMembersAccounts,
                state.zone.accounts,
                state.balances,
                state.currency,
                state.zone.members,
                state.zone.equityAccountId,
                ClientKey.getPublicKey(context)
              )

              if (changedIdentities.nonEmpty) {
                state.identities = state.identities ++ changedIdentities
                gameActionListeners.foreach(_.onIdentitiesUpdated(state.identities))
              }

              if (changedHiddenIdentities.nonEmpty) {
                state.hiddenIdentities = state.hiddenIdentities ++ changedHiddenIdentities
              }

              val (changedPlayers, changedHiddenPlayers) = playersFromMembersAccounts(
                changedMembersAccounts,
                state.zone.accounts,
                state.balances,
                state.currency,
                state.zone.members,
                state.zone.equityAccountId,
                state.connectedClients
              )

              if (changedPlayers.nonEmpty) {
                state.players = state.players ++ changedPlayers
                gameActionListeners.foreach(listener =>
                  changedPlayers.values.foreach(listener.onPlayerChanged)
                )
                gameActionListeners.foreach(_.onPlayersUpdated(state.players))
              }

              if (changedHiddenPlayers.nonEmpty) {
                state.hiddenPlayers = state.hiddenPlayers ++ changedHiddenPlayers
              }

              val createdTransfer = transfersFromTransactions(
                Map(
                  transactionAddedNotification.transactionId ->
                    transactionAddedNotification.transaction
                ),
                state.currency,
                state.accountIdsToMemberIds,
                state.players ++ state.hiddenPlayers,
                state.zone.accounts,
                state.zone.members
              )

              if (createdTransfer.nonEmpty) {
                state.transfers = state.transfers ++ createdTransfer
                gameActionListeners.foreach(listener =>
                  createdTransfer.values.foreach(listener.onTransferAdded)
                )
                gameActionListeners.foreach(_.onTransfersUpdated(state.transfers))
              }

          }

        }

    }

  }

  override def onConnectionStateChanged(connectionState: ConnectionState) {
    log.debug(s"connectionState=$connectionState")
    connectionState match {

      case ServerConnection.UNAVAILABLE =>
        state = null
        _joinState = MonopolyGame.UNAVAILABLE

      case ServerConnection.AVAILABLE =>
        state = null
        _joinState = MonopolyGame.AVAILABLE

      case ServerConnection.CONNECTING =>
        state = null
        _joinState = MonopolyGame.CONNECTING

      case ServerConnection.CONNECTED =>
        state = null
        zoneId.fold(createAndThenJoinZone())(join)
        _joinState = JOINING

      case ServerConnection.DISCONNECTING =>
        state = null
        _joinState = MonopolyGame.DISCONNECTING

    }
    joinStateListeners.foreach(_.onJoinStateChanged(_joinState))
  }

  def registerListener(listener: JoinStateListener) =
    if (!joinStateListeners.contains(listener)) {
      if (joinStateListeners.isEmpty && gameActionListeners.isEmpty && joinRequestTokens.isEmpty) {
        serverConnection.registerListener(this: ConnectionStateListener)
        serverConnection.registerListener(this: NotificationReceiptListener)
      }
      joinStateListeners = joinStateListeners + listener
      listener.onJoinStateChanged(_joinState)
    }

  def registerListener(listener: GameActionListener) =
    if (!gameActionListeners.contains(listener)) {
      if (joinStateListeners.isEmpty && gameActionListeners.isEmpty && joinRequestTokens.isEmpty) {
        serverConnection.registerListener(this: ConnectionStateListener)
        serverConnection.registerListener(this: NotificationReceiptListener)
      }
      gameActionListeners = gameActionListeners + listener
      if (_joinState == MonopolyGame.JOINED) {
        listener.onGameNameChanged(state.zone.name)
        listener.onIdentitiesUpdated(state.identities)
        listener.onPlayersInitialized(state.players.values)
        listener.onPlayersUpdated(state.players)
        listener.onTransfersInitialized(state.transfers.values)
        listener.onTransfersUpdated(state.transfers)
      }
    }

  def requestJoin(token: JoinRequestToken, retry: Boolean) = {
    zoneId.foreach(zoneId =>
      if (!instances.contains(zoneId)) {
        instances = instances + (zoneId -> MonopolyGame.this)
      }
    )
    if (joinStateListeners.isEmpty && gameActionListeners.isEmpty && joinRequestTokens.isEmpty) {
      serverConnection.registerListener(this: ConnectionStateListener)
      serverConnection.registerListener(this: NotificationReceiptListener)
    }
    if (!joinRequestTokens.contains(token)) {
      joinRequestTokens = joinRequestTokens + token
    }
    if (_joinState == MonopolyGame.AVAILABLE) {

      serverConnection.requestConnection(connectionRequestToken, retry)

    }
  }

  def restoreIdentity(identity: Identity) {
    val member = state.zone.members(identity.memberId)
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
        state.zone.members(identity.memberId).copy(name = name)
      ),
      noopResponseCallback
    )

  def transferIdentity(identityId: MemberId, to: PublicKey) {
    val identity = state.identities(identityId)
    serverConnection.sendCommand(
      UpdateMemberCommand(
        zoneId.get,
        identity.memberId,
        identity.member.copy(publicKey = to)
      ),
      noopResponseCallback
    )
  }

  def transfer(actingAs: Identity, from: Identity, to: Seq[Player], value: BigDecimal) =
    to.foreach(to =>
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
    )

  def unregisterListener(listener: JoinStateListener) =
    if (joinStateListeners.contains(listener)) {
      joinStateListeners = joinStateListeners - listener
      if (joinStateListeners.isEmpty && gameActionListeners.isEmpty && joinRequestTokens.isEmpty) {
        serverConnection.unregisterListener(this: NotificationReceiptListener)
        serverConnection.unregisterListener(this: ConnectionStateListener)
      }
    }

  def unregisterListener(listener: GameActionListener) =
    if (gameActionListeners.contains(listener)) {
      gameActionListeners = gameActionListeners - listener
      if (joinStateListeners.isEmpty && gameActionListeners.isEmpty && joinRequestTokens.isEmpty) {
        serverConnection.unregisterListener(this: NotificationReceiptListener)
        serverConnection.unregisterListener(this: ConnectionStateListener)
      }
    }

  def unrequestJoin(token: JoinRequestToken) =
    if (joinRequestTokens.contains(token)) {
      joinRequestTokens = joinRequestTokens - token
      if (joinStateListeners.isEmpty && gameActionListeners.isEmpty && joinRequestTokens.isEmpty) {
        serverConnection.unregisterListener(this: NotificationReceiptListener)
        serverConnection.unregisterListener(this: ConnectionStateListener)
      }
      if (joinRequestTokens.isEmpty) {

        instances = instances - zoneId.get

        if (_joinState == MonopolyGame.CONNECTING || _joinState == MonopolyGame.JOINING) {

          serverConnection.unrequestConnection(connectionRequestToken)

        } else if (_joinState == MonopolyGame.JOINED) {

          serverConnection.sendCommand(
            QuitZoneCommand(
              zoneId.get
            ),
            new ResponseCallback {

              private def doDisconnect() {
                if (joinRequestTokens.isEmpty) {

                  serverConnection.unrequestConnection(connectionRequestToken)

                  state = null

                }
              }

              override def onErrorReceived(errorResponse: ErrorResponse) {
                log.debug(s"errorResponse=$errorResponse")

                doDisconnect()
              }

              override def onResultReceived(resultResponse: ResultResponse) {
                log.debug(s"resultResponse=$resultResponse")

                doDisconnect()
              }

            }
          )

          _joinState = QUITTING
          joinStateListeners.foreach(_.onJoinStateChanged(_joinState))

        }

      }
    }

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
