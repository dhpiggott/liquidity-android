package com.dhpcs.liquidity

import android.content.{ContentUris, ContentValues, Context}
import android.provider.ContactsContract
import com.dhpcs.liquidity.MonopolyGame.{IdentityWithBalance, Listener, PlayerWithBalance, PlayerWithBalanceAndConnectionState}
import com.dhpcs.liquidity.ServerConnection.{ConnectionStateListener, NotificationListener, ResponseCallback}
import com.dhpcs.liquidity.models._
import com.dhpcs.liquidity.provider.LiquidityContract
import com.dhpcs.liquidity.provider.LiquidityContract.Games
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MonopolyGame {

  trait IdentityWithBalance {

    def member: Member

    def balanceWithCurrencyCode: (BigDecimal, String)

  }

  case class BankerWithBalance(member: Member,
                               balanceWithCurrencyCode: (BigDecimal, String))
    extends IdentityWithBalance

  case class PlayerWithBalance(member: Member,
                               balanceWithCurrencyCode: (BigDecimal, String))
    extends IdentityWithBalance

  case class PlayerWithBalanceAndConnectionState(member: Member,
                                                 balanceWithCurrencyCode: (BigDecimal, String),
                                                 isConnected: Boolean)

  trait Listener {

    def onErrorResponse(errorResponse: ErrorResponse)

    def onIdentitiesChanged(identities: Map[MemberId, IdentityWithBalance])

    def onJoined(zoneId: ZoneId)

    def onPlayersChanged(players: Map[MemberId, PlayerWithBalanceAndConnectionState])

    def onQuit()

  }

  private val ZoneType = GameType.MONOPOLY

  private def aggregateMembersAccountBalances(memberIds: Set[MemberId],
                                              accounts: Map[AccountId, Account],
                                              accountBalances: Map[AccountId, BigDecimal]) =
    memberIds.map {
      memberId =>
        val membersAccountIds = accounts.filter {
          case (_, account) =>
            account.owners.contains(memberId)
        }.keys
        val membersAccountBalances = membersAccountIds.map(
          accountBalances.getOrElse(_, BigDecimal(0))
        )
        memberId -> membersAccountBalances.sum
    }.toMap

  private def getUserName(context: Context, aliasConstant: String) = {
    val cursor = context.getContentResolver.query(
      ContactsContract.Profile.CONTENT_URI,
      Array(aliasConstant),
      null,
      null,
      null
    )
    val userName = if (cursor.moveToNext) {
      cursor.getString(cursor.getColumnIndex(aliasConstant))
    } else {
      context.getString(R.string.unnamed)
    }
    cursor.close()
    userName
  }

  private def identitiesFromMembers(members: Map[MemberId, Member],
                                    balances: Map[MemberId, BigDecimal],
                                    clientPublicKey: PublicKey,
                                    equityHolderMemberId: MemberId) =
    members.filter {
      case (memberId, member) =>
        member.publicKey == clientPublicKey
    }.map {
      case (memberId, member) if memberId != equityHolderMemberId =>
        memberId -> PlayerWithBalance(
          member,
          // TODO: Get from zone, and have zone creator set it
          (balances.getOrElse(memberId, BigDecimal(0)).bigDecimal, "GBP")
        )
      case (memberId, member) =>
        memberId -> BankerWithBalance(
          member,
          // TODO: Get from zone, and have zone creator set it
          (balances.getOrElse(memberId, BigDecimal(0)).bigDecimal, "GBP")
        )
    }

  private def identityFromMember(memberId: MemberId,
                                 member: Member,
                                 balances: Map[MemberId, BigDecimal],
                                 clientPublicKey: PublicKey,
                                 equityHolderMemberId: MemberId) =
    identitiesFromMembers(
      Map(memberId -> member),
      balances,
      clientPublicKey,
      equityHolderMemberId
    ).headOption

  private def playersFromMembers(members: Map[MemberId, Member],
                                 balances: Map[MemberId, BigDecimal],
                                 connectedPublicKeys: Set[PublicKey]) =
    members.map {
      case (memberId, member) =>
        memberId -> PlayerWithBalanceAndConnectionState(
          member,
          // TODO: Get from zone, and have zone creator set it
          (balances.getOrElse(memberId, BigDecimal(0)).bigDecimal, "GBP"),
          connectedPublicKeys.contains(member.publicKey)
        )
    }

  private def playerFromMember(memberId: MemberId,
                               member: Member,
                               balances: Map[MemberId, BigDecimal],
                               connectedPublicKeys: Set[PublicKey]) =
    playersFromMembers(
      Map(memberId -> member),
      balances,
      connectedPublicKeys
    ).headOption

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
  private var accountBalances: Map[AccountId, BigDecimal] = _
  private var memberBalances: Map[MemberId, BigDecimal] = _
  private var identities: Map[MemberId, IdentityWithBalance] = _
  private var players: Map[MemberId, PlayerWithBalanceAndConnectionState] = _

  private var listener = Option.empty[Listener]

  def connectCreateAndOrJoinZone() =
    serverConnection.connect()

  private def createAndThenJoinZone() {
    val playerName = MonopolyGame.getUserName(
      context,
      "display_name"
    )

    log.debug("playerName={}", playerName)

    serverConnection.sendCommand(
      CreateZoneCommand(
        context.getString(R.string.game_name_format_string, playerName),
        MonopolyGame.ZoneType.typeName,
        Member(
          context.getString(R.string.bank_member_name),
          ClientKey.getInstance(context).getPublicKey
        ),
        Account(
          context.getString(R.string.bank_member_name),
          Set.empty
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
    serverConnection.sendCommand(
      CreateMemberCommand(
        zoneId.get,
        Member(
          name,
          ClientKey.getInstance(context).getPublicKey)
      ),
      new ResponseCallbackWithErrorForwarding {

        override def onResultReceived(resultResponse: ResultResponse) {
          log.debug("resultResponse={}", resultResponse)

          val createMemberResponse = resultResponse.asInstanceOf[CreateMemberResponse]

          serverConnection.sendCommand(
            CreateAccountCommand(
              zoneId.get,
              Account(
                name,
                Set(createMemberResponse.memberId)
              )
            ),
            noopResponseCallback
          )
        }

      }
    )
  }

  private def createIdentity() {
    val identityName = MonopolyGame.getUserName(
      context,
      "display_name"
    )

    log.debug("identityName={}", identityName)

    createIdentity(identityName)
  }

  private def disconnect() =
    serverConnection.disconnect()

  private def join(zoneId: ZoneId) {
    serverConnection.sendCommand(
      JoinZoneCommand(
        zoneId
      ),
      new ResponseCallbackWithErrorForwarding {

        override def onResultReceived(resultResponse: ResultResponse) {
          log.debug("resultResponse={}", resultResponse)

          val joinZoneResponse = resultResponse.asInstanceOf[JoinZoneResponse]

          listener.foreach(_.onJoined(zoneId))

          zone = joinZoneResponse.zone
          connectedClients = joinZoneResponse.connectedClients
          accountBalances = Map.empty

          val contentValues = new ContentValues
          contentValues.put(LiquidityContract.Games.GAME_TYPE, GameType.MONOPOLY.typeName)
          contentValues.put(LiquidityContract.Games.ZONE_ID, zoneId.id.toString)
          contentValues.put(LiquidityContract.Games.NAME, zone.name)
          contentValues.put(LiquidityContract.Games.CREATED, zone.created: java.lang.Long)
          gameId = gameId.fold(
            Some(
              Future(
                ContentUris.parseId(
                  context.getContentResolver.insert(
                    LiquidityContract.Games.CONTENT_URI,
                    contentValues
                  )
                )
              )
            )
          ) { gameId =>
            gameId.onSuccess { case id =>
              Future(
                context.getContentResolver.update(
                  ContentUris.withAppendedId(Games.CONTENT_URI, id),
                  contentValues,
                  null,
                  null
                )
              )
            }
            Some(gameId)
          }

          val iterator = zone.transactions.valuesIterator
          while (iterator.hasNext) {
            val transaction = iterator.next()
            val newSourceBalance = accountBalances
              .getOrElse(transaction.from, BigDecimal(0)) - transaction.amount
            val newDestinationBalance = accountBalances
              .getOrElse(transaction.to, BigDecimal(0)) + transaction.amount
            accountBalances = accountBalances +
              (transaction.from -> newSourceBalance) +
              (transaction.to -> newDestinationBalance)
          }

          memberBalances = MonopolyGame.aggregateMembersAccountBalances(
            zone.members.keySet,
            zone.accounts,
            accountBalances
          )

          identities = MonopolyGame.identitiesFromMembers(
            zone.members,
            memberBalances,
            ClientKey.getInstance(context).getPublicKey,
            zone.equityHolderMemberId
          )

          players = MonopolyGame.playersFromMembers(
            zone.members,
            memberBalances,
            connectedClients
          )

          listener.foreach { listener =>
            listener.onIdentitiesChanged(identities)
            listener.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)))
          }

          if (!identities.values.exists(_.isInstanceOf[PlayerWithBalance])) {
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

            val joinedPlayers = MonopolyGame.playersFromMembers(
              zone.members.filter {
                case (_, member) => member.publicKey == clientJoinedZoneNotification.publicKey
              },
              memberBalances,
              Set(clientJoinedZoneNotification.publicKey)
            )

            players = players ++ joinedPlayers
            listener.foreach(
              _.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)))
            )

          case clientQuitZoneNotification: ClientQuitZoneNotification =>

            connectedClients = connectedClients - clientQuitZoneNotification.publicKey

            val quitPlayers = MonopolyGame.playersFromMembers(
              zone.members.filter {
                case (_, member) => member.publicKey == clientQuitZoneNotification.publicKey
              },
              memberBalances,
              Set.empty
            )

            players = players ++ quitPlayers
            listener.foreach(
              _.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)))
            )

          case zoneTerminatedNotification: ZoneTerminatedNotification =>

            zone = null
            connectedClients = null
            accountBalances = null
            memberBalances = null
            identities = null
            players = null

            listener.foreach(_.onQuit())

            join(zoneId)

          case zoneNameSetNotification: ZoneNameSetNotification =>

            val contentValues = new ContentValues
            contentValues.put(LiquidityContract.Games.ZONE_ID, zoneId.id.toString)
            contentValues.put(LiquidityContract.Games.NAME, zoneNameSetNotification.name)
            gameId.foreach(_.onSuccess { case id =>
              Future(
                context.getContentResolver.update(
                  ContentUris.withAppendedId(Games.CONTENT_URI, id),
                  contentValues,
                  null,
                  null
                )
              )
            })

            zone = zone.copy(name = zoneNameSetNotification.name)

          case memberCreatedNotification: MemberCreatedNotification =>

            zone = zone.copy(
              members = zone.members
                + (memberCreatedNotification.memberId ->
                memberCreatedNotification.member)
            )

            memberBalances = memberBalances + (memberCreatedNotification.memberId -> BigDecimal(0))

            val createdIdentity = MonopolyGame.identityFromMember(
              memberCreatedNotification.memberId,
              memberCreatedNotification.member,
              memberBalances,
              ClientKey.getInstance(context).getPublicKey,
              zone.equityHolderMemberId
            )

            createdIdentity.foreach { createdIdentity =>
              identities = identities + createdIdentity
              listener.foreach(_.onIdentitiesChanged(identities))
            }

            val createdPlayer = MonopolyGame.playerFromMember(
              memberCreatedNotification.memberId,
              memberCreatedNotification.member,
              memberBalances,
              connectedClients
            )

            players = players ++ createdPlayer
            listener.foreach(
              _.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)))
            )

          case memberUpdatedNotification: MemberUpdatedNotification =>

            zone = zone.copy(
              members = zone.members
                + (memberUpdatedNotification.memberId ->
                memberUpdatedNotification.member)
            )

            val updatedIdentity = MonopolyGame.identityFromMember(
              memberUpdatedNotification.memberId,
              memberUpdatedNotification.member,
              memberBalances,
              ClientKey.getInstance(context).getPublicKey,
              zone.equityHolderMemberId
            )

            updatedIdentity.foreach { updatedIdentity =>
              if (players.contains(updatedIdentity._1)) {
                players = players - updatedIdentity._1
                listener.foreach(
                  _.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)))
                )
              }
              identities = identities + updatedIdentity
              listener.foreach(_.onIdentitiesChanged(identities))
            }

            val updatedPlayer = MonopolyGame.playerFromMember(
              memberUpdatedNotification.memberId,
              memberUpdatedNotification.member,
              memberBalances,
              connectedClients
            )

            updatedPlayer.foreach { updatedPlayer =>
              if (identities.contains(updatedPlayer._1)) {
                identities = identities - updatedPlayer._1
                listener.foreach(_.onIdentitiesChanged(identities))
              }
              players = players + updatedPlayer
              listener.foreach(
                _.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)))
              )
            }

          case accountCreatedNotification: AccountCreatedNotification =>

            zone = zone.copy(
              accounts = zone.accounts
                + (accountCreatedNotification.accountId ->
                accountCreatedNotification.account)
            )

          case accountUpdatedNotification: AccountUpdatedNotification =>

            zone = zone.copy(
              accounts = zone.accounts
                + (accountUpdatedNotification.accountId ->
                accountUpdatedNotification.account)
            )

            val updatedMemberBalances = MonopolyGame.aggregateMembersAccountBalances(
              zone.members.keySet,
              zone.accounts,
              accountBalances
            ).filterNot {
              case (memberId, balance) => memberBalances(memberId) == balance
            }

            memberBalances = memberBalances ++ updatedMemberBalances

            val updatedIdentities = MonopolyGame.identitiesFromMembers(
              zone.members.filterKeys(updatedMemberBalances.contains),
              updatedMemberBalances,
              ClientKey.getInstance(context).getPublicKey,
              zone.equityHolderMemberId
            )
            val updatedPlayers = MonopolyGame.playersFromMembers(
              zone.members.filterKeys(updatedMemberBalances.contains),
              updatedMemberBalances,
              connectedClients
            )

            identities = identities ++ updatedIdentities
            listener.foreach(_.onIdentitiesChanged(identities))

            players = players ++ updatedPlayers
            listener.foreach(
              _.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)))
            )

          case transactionAddedNotification: TransactionAddedNotification =>

            zone = zone.copy(
              transactions = zone.transactions
                + (transactionAddedNotification.transactionId ->
                transactionAddedNotification.transaction)
            )

            val transaction = transactionAddedNotification.transaction
            val newSourceBalance = accountBalances
              .getOrElse(transaction.from, BigDecimal(0)) - transaction.amount
            val newDestinationBalance = accountBalances
              .getOrElse(transaction.to, BigDecimal(0)) + transaction.amount
            accountBalances = accountBalances +
              (transaction.from -> newSourceBalance) +
              (transaction.to -> newDestinationBalance)

            val updatedMemberBalances = MonopolyGame.aggregateMembersAccountBalances(
              zone.members.keySet,
              zone.accounts,
              accountBalances
            ).filterNot {
              case (memberId, balance) => memberBalances(memberId) == balance
            }

            memberBalances = memberBalances ++ updatedMemberBalances

            val updatedIdentities = MonopolyGame.identitiesFromMembers(
              zone.members.filterKeys(updatedMemberBalances.contains),
              updatedMemberBalances,
              ClientKey.getInstance(context).getPublicKey,
              zone.equityHolderMemberId
            )
            val updatedPlayers = MonopolyGame.playersFromMembers(
              zone.members.filterKeys(updatedMemberBalances.contains),
              updatedMemberBalances,
              connectedClients
            )

            identities = identities ++ updatedIdentities
            listener.foreach(_.onIdentitiesChanged(identities))

            players = players ++ updatedPlayers
            listener.foreach(
              _.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)))
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

            listener.foreach(_.onQuit())

            zone = null
            disconnect()

          }

        }
      )
    }

  def getGameName =
    if (zone == null) {
      null
    } else {
      zone.name
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

  def setListener(listener: MonopolyGame.Listener) {
    this.listener = Option(listener)
    this.listener.foreach(listener =>
      if (zone == null) {
        listener.onQuit()
      } else {
        listener.onJoined(zoneId.get)
        listener.onIdentitiesChanged(identities)
        listener.onPlayersChanged(players.filterKeys(!selectedIdentityId.contains(_)))
      }
    )
  }

  def setSelectedIdentity(selectedIdentityId: MemberId) {
    this.selectedIdentityId = Option(selectedIdentityId)
    listener.foreach(_.onPlayersChanged(players.filterKeys(selectedIdentityId != _)))
  }

  def setZoneId(zoneId: ZoneId) {
    this.zoneId = Option(zoneId)
  }

  def transfer(fromMember: MemberId, toMember: MemberId, amount: BigDecimal) {
    val fromMemberId = identities.headOption.map {
      case (memberId, _) => memberId
    }
    val fromAccountId = fromMemberId.fold[Option[AccountId]](
      None
    )(fromAccount =>
      zone.accounts.collectFirst {
        case (accountId, account) if account.owners == Set(fromMember) => accountId
      })
    val toAccountId = zone.accounts.collectFirst {
      case (accountId, account) if account.owners == Set(toMember) => accountId
    }
    if (fromAccountId.isEmpty || toAccountId.isEmpty) {
      // TODO
    } else {
      serverConnection.sendCommand(
        AddTransactionCommand(
          zoneId.get,
          // TODO
          "",
          fromAccountId.get,
          toAccountId.get,
          amount
        ),
        noopResponseCallback
      )
    }
  }

}
