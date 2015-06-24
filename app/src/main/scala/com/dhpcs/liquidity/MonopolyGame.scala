package com.dhpcs.liquidity

import android.content.{ContentUris, ContentValues, Context}
import android.provider.ContactsContract
import com.dhpcs.liquidity.MonopolyGame.{IdentityWithBalance, Listener, PlayerWithBalance, PlayerWithBalanceAndConnectionState}
import com.dhpcs.liquidity.ServerConnection.{ConnectionStateListener, NotificationListener, ResponseCallback}
import com.dhpcs.liquidity.models._
import com.dhpcs.liquidity.provider.LiquidityContract
import com.dhpcs.liquidity.provider.LiquidityContract.Games
import org.slf4j.LoggerFactory

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

    def onIdentitiesChanged(identities: Map[MemberId, IdentityWithBalance])

    def onIdentityAdded(addedIdentity: (MemberId, IdentityWithBalance))

    def onIdentityRemoved(removedIdentity: (MemberId, IdentityWithBalance))

    def onIdentityUpdated(removedIdentity: (MemberId, IdentityWithBalance),
                          addedIdentity: (MemberId, IdentityWithBalance))

    def onJoined(zoneId: ZoneId)

    def onPlayersChanged(players: Map[MemberId, PlayerWithBalanceAndConnectionState])

    def onPlayerAdded(addedPlayer: (MemberId, PlayerWithBalanceAndConnectionState))

    def onPlayerRemoved(removedPlayer: (MemberId, PlayerWithBalanceAndConnectionState))

    def onPlayerUpdated(removedPlayer: (MemberId, PlayerWithBalanceAndConnectionState),
                        addedPlayer: (MemberId, PlayerWithBalanceAndConnectionState))

    def onQuit()

  }

  val ZONE_TYPE = GameType.MONOPOLY
  val BANK_MEMBER_NAME = "Banker"
  val BANK_ACCOUNT_NAME = "Bank"
  val GAME_NAME_PREFIX = "Bank of "
  val ACCOUNT_NAME_SUFFIX = "'s account"

  def aggregateMembersAccountBalances(zone: Zone,
                                      accountBalances: Map[AccountId, BigDecimal]) =
    zone.members.map {
      case (memberId, _) =>
        val membersAccounts = zone.accounts.filter {
          case (_, account) =>
            account.owners.contains(memberId)
        }
        val membersAccountBalances = membersAccounts.map {
          case (accountId, _) =>
            accountId -> accountBalances.getOrElse(accountId, BigDecimal(0))
        }
        memberId -> membersAccountBalances.values.sum
    }

  def getUserName(context: Context, aliasConstant: String): String = {
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
      "null"
    }
    cursor.close()
    userName
  }

  // TODO: Partition?

  def identitiesFromMembers(members: Map[MemberId, Member],
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

  def identityFromMember(memberId: MemberId,
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

  def playersFromMembers(members: Map[MemberId, Member],
                         balances: Map[MemberId, BigDecimal],
                         connectedPublicKeys: Set[PublicKey],
                         clientPublicKey: PublicKey,
                         equityHolderMemberId: MemberId) =
    members.filter {
      case (memberId, member) =>
        memberId != equityHolderMemberId && member.publicKey != clientPublicKey
    }.map {
      case (memberId, member) =>
        memberId -> PlayerWithBalanceAndConnectionState(
          member,
          // TODO: Get from zone, and have zone creator set it
          (balances.getOrElse(memberId, BigDecimal(0)).bigDecimal, "GBP"),
          connectedPublicKeys.contains(member.publicKey)
        )
    }

  def playerFromMember(memberId: MemberId,
                       member: Member,
                       balances: Map[MemberId, BigDecimal],
                       connectedPublicKeys: Set[PublicKey],
                       clientPublicKey: PublicKey,
                       equityHolderMemberId: MemberId) =
    playersFromMembers(
      Map(memberId -> member),
      balances,
      connectedPublicKeys,
      clientPublicKey,
      equityHolderMemberId
    ).headOption

}

class MonopolyGame(val context: Context)
  extends ConnectionStateListener with NotificationListener {

  private val log = LoggerFactory.getLogger(getClass)

  val serverConnection = new ServerConnection(context, this, this)

  var initialCapital: BigDecimal = _
  var zoneId: ZoneId = _
  // TODO
  var gameId: Long = _

  private var zone: Zone = _
  private var connectedClients: Set[PublicKey] = _
  private var accountBalances: Map[AccountId, BigDecimal] = _
  private var memberBalances: Map[MemberId, BigDecimal] = _
  private var identities: Map[MemberId, IdentityWithBalance] = _
  private var players: Map[MemberId, PlayerWithBalanceAndConnectionState] = _

  private var listener: Listener = _

  def connectCreateAndOrJoinZone() {
    serverConnection.connect()
  }

  private def createAndThenJoinZone() {
    val playerName = MonopolyGame.getUserName(
      context,
      "display_name"
    )

    log.debug("playerName={}", playerName)

    serverConnection.sendCommand(
      CreateZoneCommand(
        MonopolyGame.GAME_NAME_PREFIX + playerName,
        MonopolyGame.ZONE_TYPE.typeName,
        Member(
          MonopolyGame.BANK_MEMBER_NAME,
          ClientKey.getInstance(context).getPublicKey
        ),
        Account(
          MonopolyGame.BANK_ACCOUNT_NAME,
          Set.empty
        )
      ),
      new ResponseCallback {

        def onResultReceived(resultResponse: ResultResponse) {
          log.debug("resultResponse={}", resultResponse)

          val createZoneResponse = resultResponse.asInstanceOf[CreateZoneResponse]

          setZoneId(createZoneResponse.zoneId)
          join()

        }

      })
  }

  private def createPlayer(zoneId: ZoneId) {
    val playerName = MonopolyGame.getUserName(
      context,
      "display_name"
    )

    log.debug("playerName={}", playerName)

    serverConnection.sendCommand(
      CreateMemberCommand(
        zoneId,
        Member(
          playerName,
          ClientKey.getInstance(context).getPublicKey)
      ),
      new ResponseCallback {

        def onResultReceived(resultResponse: ResultResponse) {
          log.debug("resultResponse={}", resultResponse)

          val createMemberResponse = resultResponse.asInstanceOf[CreateMemberResponse]

          serverConnection.sendCommand(
            CreateAccountCommand(
              zoneId,
              Account(
                playerName + MonopolyGame.ACCOUNT_NAME_SUFFIX,
                Set(createMemberResponse.memberId)
              )
            ),
            new ResponseCallback() {

              def onResultReceived(resultResponse: ResultResponse) {
                log.debug("resultResponse={}", resultResponse)

                val createAccountResponse = resultResponse.asInstanceOf[CreateAccountResponse]

              }

            })
        }

      })
  }

  private def disconnect() {
    serverConnection.disconnect()
  }

  private def join() {
    serverConnection.sendCommand(
      JoinZoneCommand(
        zoneId
      ),
      new ResponseCallback {

        def onResultReceived(resultResponse: ResultResponse) {
          log.debug("resultResponse={}", resultResponse)

          val joinZoneResponse = resultResponse.asInstanceOf[JoinZoneResponse]

          if (listener != null) {
            listener.onJoined(zoneId)
          }

          zone = joinZoneResponse.zone
          connectedClients = joinZoneResponse.connectedClients
          accountBalances = Map.empty

          // TODO: Do off main thread
          val contentValues = new ContentValues
          contentValues.put(LiquidityContract.Games.GAME_TYPE, GameType.MONOPOLY.typeName)
          contentValues.put(LiquidityContract.Games.ZONE_ID, zoneId.id.toString)
          contentValues.put(LiquidityContract.Games.NAME, zone.name)
          contentValues.put(LiquidityContract.Games.CREATED, zone.created: java.lang.Long)

          if (gameId == 0) {
            context.getContentResolver.insert(
              LiquidityContract.Games.CONTENT_URI,
              contentValues
            )
          } else {
            context.getContentResolver.update(
              ContentUris.withAppendedId(Games.CONTENT_URI, gameId),
              contentValues,
              null,
              null
            )
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
            zone,
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
            connectedClients,
            ClientKey.getInstance(context).getPublicKey,
            zone.equityHolderMemberId

          )

          if (listener != null) {
            listener.onIdentitiesChanged(identities)
            listener.onPlayersChanged(players)
          }

          // TODO
          if (!identities.values.exists(_.isInstanceOf[PlayerWithBalance])) {
            createPlayer(zoneId)
          }

        }

      })
  }

  def onNotificationReceived(notification: Notification) {
    log.debug("notification={}", notification)

    notification match {

      case zoneNotification: ZoneNotification =>

        if (zoneNotification.zoneId != zoneId) {
          sys.error(s"zoneNotification.zoneId != zoneId (${zoneNotification.zoneId} != $zoneId)")
        }

        zoneNotification match {

          case clientJoinedZoneNotification: ClientJoinedZoneNotification =>

            connectedClients = connectedClients + clientJoinedZoneNotification.publicKey

            val joinedPlayers = MonopolyGame.playersFromMembers(
              zone.members.filter {
                case (_, member) => member.publicKey == clientJoinedZoneNotification.publicKey
              },
              memberBalances,
              Set(clientJoinedZoneNotification.publicKey),
              ClientKey.getInstance(context).getPublicKey,
              zone.equityHolderMemberId
            )

            if (joinedPlayers.nonEmpty) {
              val previousPlayers = players
              players = players ++ joinedPlayers
              if (listener != null) {
                joinedPlayers.foreach { joinedPlayer =>
                  val removedPlayer = previousPlayers(joinedPlayer._1)
                  listener.onPlayerUpdated(joinedPlayer._1 -> removedPlayer, joinedPlayer)
                }
              }
            }

          case clientQuitZoneNotification: ClientQuitZoneNotification =>

            connectedClients = connectedClients - clientQuitZoneNotification.publicKey

            val quitPlayers = MonopolyGame.playersFromMembers(
              zone.members.filter {
                case (_, member) => member.publicKey == clientQuitZoneNotification.publicKey
              },
              memberBalances,
              Set.empty,
              ClientKey.getInstance(context).getPublicKey,
              zone.equityHolderMemberId
            )

            if (quitPlayers.nonEmpty) {
              val previousPlayers = players
              players = players ++ quitPlayers
              if (listener != null) {
                quitPlayers.foreach { quitPlayer =>
                  val removedPlayer = previousPlayers(quitPlayer._1)
                  listener.onPlayerUpdated(quitPlayer._1 -> removedPlayer, quitPlayer)
                }
              }
            }

          case zoneTerminatedNotification: ZoneTerminatedNotification =>

            zone = null
            connectedClients = null
            accountBalances = null
            memberBalances = null
            identities = null
            players = null

            if (listener != null) {
              listener.onQuit()
            }

            serverConnection.sendCommand(
              JoinZoneCommand(
                zoneId
              ),
              new ResponseCallback {

                def onResultReceived(resultResponse: ResultResponse) {
                  log.debug("resultResponse={}", resultResponse)

                  val joinZoneResponse = resultResponse.asInstanceOf[JoinZoneResponse]

                  // TODO

                }

              })

          case zoneNameSetNotification: ZoneNameSetNotification =>

            // TODO: Do off main thread
            val contentValues = new ContentValues
            contentValues.put(LiquidityContract.Games.ZONE_ID, zoneId.id.toString)
            contentValues.put(LiquidityContract.Games.NAME, zoneNameSetNotification.name)

            context.getContentResolver.update(
              ContentUris.withAppendedId(Games.CONTENT_URI, gameId),
              contentValues,
              null,
              null
            )

            zone = zone.copy(name = zoneNameSetNotification.name)

          case memberCreatedNotification: MemberCreatedNotification =>

            zone = zone.copy(
              members = zone.members
                + (memberCreatedNotification.memberId ->
                memberCreatedNotification.member)
            )

            val maybeCreatedIdentity = MonopolyGame.identityFromMember(
              memberCreatedNotification.memberId,
              memberCreatedNotification.member,
              memberBalances,
              ClientKey.getInstance(context).getPublicKey,
              zone.equityHolderMemberId
            )

            if (maybeCreatedIdentity.isDefined) {
              val createdIdentity = maybeCreatedIdentity.get
              identities = identities + createdIdentity
              if (listener != null) {
                listener.onIdentityAdded(createdIdentity)
              }
            }

            val maybeCreatedPlayer = MonopolyGame.playerFromMember(
              memberCreatedNotification.memberId,
              memberCreatedNotification.member,
              memberBalances,
              connectedClients,
              ClientKey.getInstance(context).getPublicKey,
              zone.equityHolderMemberId
            )

            if (maybeCreatedPlayer.isDefined) {
              val createdPlayer = maybeCreatedPlayer.get
              players = players + createdPlayer
              if (listener != null) {
                listener.onPlayerAdded(createdPlayer)
              }
            }

          case memberUpdatedNotification: MemberUpdatedNotification =>

            zone = zone.copy(
              members = zone.members
                + (memberUpdatedNotification.memberId ->
                memberUpdatedNotification.member)
            )

            val maybeUpdatedIdentity = MonopolyGame.identityFromMember(
              memberUpdatedNotification.memberId,
              memberUpdatedNotification.member,
              memberBalances,
              ClientKey.getInstance(context).getPublicKey,
              zone.equityHolderMemberId
            )

            if (maybeUpdatedIdentity.isDefined) {
              val updatedIdentity = maybeUpdatedIdentity.get
              if (players.contains(updatedIdentity._1)) {
                val removedPlayer = players(updatedIdentity._1)
                players = players - updatedIdentity._1
                identities = identities + updatedIdentity
                if (listener != null) {
                  listener.onPlayerRemoved(updatedIdentity._1 -> removedPlayer)
                  listener.onIdentityAdded(updatedIdentity)
                }
              } else {
                val removedIdentity = identities(updatedIdentity._1)
                identities = identities + updatedIdentity
                if (listener != null) {
                  listener.onIdentityUpdated(updatedIdentity._1 -> removedIdentity, updatedIdentity)
                }
              }
            }

            val maybeUpdatedPlayer = MonopolyGame.playerFromMember(
              memberUpdatedNotification.memberId,
              memberUpdatedNotification.member,
              memberBalances,
              connectedClients,
              ClientKey.getInstance(context).getPublicKey,
              zone.equityHolderMemberId
            )

            if (maybeUpdatedPlayer.isDefined) {
              val updatedPlayer = maybeUpdatedPlayer.get
              if (identities.contains(updatedPlayer._1)) {
                val removedIdentity = identities(updatedPlayer._1)
                identities = identities - updatedPlayer._1
                players = players + updatedPlayer
                if (listener != null) {
                  listener.onIdentityRemoved(updatedPlayer._1 -> removedIdentity)
                  listener.onPlayerAdded(updatedPlayer)
                }
              } else {
                val removedPlayer = players(updatedPlayer._1)
                players = players + updatedPlayer
                if (listener != null) {
                  listener.onPlayerUpdated(updatedPlayer._1 -> removedPlayer, updatedPlayer)
                }
              }
            }

          case accountCreatedNotification: AccountCreatedNotification =>

            val previousAccounts = zone.accounts

            zone = zone.copy(
              accounts = zone.accounts
                + (accountCreatedNotification.accountId ->
                accountCreatedNotification.account)
            )

            if (zone.members(zone.equityHolderMemberId).publicKey ==
              ClientKey.getInstance(context).getPublicKey) {

              if (accountCreatedNotification.account.owners.size == 1) {

                val ownersOtherAccounts = previousAccounts.filter {
                  case (accountId, account) => account.owners == Set(
                    accountCreatedNotification.account.owners.head
                  )
                }

                if (ownersOtherAccounts.isEmpty) {

                  transfer(
                    zone.equityHolderMemberId,
                    accountCreatedNotification.account.owners.head,
                    initialCapital
                  )

                }

              }

            }

          case accountUpdatedNotification: AccountUpdatedNotification =>

            zone = zone.copy(
              accounts = zone.accounts
                + (accountUpdatedNotification.accountId ->
                accountUpdatedNotification.account)
            )

            val updatedMemberBalances = MonopolyGame.aggregateMembersAccountBalances(
              zone,
              accountBalances
            )

            if (updatedMemberBalances != memberBalances) {

              memberBalances = updatedMemberBalances

              val updatedIdentities = MonopolyGame.identitiesFromMembers(
                zone.members,
                memberBalances,
                ClientKey.getInstance(context).getPublicKey,
                zone.equityHolderMemberId
              )
              val updatedPlayers = MonopolyGame.playersFromMembers(
                zone.members,
                memberBalances,
                connectedClients,
                ClientKey.getInstance(context).getPublicKey,
                zone.equityHolderMemberId
              )

              if (updatedIdentities != identities) {
                identities = updatedIdentities
                if (listener != null) {
                  // TODO
                  listener.onIdentitiesChanged(identities)
                }
              }

              if (updatedPlayers != players) {
                players = updatedPlayers
                if (listener != null) {
                  // TODO
                  listener.onPlayersChanged(players)
                }
              }

            }

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
              zone,
              accountBalances
            )

            if (updatedMemberBalances != memberBalances) {

              memberBalances = updatedMemberBalances

              val updatedIdentities = MonopolyGame.identitiesFromMembers(
                zone.members,
                memberBalances,
                ClientKey.getInstance(context).getPublicKey,
                zone.equityHolderMemberId
              )

              val updatedPlayers = MonopolyGame.playersFromMembers(
                zone.members,
                memberBalances,
                connectedClients,
                ClientKey.getInstance(context).getPublicKey,
                zone.equityHolderMemberId
              )

              if (updatedIdentities != identities) {
                identities = updatedIdentities
                if (listener != null) {
                  // TODO
                  listener.onIdentitiesChanged(identities)
                }
              }

              if (updatedPlayers != players) {
                players = updatedPlayers
                if (listener != null) {
                  // TODO
                  listener.onPlayersChanged(players)
                }
              }

            }

        }

    }

  }

  def onStateChanged(connectionState: ServerConnection.ConnectionState) {
    log.debug("connectionState={}", connectionState)
    connectionState match {

      case ServerConnection.ConnectionState.CONNECTING =>

      case ServerConnection.ConnectionState.CONNECTED =>

        if (zoneId != null) {
          join()
        } else if (initialCapital != null) {
          createAndThenJoinZone()
        } else {
          sys.error("Neither zoneId nor initialCapital were set")
        }

      case ServerConnection.ConnectionState.DISCONNECTING =>

      case ServerConnection.ConnectionState.DISCONNECTED =>

      // TODO

    }
  }

  def quitAndOrDisconnect() {
    if (zone == null) {
      disconnect()
    }
    else {
      serverConnection.sendCommand(
        QuitZoneCommand(
          zoneId
        ),
        new ServerConnection.ResponseCallback {

          def onResultReceived(resultResponse: ResultResponse) {
            log.debug("resultResponse={}", resultResponse)

            // TODO
            val quitZoneResponse = resultResponse.asInstanceOf[QuitZoneResponse.type]

            if (listener != null) {
              listener.onQuit()
            }

            zone = null
            disconnect()

          }

        })
    }
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
        zoneId,
        gameName
      ),
      new ServerConnection.ResponseCallback {

        override def onErrorReceived(errorResponse: ErrorResponse) {
          log.debug("errorResponse={}", errorResponse)

          // TODO

        }

        def onResultReceived(resultResponse: ResultResponse) {
          log.debug("resultResponse={}", resultResponse)

          // TODO
          val setZoneName = resultResponse.asInstanceOf[SetZoneNameResponse.type]

        }

      })
  }

  def setGameId(gameId: Long) {
    this.gameId = gameId
  }

  def setInitialCapital(initialCapital: BigDecimal) {
    this.initialCapital = initialCapital
  }

  def setListener(listener: MonopolyGame.Listener) {
    this.listener = listener
    if (listener != null) {
      if (zone == null) {
        listener.onQuit()
      }
      else {
        listener.onJoined(zoneId)
        listener.onIdentitiesChanged(identities)
        listener.onPlayersChanged(players)
      }
    }
  }

  def setZoneId(zoneId: ZoneId) {
    this.zoneId = zoneId
  }

  def transfer(fromMember: MemberId, toMember: MemberId, amount: BigDecimal) {
    val maybeFromMemberId = identities.headOption.map {
      case (memberId, _) => memberId
    }
    val maybeFromAccountId = maybeFromMemberId.fold[Option[AccountId]](
      None
    )(fromAccount =>
      zone.accounts.collectFirst {
        case (accountId, account) if account.owners == Set(fromMember) => accountId
      })
    val maybeToAccountId = zone.accounts.collectFirst {
      case (accountId, account) if account.owners == Set(toMember) => accountId
    }
    if (maybeFromAccountId.isEmpty || maybeToAccountId.isEmpty) {
      // TODO
    } else {
      serverConnection.sendCommand(
        AddTransactionCommand(
          zoneId,
          // TODO
          "",
          maybeFromAccountId.get,
          maybeToAccountId.get,
          amount
        ),
        new ServerConnection.ResponseCallback {

          override def onErrorReceived(errorResponse: ErrorResponse) {
            log.debug("errorResponse={}", errorResponse)

            // TODO

          }

          def onResultReceived(resultResponse: ResultResponse) {
            log.debug("resultResponse={}", resultResponse)

            // TODO
            val addTransactionResponse = resultResponse.asInstanceOf[AddTransactionResponse]

          }

        })
    }
  }

}
