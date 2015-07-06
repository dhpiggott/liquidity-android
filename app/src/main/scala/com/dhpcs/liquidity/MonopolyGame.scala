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

  sealed trait Identity extends Player

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
                                 (BigDecimal, Option[Either[String, Currency]]))
    extends Identity

  trait Listener {

    def onErrorResponse(errorResponse: ErrorResponse)

    def onGameNameChanged(name: String)

    def onIdentitiesChanged(identities: Map[MemberId, IdentityWithBalance])

    def onIdentityCreated(identity: IdentityWithBalance)

    def onIdentityReceived(identity: IdentityWithBalance)

    def onIdentityRequired()

    def onJoined(zoneId: ZoneId)

    def onPlayersChanged(players: Iterable[PlayerWithBalanceAndConnectionState])

    def onQuit()

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

  private def identitiesFromAccounts(accounts: Map[AccountId, Account],
                                     balances: Map[AccountId, BigDecimal],
                                     currency: Option[Either[String, Currency]],
                                     members: Map[MemberId, Member],
                                     clientPublicKey: PublicKey,
                                     equityAccountOwners: Set[MemberId],
                                     includeHidden: Boolean = false,
                                     includeUnhidden: Boolean = true) =
    accounts.collect {
      case (accountId, account) if account.owners.size == 1
        && members.get(account.owners.head).fold(false) { member =>
        if (includeHidden && includeUnhidden) {
          true
        } else if (includeHidden) {
          isHidden(member)
        } else if (includeUnhidden) {
          !isHidden(member)
        } else {
          false
        } &&
          member.publicKey == clientPublicKey
      } =>

        val ownerId = account.owners.head
        val owner = members(ownerId)
        ownerId -> IdentityWithBalance(
          ownerId,
          owner,
          accountId,
          account,
          (balances(accountId).bigDecimal, currency)
        )

    }

  private def isHidden(member: Member) =
    member.metadata.fold(false)(
      _.value.get("hidden").fold(false)(
        _.asOpt[Boolean].getOrElse(false)
      )
    )

  private def playersFromAccounts(accounts: Map[AccountId, Account],
                                  balances: Map[AccountId, BigDecimal],
                                  currency: Option[Either[String, Currency]],
                                  members: Map[MemberId, Member],
                                  connectedPublicKeys: Set[PublicKey]) =
    accounts.collect {
      case (accountId, account) if account.owners.size == 1
        && members.get(account.owners.head).fold(false)(!isHidden(_)) =>

        val ownerId = account.owners.head
        val owner = members(ownerId)
        ownerId -> PlayerWithBalanceAndConnectionState(
          ownerId,
          owner,
          accountId,
          account,
          (balances(accountId).bigDecimal, currency),
          connectedPublicKeys.contains(owner.publicKey)
        )

    }

}

class MonopolyGame(context: Context)
  extends ConnectionStateListener with NotificationListener {

  private trait ResponseCallbackWithErrorForwarding extends ServerConnection.ResponseCallback {

    override def onErrorReceived(errorResponse: ErrorResponse) =
      listener.foreach(_.onErrorResponse(errorResponse))

  }

  // TODO: Review all other project Scala classes for non-private fields

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
  private var identities: Map[MemberId, IdentityWithBalance] = _
  private var players: Map[MemberId, PlayerWithBalanceAndConnectionState] = _

  private var listener = Option.empty[Listener]

  def connectCreateAndOrJoinZone() =
    serverConnection.connect()

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
            "type" -> MonopolyGame.ZoneType.name,
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

  def deleteIdentity(identityId: MemberId) {
    val member = zone.members(identityId)
    serverConnection.sendCommand(
      UpdateMemberCommand(
        zoneId.get,
        identityId,
        member.copy(
          metadata = Some(
            member.metadata.getOrElse(Json.obj()) ++ Json.obj("hidden" -> true)
          )
        )
      ),
      noopResponseCallback
    )
  }

  private def disconnect() =
    serverConnection.disconnect()

  def getGameName =
    if (zone == null) {
      null
    } else {
      zone.name
    }

  def getIdentityName(identityId: MemberId) =
    zone.members(identityId).name

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
              _.asOpt[String].contains(MonopolyGame.ZoneType.name)
            ))) {
            // TODO
          }

          listener.foreach(_.onJoined(zoneId))

          zone = joinZoneResponse.zone
          connectedClients = joinZoneResponse.connectedClients
          balances = Map.empty.withDefaultValue(BigDecimal(0))
          currency = MonopolyGame.currencyFromMetadata(zone.metadata)

          listener.foreach(_.onGameNameChanged(zone.name))

          val iterator = zone.transactions.valuesIterator
          while (iterator.hasNext) {
            val transaction = iterator.next()
            balances = balances +
              (transaction.from -> (balances(transaction.from) - transaction.value)) +
              (transaction.to -> (balances(transaction.to) + transaction.value))
          }

          identities = MonopolyGame.identitiesFromAccounts(
            zone.accounts,
            balances,
            currency,
            zone.members,
            ClientKey.getInstance(context).getPublicKey,
            zone.accounts.get(zone.equityAccountId).fold[Set[MemberId]](Set.empty)(_.owners)
          )

          listener.foreach(_.onIdentitiesChanged(identities))

          players = MonopolyGame.playersFromAccounts(
            zone.accounts,
            balances,
            currency,
            zone.members,
            connectedClients
          )

          listener.foreach(_.onPlayersChanged(players.values))

          val partiallyCreatedIdentities = zone.members.filter { case (memberId, member) =>
            ClientKey.getInstance(context).getPublicKey == member.publicKey &&
              !zone.accounts.values.exists(_.owners == Set(memberId))
          }
          partiallyCreatedIdentities.keys.foreach(createAccount)

          // TODO: Chain this so it only happens after partially created identities have had their
          // accounts created.
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

  def onNotificationReceived(notification: Notification) {
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

            val joinedPlayers = MonopolyGame.playersFromAccounts(
              zone.accounts,
              balances,
              currency,
              zone.members.filter { case (_, member) =>
                member.publicKey == clientJoinedZoneNotification.publicKey
              },
              Set(clientJoinedZoneNotification.publicKey)
            )

            players = players ++ joinedPlayers

            listener.foreach(_.onPlayersChanged(players.values))

          case clientQuitZoneNotification: ClientQuitZoneNotification =>

            connectedClients = connectedClients - clientQuitZoneNotification.publicKey

            val quitPlayers = MonopolyGame.playersFromAccounts(
              zone.accounts,
              balances,
              currency,
              zone.members.filter { case (_, member) =>
                member.publicKey == clientQuitZoneNotification.publicKey
              },
              Set.empty
            )

            players = players ++ quitPlayers

            listener.foreach(_.onPlayersChanged(players.values))

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

            val isIdentityCreation = !identities.contains(memberCreatedNotification.memberId) &&
              memberCreatedNotification.member.publicKey ==
                ClientKey.getInstance(context).getPublicKey &&
              zone.accounts.values.exists(_.owners == Set(memberCreatedNotification.memberId))

            val createdIdentity = MonopolyGame.identitiesFromAccounts(
              zone.accounts,
              balances,
              currency,
              Map(
                memberCreatedNotification.memberId ->
                  memberCreatedNotification.member
              ),
              ClientKey.getInstance(context).getPublicKey,
              zone.accounts.get(zone.equityAccountId).fold[Set[MemberId]](Set.empty)(_.owners)
            )

            createdIdentity.foreach { createdIdentity =>
              identities = identities + createdIdentity
              listener.foreach(_.onIdentitiesChanged(identities))
            }

            if (isIdentityCreation) {
              listener.foreach(_.onIdentityCreated(identities(memberCreatedNotification.memberId)))
            }

            val createdPlayer = MonopolyGame.playersFromAccounts(
              zone.accounts,
              balances,
              currency,
              Map(
                memberCreatedNotification.memberId ->
                  memberCreatedNotification.member
              ),
              connectedClients
            )

            createdPlayer.foreach { createdPlayer =>
              players = players + createdPlayer
              listener.foreach(_.onPlayersChanged(players.values))
            }

          case memberUpdatedNotification: MemberUpdatedNotification =>

            zone = zone.copy(
              members = zone.members
                + (memberUpdatedNotification.memberId ->
                memberUpdatedNotification.member)
            )

            val isIdentityReceipt = !identities.contains(memberUpdatedNotification.memberId) &&
              memberUpdatedNotification.member.publicKey ==
                ClientKey.getInstance(context).getPublicKey

            identities = MonopolyGame.identitiesFromAccounts(
              zone.accounts,
              balances,
              currency,
              zone.members,
              ClientKey.getInstance(context).getPublicKey,
              zone.accounts.get(zone.equityAccountId).fold[Set[MemberId]](Set.empty)(_.owners)
            )

            listener.foreach(_.onIdentitiesChanged(identities))

            if (isIdentityReceipt) {
              listener.foreach(_.onIdentityReceived(identities(memberUpdatedNotification.memberId)))
            }

            players = MonopolyGame.playersFromAccounts(
              zone.accounts,
              balances,
              currency,
              zone.members,
              connectedClients
            )

            listener.foreach(_.onPlayersChanged(players.values))

          case accountCreatedNotification: AccountCreatedNotification =>

            zone = zone.copy(
              accounts = zone.accounts
                + (accountCreatedNotification.accountId ->
                accountCreatedNotification.account)
            )

            val isIdentityCreation = accountCreatedNotification.account.owners.size == 1 &&
              zone.members.get(accountCreatedNotification.account.owners.head).fold(false)(
                _.publicKey == ClientKey.getInstance(context).getPublicKey
              )

            val createdIdentity = MonopolyGame.identitiesFromAccounts(
              Map(
                accountCreatedNotification.accountId ->
                  zone.accounts(accountCreatedNotification.accountId)
              ),
              balances,
              currency,
              zone.members,
              ClientKey.getInstance(context).getPublicKey,
              zone.accounts.get(zone.equityAccountId).fold[Set[MemberId]](Set.empty)(_.owners)
            )

            createdIdentity.foreach { createdIdentity =>
              identities = identities + createdIdentity
              listener.foreach(_.onIdentitiesChanged(identities))
            }

            if (isIdentityCreation) {
              listener.foreach(_.onIdentityCreated(
                identities(accountCreatedNotification.account.owners.head)
              ))
            }

            val createdPlayer = MonopolyGame.playersFromAccounts(
              Map(
                accountCreatedNotification.accountId ->
                  zone.accounts(accountCreatedNotification.accountId)
              ),
              balances,
              currency,
              zone.members,
              connectedClients
            )

            createdPlayer.foreach { createdPlayer =>
              players = players + createdPlayer
              listener.foreach(_.onPlayersChanged(players.values))
            }

          case accountUpdatedNotification: AccountUpdatedNotification =>

            zone = zone.copy(
              accounts = zone.accounts
                + (accountUpdatedNotification.accountId ->
                accountUpdatedNotification.account)
            )

            identities = MonopolyGame.identitiesFromAccounts(
              zone.accounts,
              balances,
              currency,
              zone.members,
              ClientKey.getInstance(context).getPublicKey,
              zone.accounts.get(zone.equityAccountId).fold[Set[MemberId]](Set.empty)(_.owners)
            )

            listener.foreach(_.onIdentitiesChanged(identities))

            players = MonopolyGame.playersFromAccounts(
              zone.accounts,
              balances,
              currency,
              zone.members,
              connectedClients
            )

            listener.foreach(_.onPlayersChanged(players.values))

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

            val updatedIdentities = MonopolyGame.identitiesFromAccounts(
              zone.accounts.filterKeys(
                accountId => accountId == transaction.from || accountId == transaction.to
              ),
              balances,
              currency,
              zone.members,
              ClientKey.getInstance(context).getPublicKey,
              zone.accounts.get(zone.equityAccountId).fold[Set[MemberId]](Set.empty)(_.owners)
            )

            identities = identities ++ updatedIdentities
            listener.foreach(_.onIdentitiesChanged(identities))

            val updatedPlayers = MonopolyGame.playersFromAccounts(
              zone.accounts.filterKeys(
                accountId => accountId == transaction.from || accountId == transaction.to
              ),
              balances,
              currency,
              zone.members,
              connectedClients
            )

            players = players ++ updatedPlayers
            listener.foreach(_.onPlayersChanged(players.values))

        }

    }

  }

  def onStateChanged(connectionState: ServerConnection.ConnectionState) {
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

  def setIdentityName(identityId: MemberId, name: String) {
    serverConnection.sendCommand(
      UpdateMemberCommand(
        zoneId.get,
        identityId,
        zone.members(identityId).copy(name = name)
      ),
      noopResponseCallback
    )
  }

  def setListener(listener: MonopolyGame.Listener) {
    this.listener = Option(listener)
    this.listener.foreach(listener =>
      if (zone == null) {
        listener.onQuit()
      } else {
        listener.onJoined(zoneId.get)
        listener.onIdentitiesChanged(identities)
        listener.onPlayersChanged(players.values)
      }
    )
  }

  def setZoneId(zoneId: ZoneId) {
    this.zoneId = Option(zoneId)
  }

  def transfer(identityId: MemberId,
               newOwnerPublicKey: PublicKey) {
    serverConnection.sendCommand(
      UpdateMemberCommand(
        zoneId.get,
        identityId,
        identities(identityId).member.copy(publicKey = newOwnerPublicKey)
      ),
      noopResponseCallback
    )
  }

  def transfer(actingAs: Identity,
               from: Player,
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
