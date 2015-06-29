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

    def onIdentitySwapped(removedIdentity: (MemberId, IdentityWithBalance),
                          addedIdentity: (MemberId, IdentityWithBalance))

    def onJoined(zoneId: ZoneId)

    def onPlayersChanged(players: Map[MemberId, PlayerWithBalanceAndConnectionState])

    def onPlayerAdded(addedPlayer: (MemberId, PlayerWithBalanceAndConnectionState))

    def onPlayerRemoved(removedPlayer: (MemberId, PlayerWithBalanceAndConnectionState))

    def onPlayerSwapped(removedPlayer: (MemberId, PlayerWithBalanceAndConnectionState),
                        addedPlayer: (MemberId, PlayerWithBalanceAndConnectionState))

    def onQuit()

  }

  val ZoneType = GameType.MONOPOLY

  def aggregateMembersAccountBalances(memberIds: Set[MemberId],
                                      accounts: Map[AccountId, Account],
                                      accountBalances: Map[AccountId, BigDecimal]) =
    memberIds.map {
      memberId =>
        val membersAccountIds = accounts.filter {
          case (_, account) =>
            account.owners.contains(memberId)
        }.keys
        val membersAccountBalances = membersAccountIds.map (
            accountBalances.getOrElse(_, BigDecimal(0))
        )
        memberId -> membersAccountBalances.sum
    }.toMap

  def getUserName(context: Context, aliasConstant: String) = {
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
                         selectedIdentityId: Option[MemberId]) =
    members.filterNot {
      case (memberId, member) =>
        selectedIdentityId.contains(memberId)
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
                       selectedIdentityId: Option[MemberId]) =
    playersFromMembers(
      Map(memberId -> member),
      balances,
      connectedPublicKeys,
      selectedIdentityId
    ).headOption

}

class MonopolyGame(context: Context)
  extends ConnectionStateListener with NotificationListener {

  // TODO: Review all other project Scala classes for non-private fields

  private val log = LoggerFactory.getLogger(getClass)

  private val serverConnection = new ServerConnection(context, this, this)

  private var gameId = Option.empty[Long]
  private var zoneId = Option.empty[ZoneId]
  // TODO: Need to persist this
  private var initialCapital = Option.empty[BigDecimal]
  private var selectedIdentityId = Option.empty[MemberId]

  // TODO
  private var zone: Zone = _
  private var connectedClients: Set[PublicKey] = _
  private var accountBalances: Map[AccountId, BigDecimal] = _
  private var memberBalances: Map[MemberId, BigDecimal] = _
  private var identities: Map[MemberId, IdentityWithBalance] = _
  // TODO: Maintain a version with the selectedIdentityId and then just filter it for listeners?
  private var players: Map[MemberId, PlayerWithBalanceAndConnectionState] = _

  private var listener = Option.empty[Listener]

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
        context.getString(R.string.game_name_format_string, playerName),
        MonopolyGame.ZoneType.typeName,
        Member(
          context.getString(R.string.bank_member_name),
          ClientKey.getInstance(context).getPublicKey
        ),
        Account(
          context.getString(R.string.bank_account_name),
          Set.empty
        )
      ),
      new ResponseCallback {

        def onResultReceived(resultResponse: ResultResponse) {
          log.debug("resultResponse={}", resultResponse)

          val createZoneResponse = resultResponse.asInstanceOf[CreateZoneResponse]

          zoneId = Some(createZoneResponse.zoneId)
          join(createZoneResponse.zoneId)

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
                // TODO
                "",
                Set(createMemberResponse.memberId)
              )
            ),
            new ResponseCallback() {

              def onResultReceived(resultResponse: ResultResponse) {
                log.debug("resultResponse={}", resultResponse)

              }

            })
        }

      })
  }

  private def disconnect() {
    serverConnection.disconnect()
  }

  private def join(zoneId: ZoneId) {
    serverConnection.sendCommand(
      JoinZoneCommand(
        zoneId
      ),
      new ResponseCallback {

        def onResultReceived(resultResponse: ResultResponse) {
          log.debug("resultResponse={}", resultResponse)

          val joinZoneResponse = resultResponse.asInstanceOf[JoinZoneResponse]

          listener.foreach(_.onJoined(zoneId))

          zone = joinZoneResponse.zone
          connectedClients = joinZoneResponse.connectedClients
          accountBalances = Map.empty

          // TODO: Do off main thread
          val contentValues = new ContentValues
          contentValues.put(LiquidityContract.Games.GAME_TYPE, GameType.MONOPOLY.typeName)
          contentValues.put(LiquidityContract.Games.ZONE_ID, zoneId.id.toString)
          contentValues.put(LiquidityContract.Games.NAME, zone.name)
          contentValues.put(LiquidityContract.Games.CREATED, zone.created: java.lang.Long)

          gameId = Some(gameId.fold(
            ContentUris.parseId(
              context.getContentResolver.insert(
                LiquidityContract.Games.CONTENT_URI,
                contentValues
              )
            )
          ) { gameId =>
            context.getContentResolver.update(
              ContentUris.withAppendedId(Games.CONTENT_URI, gameId),
              contentValues,
              null,
              null
            )
            gameId
          })

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
            connectedClients,
            selectedIdentityId
          )

          listener.foreach { listener =>
            listener.onIdentitiesChanged(identities)
            listener.onPlayersChanged(players)
          }

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

        val gameId = this.gameId.get
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
              Set(clientJoinedZoneNotification.publicKey),
              selectedIdentityId
            )

            val previousPlayers = players
            players = players ++ joinedPlayers
            listener.foreach { listener =>
              joinedPlayers.foreach { joinedPlayer =>
                listener.onPlayerSwapped(
                  joinedPlayer._1 -> previousPlayers(joinedPlayer._1),
                  joinedPlayer
                )
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
              selectedIdentityId
            )

            val previousPlayers = players
            players = players ++ quitPlayers
            listener.foreach { listener =>
              quitPlayers.foreach { quitPlayer =>
                listener.onPlayerSwapped(
                  quitPlayer._1 -> previousPlayers(quitPlayer._1),
                  quitPlayer
                )
              }
            }

          case zoneTerminatedNotification: ZoneTerminatedNotification =>

            zone = null
            connectedClients = null
            accountBalances = null
            memberBalances = null
            identities = null
            players = null

            listener.foreach(_.onQuit())

            serverConnection.sendCommand(
              JoinZoneCommand(
                zoneId
              ),
              new ResponseCallback {

                def onResultReceived(resultResponse: ResultResponse) {
                  log.debug("resultResponse={}", resultResponse)

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

            val createdIdentity = MonopolyGame.identityFromMember(
              memberCreatedNotification.memberId,
              memberCreatedNotification.member,
              memberBalances,
              ClientKey.getInstance(context).getPublicKey,
              zone.equityHolderMemberId
            )

            createdIdentity.foreach { createdIdentity =>
              identities = identities + createdIdentity
              listener.foreach(_.onIdentityAdded(createdIdentity))
            }

            val createdPlayer = MonopolyGame.playerFromMember(
              memberCreatedNotification.memberId,
              memberCreatedNotification.member,
              memberBalances,
              connectedClients,
              selectedIdentityId
            )

            createdPlayer.foreach { createdPlayer =>
              players = players + createdPlayer
              listener.foreach(_.onPlayerAdded(createdPlayer))
            }

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
                val removedPlayer = players(updatedIdentity._1)
                players = players - updatedIdentity._1
                identities = identities + updatedIdentity
                listener.foreach { listener =>
                  listener.onPlayerRemoved(updatedIdentity._1 -> removedPlayer)
                  listener.onIdentityAdded(updatedIdentity)
                }
              } else {
                val removedIdentity = identities(updatedIdentity._1)
                identities = identities + updatedIdentity
                listener.foreach(_.onIdentitySwapped(
                  updatedIdentity._1 -> removedIdentity,
                  updatedIdentity
                ))
              }
            }

            val updatedPlayer = MonopolyGame.playerFromMember(
              memberUpdatedNotification.memberId,
              memberUpdatedNotification.member,
              memberBalances,
              connectedClients,
              selectedIdentityId
            )

            updatedPlayer.foreach { updatedPlayer =>
              if (identities.contains(updatedPlayer._1)) {
                val removedIdentity = identities(updatedPlayer._1)
                identities = identities - updatedPlayer._1
                players = players + updatedPlayer
                listener.foreach { listener =>
                  listener.onIdentityRemoved(updatedPlayer._1 -> removedIdentity)
                  listener.onPlayerAdded(updatedPlayer)
                }
              } else {
                val removedPlayer = players(updatedPlayer._1)
                players = players + updatedPlayer
                listener.foreach(_.onPlayerSwapped(
                  updatedPlayer._1 -> removedPlayer,
                  updatedPlayer
                ))
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
                    // TODO
                    initialCapital.get
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
              zone.members.keySet,
              zone.accounts,
              accountBalances
            ).filterNot {
              case (memberId, balance) => memberBalances.get(memberId).contains(balance)
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
              connectedClients,
              selectedIdentityId
            )

            val previousIdentities = identities
            identities = identities ++ updatedIdentities
            listener.foreach { listener =>
              updatedIdentities.foreach { updatedIdentity =>
                listener.onIdentitySwapped(
                  updatedIdentity._1 -> previousIdentities(updatedIdentity._1),
                  updatedIdentity
                )
              }
            }

            val previousPlayers = players
            players = players ++ updatedPlayers
            listener.foreach { listener =>
              updatedPlayers.foreach { updatedPlayer =>
                listener.onPlayerSwapped(
                  updatedPlayer._1 -> previousPlayers(updatedPlayer._1),
                  updatedPlayer
                )
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
              zone.members.keySet,
              zone.accounts,
              accountBalances
            ).filterNot {
              case (memberId, balance) => memberBalances.get(memberId).contains(balance)
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
              connectedClients,
              selectedIdentityId
            )

            val previousIdentities = identities
            identities = identities ++ updatedIdentities
            listener.foreach { listener =>
              updatedIdentities.foreach { updatedIdentity =>
                listener.onIdentitySwapped(
                  updatedIdentity._1 -> previousIdentities(updatedIdentity._1),
                  updatedIdentity
                )
              }
            }

            val previousPlayers = players
            players = players ++ updatedPlayers
            listener.foreach { listener =>
              updatedPlayers.foreach { updatedPlayer =>
                listener.onPlayerSwapped(
                  updatedPlayer._1 -> previousPlayers(updatedPlayer._1),
                  updatedPlayer
                )
              }
            }

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

  def quitAndOrDisconnect() {
    if (zone == null) {
      disconnect()
    } else {
      serverConnection.sendCommand(
        QuitZoneCommand(
          zoneId.get
        ),
        new ServerConnection.ResponseCallback {

          def onResultReceived(resultResponse: ResultResponse) {
            log.debug("resultResponse={}", resultResponse)

            listener.foreach(_.onQuit())

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
        zoneId.get,
        gameName
      ),
      new ServerConnection.ResponseCallback {

        def onResultReceived(resultResponse: ResultResponse) {
          log.debug("resultResponse={}", resultResponse)

          // TODO

        }

      })
  }

  def setGameId(gameId: Long) {
    this.gameId = Option(gameId)
  }

  def setInitialCapital(initialCapital: BigDecimal) {
    this.initialCapital = Option(initialCapital)
  }

  def setListener(listener: MonopolyGame.Listener) {
    this.listener = Option(listener)
    this.listener.foreach { listener =>
      if (zone == null) {
        listener.onQuit()
      } else {
        listener.onJoined(zoneId.get)
        listener.onIdentitiesChanged(identities)
        listener.onPlayersChanged(players)
      }
    }
  }

  def setSelectedIdentity(selectedIdentityId: MemberId) {
    this.selectedIdentityId = Option(selectedIdentityId)
    val updatedPlayers = MonopolyGame.playersFromMembers(
      zone.members,
      memberBalances,
      connectedClients,
      this.selectedIdentityId
    )
    val addedPlayer = updatedPlayers -- players.keys
    val removedPlayer = players -- updatedPlayers.keys
    players = updatedPlayers
    if (addedPlayer.nonEmpty && removedPlayer.nonEmpty) {
      listener.foreach(_.onPlayerSwapped(removedPlayer.head, addedPlayer.head))
    } else if (addedPlayer.nonEmpty) {
      listener.foreach(_.onPlayerAdded(addedPlayer.head))
    } else if (removedPlayer.nonEmpty) {
      listener.foreach(_.onPlayerRemoved(removedPlayer.head))
    }
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
        new ServerConnection.ResponseCallback {

          override def onErrorReceived(errorResponse: ErrorResponse) {
            log.debug("errorResponse={}", errorResponse)

            // TODO

          }

          def onResultReceived(resultResponse: ResultResponse) {
            log.debug("resultResponse={}", resultResponse)

            // TODO

          }

        })
    }
  }

}
