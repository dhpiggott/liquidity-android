package com.dhpcs.liquidity

import java.util.{Currency, Locale}

import android.content.{ContentUris, ContentValues, Context}
import android.provider.ContactsContract
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
                                 balanceWithCurrency: (BigDecimal, Option[Either[String, Currency]]))
    extends Identity

  trait Listener {

    def onErrorResponse(errorResponse: ErrorResponse)

    def onIdentitiesChanged(identities: Map[MemberId, IdentityWithBalance])

    def onIdentityCreated(identity: IdentityWithBalance)

    def onIdentityReceived(identity: IdentityWithBalance)

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

  // TODO: Not on the UI thread!
  private def getUserName(context: Context) = {
    val cursor = context.getContentResolver.query(
      ContactsContract.Profile.CONTENT_URI,
      Array("display_name"),
      null,
      null,
      null
    )
    val userName = if (cursor.moveToNext) {
      cursor.getString(cursor.getColumnIndex("display_name"))
    } else {
      context.getString(R.string.unnamed)
    }
    cursor.close()
    userName
  }

  private def identitiesFromAccounts(accounts: Map[AccountId, Account],
                                     balances: Map[AccountId, BigDecimal],
                                     currency: Option[Either[String, Currency]],
                                     members: Map[MemberId, Member],
                                     clientPublicKey: PublicKey,
                                     equityAccountOwners: Set[MemberId]) =
    accounts.collect {
      case (accountId, account) if account.owners.size == 1
        && members.get(account.owners.head).fold(false)(_.publicKey == clientPublicKey) =>

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

  private def playersFromAccounts(accounts: Map[AccountId, Account],
                                  balances: Map[AccountId, BigDecimal],
                                  currency: Option[Either[String, Currency]],
                                  members: Map[MemberId, Member],
                                  connectedPublicKeys: Set[PublicKey]) =
    accounts.collect {
      case (accountId, account) if account.owners.size == 1
        && members.contains(account.owners.head) =>

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

  private var gameId = Option.empty[Future[Long]]
  private var zoneId = Option.empty[ZoneId]
  private var selectedIdentityId = Option.empty[MemberId]

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
    val identityName = MonopolyGame.getUserName(context)

    log.debug("identityName={}", identityName)

    val zoneName = context.getString(R.string.game_name_format_string, identityName)

    serverConnection.sendCommand(
      CreateZoneCommand(
        zoneName,
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

          gameId = Some(
            Future {
              val contentValues = new ContentValues
              contentValues.put(Games.GAME_TYPE, MONOPOLY.name)
              contentValues.put(Games.ZONE_ID, createZoneResponse.zoneId.id.toString)
              contentValues.put(Games.NAME, zoneName)
              // TODO: Add created to response
              //              contentValues.put(Games.CREATED, createZoneResponse.created: java.lang.Long)
              contentValues.put(Games.CREATED, System.currentTimeMillis: java.lang.Long)
              ContentUris.parseId(
                context.getContentResolver.insert(
                  Games.CONTENT_URI,
                  contentValues
                )
              )
            }
          )
          zoneId = Some(createZoneResponse.zoneId)

          join(createZoneResponse.zoneId)

        }

      }
    )
  }

  def createIdentity(name: String) {
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

  private def createIdentity() {
    val identityName = MonopolyGame.getUserName(context)

    log.debug("identityName={}", identityName)

    createIdentity(identityName)
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

          listener.foreach(_.onJoined(zoneId))

          zone = joinZoneResponse.zone
          connectedClients = joinZoneResponse.connectedClients
          balances = Map.empty.withDefaultValue(BigDecimal(0))
          currency = MonopolyGame.currencyFromMetadata(zone.metadata)

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

          players = MonopolyGame.playersFromAccounts(
            zone.accounts,
            balances,
            currency,
            zone.members,
            connectedClients
          )

          listener.foreach { listener =>
            listener.onIdentitiesChanged(identities)
            listener.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)).values)
          }

          val partiallyCreatedIdentities = zone.members.filter { case (memberId, member) =>
            ClientKey.getInstance(context).getPublicKey == member.publicKey &&
              !zone.accounts.values.exists(_.owners == Set(memberId))
          }
          partiallyCreatedIdentities.foreach { case (memberId, member) =>
            createAccount(memberId)
          }

          if (!identities.values.exists(_.accountId != zone.equityAccountId)) {
            createIdentity()
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
            listener.foreach(
              _.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)).values)
            )

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
            listener.foreach(
              _.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)).values)
            )

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

            zone = zone.copy(name = zoneNameSetNotification.name)

          case memberCreatedNotification: MemberCreatedNotification =>

            zone = zone.copy(
              members = zone.members
                + (memberCreatedNotification.memberId ->
                memberCreatedNotification.member)
            )

            val isIdentityCreation = !identities.contains(memberCreatedNotification.memberId) &&
              memberCreatedNotification.member.publicKey ==
                ClientKey.getInstance(context).getPublicKey &&
              zone.accounts.exists { case (_, account) =>
                account.owners == Set(memberCreatedNotification.memberId)
              }

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

            createdIdentity.foreach { createdIdentity =>
              identities = identities + createdIdentity
              listener.foreach(_.onIdentitiesChanged(identities))
            }

            createdPlayer.foreach { createdPlayer =>
              players = players + createdPlayer
              listener.foreach(
                _.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)).values)
              )
            }

            if (isIdentityCreation) {
              listener.foreach(_.onIdentityCreated(identities(memberCreatedNotification.memberId)))
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

            players = MonopolyGame.playersFromAccounts(
              zone.accounts,
              balances,
              currency,
              zone.members,
              connectedClients
            )

            listener.foreach { listener =>
              listener.onIdentitiesChanged(identities)
              listener.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)).values)
            }

            if (isIdentityReceipt) {
              listener.foreach(_.onIdentityReceived(identities(memberUpdatedNotification.memberId)))
            }

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

            createdIdentity.foreach { createdIdentity =>
              identities = identities + createdIdentity
              listener.foreach(_.onIdentitiesChanged(identities))
            }

            createdPlayer.foreach { createdPlayer =>
              players = players + createdPlayer
              listener.foreach(
                _.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)).values)
              )
            }

            if (isIdentityCreation) {
              listener.foreach(_.onIdentityCreated(
                identities(accountCreatedNotification.account.owners.head)
              ))
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

            players = MonopolyGame.playersFromAccounts(
              zone.accounts,
              balances,
              currency,
              zone.members,
              connectedClients
            )

            listener.foreach { listener =>
              listener.onIdentitiesChanged(identities)
              listener.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)).values)
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

            val updatedPlayers = MonopolyGame.playersFromAccounts(
              zone.accounts.filterKeys(
                accountId => accountId == transaction.from || accountId == transaction.to
              ),
              balances,
              currency,
              zone.members,
              connectedClients
            )

            identities = identities ++ updatedIdentities
            listener.foreach(_.onIdentitiesChanged(identities))

            players = players ++ updatedPlayers
            listener.foreach(
              _.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)).values)
            )

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

  def setGameName(gameName: String) {
    serverConnection.sendCommand(
      SetZoneNameCommand(
        zoneId.get,
        gameName
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
        listener.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)).values)
      }
    )
  }

  def setSelectedIdentity(selectedIdentityId: MemberId) {
    this.selectedIdentityId = Option(selectedIdentityId)
    listener.foreach(
      _.onPlayersChanged(players.filterKeys(selectedIdentityId != _).values)
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
