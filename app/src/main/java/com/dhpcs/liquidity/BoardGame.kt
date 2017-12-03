package com.dhpcs.liquidity

import android.os.AsyncTask
import com.dhpcs.liquidity.client.ServerConnection
import com.dhpcs.liquidity.model.*
import com.dhpcs.liquidity.ws.protocol.*
import com.google.protobuf.struct.Struct
import com.google.protobuf.struct.Value
import scala.Option
import scala.Some
import scala.Tuple2
import scala.collection.JavaConverters
import scala.concurrent.Future
import scala.concurrent.`ExecutionContext$`
import scala.concurrent.`Future$`
import scala.runtime.AbstractFunction0
import scala.runtime.AbstractFunction1
import scala.util.Either
import scala.util.Left
import scala.util.Right
import java.io.Serializable
import java.math.BigDecimal
import java.util.Currency
import java.util.concurrent.Executor
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.component1
import kotlin.collections.component2

class BoardGame private constructor(private val serverConnection: ServerConnection,
                                    mainThreadExecutor: Executor,
                                    private val gameDatabase: GameDatabase,
                                    private val _currency: Option<Currency>,
                                    private val _gameName: Option<String>,
                                    private val bankMemberName: Option<String>,
                                    private var _zoneId: Option<ZoneId>,
                                    private var gameId: Option<Future<Long>>
) : ServerConnection.ConnectionStateListener,
        ServerConnection.NotificationReceiptListener {

    companion object {

        interface GameDatabase {

            fun insertGame(zoneId: ZoneId, created: Long, expires: Long, name: String?): Long

            fun checkAndUpdateGame(zoneId: ZoneId, name: String?): Long?

            fun updateGameName(gameId: Long, name: String?)

        }

        enum class JoinState {
            UNAVAILABLE,
            GENERAL_FAILURE,
            TLS_ERROR,
            AVAILABLE,
            CONNECTING,
            AUTHENTICATING,
            CREATING,
            JOINING,
            JOINED,
            QUITTING,
            DISCONNECTING
        }

        interface Player : Serializable {
            val zoneId: ZoneId
            val member: Member
            val account: Account
            val isBanker: Boolean
        }

        interface Identity : Player

        data class PlayerWithBalanceAndConnectionState(
                override val zoneId: ZoneId,
                override val member: Member,
                override val account: Account,
                val balance: BigDecimal,
                val currency: Option<Either<String, Currency>>,
                override val isBanker: Boolean,
                val isConnected: Boolean
        ) : Player

        data class IdentityWithBalance(
                override val zoneId: ZoneId,
                override val member: Member,
                override val account: Account,
                val balance: BigDecimal,
                val currency: Option<Either<String, Currency>>,
                override val isBanker: Boolean
        ) : Identity

        interface Transfer : Serializable {
            val from: Either<Account, Player>
            val to: Either<Account, Player>
            val creator: Either<Member, Player>
            val transaction: Transaction
        }

        data class TransferWithCurrency(
                override val from: Either<Account, Player>,
                override val to: Either<Account, Player>,
                override val creator: Either<Member, Player>,
                override val transaction: Transaction,
                val currency: Option<Either<String, Currency>>
        ) : Transfer

        interface JoinStateListener {

            fun onJoinStateChanged(joinState: JoinState)

        }

        interface GameActionListener {

            fun onChangeGameNameError(name: Option<String>)

            fun onChangeIdentityNameError(name: Option<String>)

            fun onCreateIdentityAccountError(name: Option<String>)

            fun onCreateIdentityMemberError(name: Option<String>)

            fun onCreateGameError(name: Option<String>)

            fun onDeleteIdentityError(name: Option<String>)

            fun onGameNameChanged(name: Option<String>)

            fun onIdentitiesUpdated(identities: Map<MemberId, IdentityWithBalance>)

            fun onIdentityCreated(identity: IdentityWithBalance)

            fun onIdentityReceived(identity: IdentityWithBalance)

            fun onIdentityRequired()

            fun onIdentityRestored(identity: IdentityWithBalance)

            fun onJoinGameError()

            fun onPlayerAdded(addedPlayer: PlayerWithBalanceAndConnectionState)

            fun onPlayerChanged(changedPlayer: PlayerWithBalanceAndConnectionState)

            fun onPlayersInitialized(players: Collection<PlayerWithBalanceAndConnectionState>)

            fun onPlayerRemoved(removedPlayer: PlayerWithBalanceAndConnectionState)

            fun onPlayersUpdated(players: Map<MemberId, PlayerWithBalanceAndConnectionState>)

            fun onQuitGameError()

            fun onRestoreIdentityError(name: Option<String>)

            fun onTransferAdded(addedTransfer: TransferWithCurrency)

            fun onTransferIdentityError(name: Option<String>)

            fun onTransferToPlayerError(name: Option<String>)

            fun onTransfersChanged(changedTransfers: Collection<TransferWithCurrency>)

            fun onTransfersInitialized(transfers: Collection<TransferWithCurrency>)

            fun onTransfersUpdated(transfers: Map<TransactionId, TransferWithCurrency>)

        }

        class JoinRequestToken

        private class State(
                var zone: Zone,
                var connectedClients: Map<String, PublicKey>,
                var balances: Map<AccountId, BigDecimal>,
                var currency: Option<Either<String, Currency>>,
                var memberIdsToAccountIds: Map<MemberId, AccountId>,
                var accountIdsToMemberIds: Map<AccountId, MemberId>,
                var identities: Map<MemberId, IdentityWithBalance>,
                var hiddenIdentities: Map<MemberId, IdentityWithBalance>,
                var players: Map<MemberId, PlayerWithBalanceAndConnectionState>,
                var hiddenPlayers: Map<MemberId, PlayerWithBalanceAndConnectionState>,
                var transfers: Map<TransactionId, TransferWithCurrency>
        )

        private const val CURRENCY_CODE_KEY = "currency"
        private const val HIDDEN_FLAG_KEY = "hidden"

        private var instances: Map<ZoneId, BoardGame> = HashMap()

        fun getInstance(zoneId: ZoneId): BoardGame? = instances[zoneId]

        fun isGameNameValid(name: CharSequence): Boolean = isTagValid(name)

        fun isTagValid(tag: CharSequence): Boolean {
            return tag.isNotEmpty() && tag.length <= ZoneCommand.MaximumTagLength()
        }

        private fun currencyFromMetadata(metadata: Option<Struct>
        ): Option<Either<String, Currency>> {
            return if (metadata.isEmpty) {
                Option.empty()
            } else {
                val currencyCode = metadata.get().fields()[(CURRENCY_CODE_KEY)]
                if (currencyCode.isEmpty) {
                    Option.empty()
                } else {
                    Some<Either<String, Currency>>(
                            Right(Currency.getInstance(currencyCode.get().stringValue))
                    )
                }
            }
        }

        private fun membersAccountsFromAccounts(accounts: Map<AccountId, Account>
        ): Map<MemberId, AccountId> {
            return accounts.filter { (_, account) ->
                account.ownerMemberIds().size() == 1
            }.entries.groupBy { (_, account) ->
                account.ownerMemberIds().head()
            }.filterValues {
                it.size == 1
            }.mapValues { memberAccounts ->
                memberAccounts.value.first().key
            }
        }

        private fun identitiesFromMembersAccounts(
                zoneId: ZoneId,
                membersAccounts: Map<MemberId, AccountId>,
                accounts: Map<AccountId, Account>,
                balances: Map<AccountId, BigDecimal>,
                currency: Option<Either<String, Currency>>,
                members: Map<MemberId, Member>,
                equityAccountId: AccountId,
                clientKey: PublicKey
        ): Pair<Map<MemberId, IdentityWithBalance>, Map<MemberId, IdentityWithBalance>> {
            val identitiesFromMembersAccounts = membersAccounts.filterKeys {
                val memberOwnerPublicKeys = members[it]!!.ownerPublicKeys()
                memberOwnerPublicKeys.size() == 1 && memberOwnerPublicKeys.head() == clientKey
            }.mapValues { (memberId, accountId) ->
                IdentityWithBalance(
                        zoneId,
                        members[memberId]!!,
                        accounts[accountId]!!,
                        balances.getOrDefault(accountId, BigDecimal(0)),
                        currency,
                        accountId == equityAccountId
                )
            }
            val notHidden = identitiesFromMembersAccounts.filterValues { !isHidden(it.member) }
            val hidden = identitiesFromMembersAccounts.filterValues { isHidden(it.member) }
            return Pair(notHidden, hidden)
        }

        private fun isHidden(member: Member): Boolean {
            return if (member.metadata().isEmpty) {
                false
            } else {
                val hidden = member.metadata().get().fields()[HIDDEN_FLAG_KEY]
                if (hidden.isEmpty) false else hidden.get().boolValue
            }
        }

        private fun playersFromMembersAccounts(zoneId: ZoneId,
                                               membersAccounts: Map<MemberId, AccountId>,
                                               accounts: Map<AccountId, Account>,
                                               balances: Map<AccountId, BigDecimal>,
                                               currency: Option<Either<String, Currency>>,
                                               members: Map<MemberId, Member>,
                                               equityAccountId: AccountId,
                                               connectedClients: Collection<PublicKey>
        ): Pair<Map<MemberId, PlayerWithBalanceAndConnectionState>,
                Map<MemberId, PlayerWithBalanceAndConnectionState>> {
            val playersFromMembersAccounts = membersAccounts.mapValues { (memberId, accountId) ->
                val member = members[memberId]!!
                PlayerWithBalanceAndConnectionState(
                        zoneId,
                        member,
                        accounts[accountId]!!,
                        balances.getOrDefault(accountId, BigDecimal(0)),
                        currency,
                        accountId == equityAccountId,
                        connectedClients.any { member.ownerPublicKeys().contains(it) }
                )
            }
            val notHidden = playersFromMembersAccounts.filterValues { !isHidden(it.member) }
            val hidden = playersFromMembersAccounts.filterValues { isHidden(it.member) }
            return Pair(notHidden, hidden)
        }

        private fun transfersFromTransactions(transactions: Map<TransactionId, Transaction>,
                                              currency: Option<Either<String, Currency>>,
                                              accountsMembers: Map<AccountId, MemberId>,
                                              players: Map<MemberId, Player>,
                                              accounts: Map<AccountId, Account>,
                                              members: Map<MemberId, Member>
        ): Map<TransactionId, TransferWithCurrency> {
            return transactions.mapValues { (_, transaction) ->
                val fromMemberId = accountsMembers[transaction.from()]
                val toMemberId = accountsMembers[transaction.to()]
                val creatorPlayer = players[transaction.creator()]
                TransferWithCurrency(
                        if (fromMemberId == null) {
                            Left(accounts[transaction.from()]!!)
                        } else {
                            Right(players[fromMemberId]!!)
                        },
                        if (toMemberId == null) {
                            Left(accounts[transaction.to()]!!)
                        } else {
                            Right(players[toMemberId]!!)
                        },
                        if (creatorPlayer == null) {
                            Left(members[transaction.creator()]!!)
                        } else {
                            Right(creatorPlayer)
                        },
                        transaction,
                        currency
                )
            }
        }

        private fun <A, B> scala.collection.Map<A, B>.asKotlin(): Map<A, B> {
            return JavaConverters.mapAsJavaMapConverter(this).asJava()
        }

        private fun <A> asKotlin(seq: scala.collection.Seq<A>): List<A> {
            return JavaConverters.seqAsJavaListConverter(seq).asJava()
        }

    }

    private val mainThreadEc = `ExecutionContext$`.`MODULE$`.fromExecutor(mainThreadExecutor)

    private val asyncTaskEc = `ExecutionContext$`.`MODULE$`.fromExecutor(
            AsyncTask.THREAD_POOL_EXECUTOR
    )

    private val connectionRequestToken = ServerConnection.ConnectionRequestToken()

    private var state: State? = null
    private var _joinState: JoinState = JoinState.UNAVAILABLE

    private var joinRequestTokens: Set<JoinRequestToken> = HashSet()
    private var joinStateListeners: Set<JoinStateListener> = HashSet()
    private var gameActionListeners: Set<GameActionListener> = HashSet()

    constructor (serverConnection: ServerConnection,
                 mainThreadExecutor: Executor,
                 gameDatabase: GameDatabase,
                 currency: Currency,
                 gameName: String,
                 bankMemberName: String
    ) : this(
            serverConnection,
            mainThreadExecutor,
            gameDatabase,
            Some(currency),
            Some(gameName),
            Some(bankMemberName),
            Option.empty(),
            Option.empty()
    )


    constructor(serverConnection: ServerConnection,
                mainThreadExecutor: Executor,
                gameDatabase: GameDatabase,
                zoneId: ZoneId
    ) : this(
            serverConnection,
            mainThreadExecutor,
            gameDatabase,
            Option.empty(),
            Option.empty(),
            Option.empty(),
            Some(zoneId),
            Option.empty()
    )

    constructor(serverConnection: ServerConnection,
                mainThreadExecutor: Executor,
                gameDatabase: GameDatabase,
                zoneId: ZoneId,
                gameId: Long
    ) : this(
            serverConnection,
            mainThreadExecutor,
            gameDatabase,
            Option.empty(),
            Option.empty(),
            Option.empty(),
            Some(zoneId),
            Some(`Future$`.`MODULE$`.successful(gameId))
    )

    val currency get() = state!!.currency

    val gameName get() = state!!.zone.name()!!

    val hiddenIdentities get() = state!!.hiddenIdentities.values

    val identities get() = state!!.identities.values

    val joinState get() = _joinState

    val players get() = state!!.players.values

    val zoneId get() = if (_zoneId.isEmpty) null else _zoneId.get()

    fun registerListener(listener: JoinStateListener) {
        if (!joinStateListeners.contains(listener)) {
            if (joinStateListeners.isEmpty() &&
                    gameActionListeners.isEmpty() &&
                    joinRequestTokens.isEmpty()) {
                serverConnection.registerListener(
                        this as ServerConnection.ConnectionStateListener
                )
                serverConnection.registerListener(
                        this as ServerConnection.NotificationReceiptListener
                )
            }
            joinStateListeners += listener
            listener.onJoinStateChanged(_joinState)
        }
    }

    fun registerListener(listener: GameActionListener) {
        if (!gameActionListeners.contains(listener)) {
            if (joinStateListeners.isEmpty() &&
                    gameActionListeners.isEmpty() &&
                    joinRequestTokens.isEmpty()) {
                serverConnection.registerListener(
                        this as ServerConnection.ConnectionStateListener
                )
                serverConnection.registerListener(
                        this as ServerConnection.NotificationReceiptListener
                )
            }
            gameActionListeners += listener
            if (_joinState == JoinState.JOINED) {
                listener.onGameNameChanged(state!!.zone.name())
                listener.onIdentitiesUpdated(state!!.identities)
                listener.onPlayersInitialized(state!!.players.values)
                listener.onPlayersUpdated(state!!.players)
                listener.onTransfersInitialized(state!!.transfers.values)
                listener.onTransfersUpdated(state!!.transfers)
            }
        }
    }

    fun requestJoin(token: JoinRequestToken, retry: Boolean) {
        if (_zoneId.isDefined && !instances.contains(_zoneId.get())) {
            instances += Pair(_zoneId.get(), this@BoardGame)
        }
        if (!joinRequestTokens.contains(token)) {
            if (joinStateListeners.isEmpty() &&
                    gameActionListeners.isEmpty() &&
                    joinRequestTokens.isEmpty()) {
                serverConnection.registerListener(
                        this as ServerConnection.ConnectionStateListener
                )
                serverConnection.registerListener(
                        this as ServerConnection.NotificationReceiptListener
                )
            }
            joinRequestTokens += token
        }
        serverConnection.requestConnection(connectionRequestToken, retry)
        if (_joinState != JoinState.CREATING
                && _joinState != JoinState.JOINING
                && _joinState != JoinState.JOINED
                && serverConnection.connectionState() is ServerConnection.`ONLINE$`)
            if (_zoneId.isEmpty) {
                state = null
                _joinState = JoinState.CREATING
                joinStateListeners.forEach {
                    it.onJoinStateChanged(_joinState)
                }
                createAndThenJoinZone(_currency.get(), _gameName.get())
            } else {
                state = null
                _joinState = JoinState.JOINING
                joinStateListeners.forEach {
                    it.onJoinStateChanged(_joinState)
                }
                join(_zoneId.get())
            }
    }

    fun changeGameName(name: String) {
        serverConnection.sendZoneCommand(
                _zoneId.get(),
                ChangeZoneNameCommand(
                        Some(name)
                )
        ).foreach(object : AbstractFunction1<ZoneResponse, Unit>() {
            override fun apply(zoneResponse: ZoneResponse) {
                val result = (zoneResponse as ChangeZoneNameResponse).result().toOption()
                if (result.isEmpty) {
                    gameActionListeners.forEach {
                        it.onChangeGameNameError(Some(name))
                    }
                }
            }
        }, mainThreadEc)
    }

    fun isIdentityNameValid(name: CharSequence): Boolean {
        return isTagValid(name) &&
                !state!!.zone.members()[
                        state!!.accountIdsToMemberIds[state!!.zone.equityAccountId()]
                        ].get().name().contains(name.toString())
    }

    fun createIdentity(name: String) {
        serverConnection.sendZoneCommand(
                _zoneId.get(),
                CreateMemberCommand(
                        scala.collection.immutable.Set.Set1(serverConnection.clientKey()),
                        Some(name),
                        Option.empty()
                )
        ).foreach(object : AbstractFunction1<ZoneResponse, Unit>() {
            override fun apply(zoneResponse: ZoneResponse) {
                val result = (zoneResponse as CreateMemberResponse).result().toOption()
                if (result.isEmpty) {
                    gameActionListeners.forEach {
                        it.onCreateIdentityMemberError(Some(name))
                    }
                } else {
                    createAccount(result.get())
                }
            }
        }, mainThreadEc)
    }

    fun changeIdentityName(identity: Identity, name: String) {
        serverConnection.sendZoneCommand(
                _zoneId.get(),
                UpdateMemberCommand(
                        identity.member.copy(
                                identity.member.id(),
                                identity.member.ownerPublicKeys(),
                                Some(name),
                                identity.member.metadata()
                        )
                )
        ).foreach(object : AbstractFunction1<ZoneResponse, Unit>() {
            override fun apply(zoneResponse: ZoneResponse) {
                val result = (zoneResponse as UpdateMemberResponse).result().toOption()
                if (result.isEmpty) {
                    gameActionListeners.forEach {
                        it.onChangeIdentityNameError(Some(name))
                    }
                }
            }
        }, mainThreadEc)
    }

    fun isPublicKeyConnectedAndImplicitlyValid(publicKey: PublicKey): Boolean {
        return state!!.connectedClients.values.contains(publicKey)
    }

    fun transferIdentity(identity: Identity, toPublicKey: PublicKey) {
        serverConnection.sendZoneCommand(
                _zoneId.get(),
                UpdateMemberCommand(
                        identity.member.copy(
                                identity.member.id(),
                                identity.member.ownerPublicKeys()
                                        .`$minus`(serverConnection.clientKey())
                                        .`$plus`(toPublicKey) as
                                        scala.collection.immutable.Set<PublicKey>,
                                identity.member.name(),
                                identity.member.metadata()
                        )
                )
        ).foreach(object : AbstractFunction1<ZoneResponse, Unit>() {
            override fun apply(zoneResponse: ZoneResponse) {
                val result = (zoneResponse as UpdateMemberResponse).result().toOption()
                if (result.isEmpty) {
                    gameActionListeners.forEach {
                        it.onTransferIdentityError(identity.member.name())
                    }
                }
            }
        }, mainThreadEc)
    }

    fun deleteIdentity(identity: Identity) {
        val `true` = Value.defaultInstance().withBoolValue(true)
        val metadata = Some(Struct(
                (if (identity.member.metadata().isEmpty) {
                    Struct.defaultInstance()
                } else {
                    identity.member.metadata().get()
                }).fields().`$plus`(Tuple2(HIDDEN_FLAG_KEY, `true`))
        ))
        serverConnection.sendZoneCommand(
                _zoneId.get(),
                UpdateMemberCommand(
                        identity.member.copy(
                                identity.member.id(),
                                identity.member.ownerPublicKeys(),
                                identity.member.name(),
                                metadata
                        )
                )
        ).foreach(object : AbstractFunction1<ZoneResponse, Unit>() {
            override fun apply(zoneResponse: ZoneResponse) {
                val result = (zoneResponse as UpdateMemberResponse).result().toOption()
                if (result.isEmpty) {
                    gameActionListeners.forEach {
                        it.onDeleteIdentityError(identity.member.name())
                    }
                }
            }
        }, mainThreadEc)
    }

    fun restoreIdentity(identity: Identity) {
        val metadata = if (identity.member.metadata().isEmpty) {
            Option.empty<Struct>()
        } else {
            Some(Struct(
                    identity.member.metadata().get().fields().`$minus`(HIDDEN_FLAG_KEY)
            ))
        }
        serverConnection.sendZoneCommand(
                _zoneId.get(),
                UpdateMemberCommand(
                        identity.member.copy(
                                identity.member.id(),
                                identity.member.ownerPublicKeys(),
                                identity.member.name(),
                                metadata
                        )
                )
        ).foreach(object : AbstractFunction1<ZoneResponse, Unit>() {
            override fun apply(zoneResponse: ZoneResponse) {
                val result = (zoneResponse as UpdateMemberResponse).result().toOption()
                if (result.isEmpty) {
                    gameActionListeners.forEach {
                        it.onRestoreIdentityError(identity.member.name())
                    }
                }
            }
        }, mainThreadEc)
    }

    fun transferToPlayer(actingAs: Identity,
                         from: Identity,
                         to: Player,
                         value: BigDecimal
    ) {
        serverConnection.sendZoneCommand(
                _zoneId.get(),
                AddTransactionCommand(
                        actingAs.member.id(),
                        from.account.id(),
                        to.account.id(),
                        scala.math.BigDecimal(value),
                        Option.empty(),
                        Option.empty()
                )
        ).foreach(object : AbstractFunction1<ZoneResponse, Unit>() {
            override fun apply(zoneResponse: ZoneResponse) {
                val result = (zoneResponse as AddTransactionResponse).result().toOption()
                if (result.isEmpty) {
                    gameActionListeners.forEach {
                        it.onTransferToPlayerError(to.member.name())
                    }
                }
            }
        }, mainThreadEc)
    }

    fun unrequestJoin(token: JoinRequestToken) {
        if (joinRequestTokens.contains(token)) {
            joinRequestTokens -= token
            if (joinStateListeners.isEmpty() &&
                    gameActionListeners.isEmpty() &&
                    joinRequestTokens.isEmpty()) {
                serverConnection.unregisterListener(
                        this as ServerConnection.NotificationReceiptListener
                )
                serverConnection.unregisterListener(
                        this as ServerConnection.ConnectionStateListener
                )
            }
            if (joinRequestTokens.isEmpty()) {
                if (_zoneId.isDefined && instances.contains(_zoneId.get())) {
                    instances -= _zoneId.get()
                }
                if (_joinState != JoinState.JOINING && _joinState != JoinState.JOINED) {
                    serverConnection.unrequestConnection(connectionRequestToken)
                } else {
                    state = null
                    _joinState = JoinState.QUITTING
                    joinStateListeners.forEach {
                        it.onJoinStateChanged(_joinState)
                    }
                    serverConnection.sendZoneCommand(
                            _zoneId.get(),
                            `QuitZoneCommand$`.`MODULE$`
                    ).foreach(object : AbstractFunction1<ZoneResponse, Unit>() {
                        override fun apply(zoneResponse: ZoneResponse) {
                            val result = (zoneResponse as QuitZoneResponse).result().toOption()
                            if (result.isEmpty) {
                                gameActionListeners.forEach {
                                    it.onQuitGameError()
                                }
                            } else {
                                if (joinRequestTokens.isNotEmpty()) {
                                    state = null
                                    _joinState = JoinState.JOINING
                                    joinStateListeners.forEach {
                                        it.onJoinStateChanged(_joinState)
                                    }
                                    join(_zoneId.get())
                                } else {
                                    serverConnection.unrequestConnection(connectionRequestToken)
                                }
                            }
                        }
                    }, mainThreadEc)
                }
            }
        }
    }

    fun unregisterListener(listener: GameActionListener) {
        if (gameActionListeners.contains(listener)) {
            gameActionListeners -= listener
            if (joinStateListeners.isEmpty() &&
                    gameActionListeners.isEmpty() &&
                    joinRequestTokens.isEmpty()) {
                serverConnection.unregisterListener(
                        this as ServerConnection.NotificationReceiptListener
                )
                serverConnection.unregisterListener(
                        this as ServerConnection.ConnectionStateListener
                )
            }
        }
    }

    fun unregisterListener(listener: JoinStateListener) {
        if (joinStateListeners.contains(listener)) {
            joinStateListeners -= listener
            if (joinStateListeners.isEmpty() &&
                    gameActionListeners.isEmpty() &&
                    joinRequestTokens.isEmpty()) {
                serverConnection.unregisterListener(
                        this as ServerConnection.NotificationReceiptListener
                )
                serverConnection.unregisterListener(
                        this as ServerConnection.ConnectionStateListener
                )
            }
        }
    }

    override fun onConnectionStateChanged(connectionState: ServerConnection.ConnectionState
    ) = when (connectionState) {
        is ServerConnection.`UNAVAILABLE$` -> {
            state = null
            _joinState = JoinState.UNAVAILABLE
            joinStateListeners.forEach {
                it.onJoinStateChanged(_joinState)
            }
        }
        is ServerConnection.`GENERAL_FAILURE$` -> {
            state = null
            _joinState = JoinState.GENERAL_FAILURE
            joinStateListeners.forEach {
                it.onJoinStateChanged(_joinState)
            }
        }
        is ServerConnection.`TLS_ERROR$` -> {
            state = null
            _joinState = JoinState.TLS_ERROR
            joinStateListeners.forEach {
                it.onJoinStateChanged(_joinState)
            }
        }
        is ServerConnection.`AVAILABLE$` -> {
            state = null
            _joinState = JoinState.AVAILABLE
            joinStateListeners.forEach {
                it.onJoinStateChanged(_joinState)
            }
        }
        is ServerConnection.`AUTHENTICATING$` -> {
            state = null
            _joinState = JoinState.AUTHENTICATING
            joinStateListeners.forEach {
                it.onJoinStateChanged(_joinState)
            }
        }
        is ServerConnection.`CONNECTING$` -> {
            state = null
            _joinState = JoinState.CONNECTING
            joinStateListeners.forEach {
                it.onJoinStateChanged(_joinState)
            }
        }
        is ServerConnection.`ONLINE$` -> {
            if (joinRequestTokens.isNotEmpty()) {
                if (_zoneId.isEmpty) {
                    state = null
                    _joinState = JoinState.CREATING
                    joinStateListeners.forEach {
                        it.onJoinStateChanged(_joinState)
                    }
                    createAndThenJoinZone(_currency.get(), _gameName.get())
                } else {
                    state = null
                    _joinState = JoinState.JOINING
                    joinStateListeners.forEach {
                        it.onJoinStateChanged(_joinState)
                    }
                    join(_zoneId.get())
                }
            } else {
            }
        }
        is ServerConnection.`DISCONNECTING$` -> {
            state = null
            _joinState = JoinState.DISCONNECTING
            joinStateListeners.forEach {
                it.onJoinStateChanged(_joinState)
            }
        }
        else -> {
        }
    }

    override fun onZoneNotificationReceived(notificationZoneId: ZoneId,
                                            zoneNotification: ZoneNotification
    ) {
        if (_joinState == JoinState.JOINED && _zoneId.get() == notificationZoneId) {
            fun updatePlayersAndTransactions() {
                val (updatedPlayers, updatedHiddenPlayers) = playersFromMembersAccounts(
                        notificationZoneId,
                        state!!.memberIdsToAccountIds,
                        state!!.zone.accounts().asKotlin(),
                        state!!.balances,
                        state!!.currency,
                        state!!.zone.members().asKotlin(),
                        state!!.zone.equityAccountId(),
                        state!!.connectedClients.values
                )
                if (updatedPlayers != state!!.players) {
                    val addedPlayers = updatedPlayers - state!!.players.keys
                    val changedPlayers = updatedPlayers.filter { (memberId, player) ->
                        val previousPlayer = state!!.players[memberId]
                        previousPlayer != null && previousPlayer != player
                    }
                    val removedPlayers = state!!.players - updatedPlayers.keys
                    if (addedPlayers.isNotEmpty()) {
                        gameActionListeners.forEach { listener ->
                            addedPlayers.values.forEach {
                                listener.onPlayerAdded(it)
                            }
                        }
                    }
                    if (changedPlayers.isNotEmpty()) {
                        gameActionListeners.forEach { listener ->
                            changedPlayers.values.forEach {
                                listener.onPlayerChanged(it)
                            }
                        }
                    }
                    if (removedPlayers.isNotEmpty()) {
                        gameActionListeners.forEach { listener ->
                            removedPlayers.values.forEach {
                                listener.onPlayerRemoved(it)
                            }
                        }
                    }
                    state!!.players = updatedPlayers
                    gameActionListeners.forEach {
                        it.onPlayersUpdated(updatedPlayers)
                    }
                }
                if (updatedHiddenPlayers != state!!.hiddenPlayers)
                    state!!.hiddenPlayers = updatedHiddenPlayers
                val updatedTransfers = transfersFromTransactions(
                        state!!.zone.transactions().asKotlin(),
                        state!!.currency,
                        state!!.accountIdsToMemberIds,
                        state!!.players + state!!.hiddenPlayers,
                        state!!.zone.accounts().asKotlin(),
                        state!!.zone.members().asKotlin()
                )
                if (updatedTransfers != state!!.transfers) {
                    val changedTransfers = updatedTransfers.filter { (transactionId, transfer) ->
                        val previousTransfer = state!!.transfers[transactionId]
                        previousTransfer != null && previousTransfer != transfer
                    }
                    if (changedTransfers.isNotEmpty()) {
                        gameActionListeners.forEach {
                            it.onTransfersChanged(changedTransfers.values)
                        }
                    }
                    state!!.transfers = updatedTransfers
                    gameActionListeners.forEach {
                        it.onTransfersUpdated(updatedTransfers)
                    }
                }
            }
            when (zoneNotification) {
                is `EmptyZoneNotification$` -> {
                }
                is ClientJoinedNotification -> {
                    val connectionId = zoneNotification.connectionId()
                    val publicKey = zoneNotification.publicKey()
                    val wasPublicKeyAlreadyJoined =
                            state!!.connectedClients.values.contains(publicKey)
                    if (!wasPublicKeyAlreadyJoined) {
                        state!!.connectedClients =
                                state!!.connectedClients + Pair(connectionId, publicKey)
                        val (joinedPlayers, joinedHiddenPlayers) = playersFromMembersAccounts(
                                notificationZoneId,
                                state!!.memberIdsToAccountIds.filterKeys {
                                    val ownerPublicKeys = state!!.zone.members().asKotlin()[it]!!
                                            .ownerPublicKeys()
                                    ownerPublicKeys.size() == 1 &&
                                            ownerPublicKeys.head() == publicKey
                                },
                                state!!.zone.accounts().asKotlin(),
                                state!!.balances,
                                state!!.currency,
                                state!!.zone.members().asKotlin(),
                                state!!.zone.equityAccountId(),
                                hashSetOf(publicKey)
                        )
                        if (joinedPlayers.isNotEmpty()) {
                            state!!.players = state!!.players + joinedPlayers
                            gameActionListeners.forEach { listener ->
                                joinedPlayers.values.forEach {
                                    listener.onPlayerChanged(it)
                                }
                            }
                            gameActionListeners.forEach {
                                it.onPlayersUpdated(state!!.players)
                            }
                        }
                        if (joinedHiddenPlayers.isNotEmpty()) {
                            state!!.hiddenPlayers = state!!.hiddenPlayers + joinedHiddenPlayers
                        }
                    }
                }
                is ClientQuitNotification -> {
                    val connectionId = zoneNotification.connectionId()
                    val publicKey = zoneNotification.publicKey()
                    state!!.connectedClients = state!!.connectedClients - connectionId
                    val isPublicKeyStillJoined = state!!.connectedClients.values.contains(publicKey)
                    if (!isPublicKeyStillJoined) {
                        val (quitPlayers, quitHiddenPlayers) = playersFromMembersAccounts(
                                notificationZoneId,
                                state!!.memberIdsToAccountIds.filterKeys {
                                    val ownerPublicKeys = state!!.zone.members().asKotlin()[it]!!
                                            .ownerPublicKeys()
                                    ownerPublicKeys.size() == 1 &&
                                            ownerPublicKeys.head() == publicKey
                                },
                                state!!.zone.accounts().asKotlin(),
                                state!!.balances,
                                state!!.currency,
                                state!!.zone.members().asKotlin(),
                                state!!.zone.equityAccountId(),
                                hashSetOf()
                        )
                        if (quitPlayers.isNotEmpty()) {
                            state!!.players = state!!.players + quitPlayers
                            gameActionListeners.forEach { listener ->
                                quitPlayers.values.forEach {
                                    listener.onPlayerChanged(it)
                                }
                            }
                            gameActionListeners.forEach {
                                it.onPlayersUpdated(state!!.players)
                            }
                        }
                        if (quitHiddenPlayers.isNotEmpty()) {
                            state!!.hiddenPlayers = state!!.hiddenPlayers + quitHiddenPlayers
                        }
                    }
                }
                is ZoneNameChangedNotification -> {
                    val name = zoneNotification.name()
                    state!!.zone = state!!.zone.copy(
                            state!!.zone.id(),
                            state!!.zone.equityAccountId(),
                            state!!.zone.members(),
                            state!!.zone.accounts(),
                            state!!.zone.transactions(),
                            state!!.zone.created(),
                            state!!.zone.expires(),
                            name,
                            state!!.zone.metadata()
                    )
                    gameActionListeners.forEach {
                        it.onGameNameChanged(name)
                    }
                    if (gameId.isDefined) {
                        gameId.get().foreach(object : AbstractFunction1<Long, Unit>() {
                            override fun apply(gameId: Long) {
                                `Future$`.`MODULE$`.apply(
                                        object : AbstractFunction0<Unit>() {
                                            override fun apply() {
                                                gameDatabase.updateGameName(
                                                        gameId,
                                                        if (name.isEmpty) null else name.get()
                                                )
                                            }
                                        }, asyncTaskEc)
                            }
                        }, mainThreadEc)
                    }
                }
                is MemberCreatedNotification -> {
                    val member = zoneNotification.member()
                    state!!.zone = state!!.zone.copy(
                            state!!.zone.id(),
                            state!!.zone.equityAccountId(),
                            state!!.zone.members().`$plus`(Tuple2(member.id(), member)),
                            state!!.zone.accounts(),
                            state!!.zone.transactions(),
                            state!!.zone.created(),
                            state!!.zone.expires(),
                            state!!.zone.name(),
                            state!!.zone.metadata()
                    )
                }
                is MemberUpdatedNotification -> {
                    val member = zoneNotification.member()
                    state!!.zone = state!!.zone.copy(
                            state!!.zone.id(),
                            state!!.zone.equityAccountId(),
                            state!!.zone.members().`$plus`(Tuple2(member.id(), member)),
                            state!!.zone.accounts(),
                            state!!.zone.transactions(),
                            state!!.zone.created(),
                            state!!.zone.expires(),
                            state!!.zone.name(),
                            state!!.zone.metadata()
                    )
                    val (updatedIdentities, updatedHiddenIdentities) =
                            identitiesFromMembersAccounts(
                                    notificationZoneId,
                                    state!!.memberIdsToAccountIds,
                                    state!!.zone.accounts().asKotlin(),
                                    state!!.balances,
                                    state!!.currency,
                                    state!!.zone.members().asKotlin(),
                                    state!!.zone.equityAccountId(),
                                    serverConnection.clientKey()
                            )
                    if (updatedIdentities != state!!.identities) {
                        val receivedIdentity =
                                if (!state!!.identities.contains(member.id()) &&
                                        !state!!.hiddenIdentities.contains(member.id())) {
                                    updatedIdentities[member.id()]
                                } else {
                                    null
                                }
                        val restoredIdentity =
                                if (!state!!.identities.contains(member.id()) &&
                                        state!!.hiddenIdentities.contains(member.id())) {
                                    updatedIdentities[member.id()]
                                } else {
                                    null
                                }
                        state!!.identities = updatedIdentities
                        gameActionListeners.forEach { it.onIdentitiesUpdated(updatedIdentities) }
                        if (receivedIdentity != null) {
                            gameActionListeners.forEach {
                                it.onIdentityReceived(receivedIdentity)
                            }
                        }
                        if (restoredIdentity != null) {
                            gameActionListeners.forEach {
                                it.onIdentityRestored(restoredIdentity)
                            }
                        }
                    }
                    if (updatedHiddenIdentities != state!!.hiddenIdentities) {
                        state!!.hiddenIdentities = updatedHiddenIdentities
                    }
                    updatePlayersAndTransactions()
                }
                is AccountCreatedNotification -> {
                    val account = zoneNotification.account()
                    state!!.zone = state!!.zone.copy(
                            state!!.zone.id(),
                            state!!.zone.equityAccountId(),
                            state!!.zone.members(),
                            state!!.zone.accounts().`$plus`(Tuple2(account.id(), account)),
                            state!!.zone.transactions(),
                            state!!.zone.created(),
                            state!!.zone.expires(),
                            state!!.zone.name(),
                            state!!.zone.metadata()
                    )
                    val createdMembersAccounts = membersAccountsFromAccounts(mapOf(
                            Pair(account.id(), state!!.zone.accounts().asKotlin()[account.id()]!!)
                    ))
                    state!!.memberIdsToAccountIds =
                            state!!.memberIdsToAccountIds + createdMembersAccounts
                    state!!.accountIdsToMemberIds =
                            state!!.accountIdsToMemberIds + createdMembersAccounts
                                    .map { (memberId, accountId) ->
                                        Pair(accountId, memberId)
                                    }
                    val (createdIdentity, createdHiddenIdentity) = identitiesFromMembersAccounts(
                            notificationZoneId,
                            createdMembersAccounts,
                            state!!.zone.accounts().asKotlin(),
                            state!!.balances,
                            state!!.currency,
                            state!!.zone.members().asKotlin(),
                            state!!.zone.equityAccountId(),
                            serverConnection.clientKey()
                    )
                    if (createdIdentity.isNotEmpty()) {
                        state!!.identities = state!!.identities + createdIdentity
                        gameActionListeners.forEach { it.onIdentitiesUpdated(state!!.identities) }
                        gameActionListeners.forEach {
                            it.onIdentityCreated(
                                    state!!.identities[account.ownerMemberIds().head()]!!
                            )
                        }
                    }
                    if (createdHiddenIdentity.isNotEmpty()) {
                        state!!.hiddenIdentities = state!!.hiddenIdentities + createdHiddenIdentity
                    }
                    val (createdPlayer, createdHiddenPlayer) = playersFromMembersAccounts(
                            notificationZoneId,
                            createdMembersAccounts,
                            state!!.zone.accounts().asKotlin(),
                            state!!.balances,
                            state!!.currency,
                            state!!.zone.members().asKotlin(),
                            state!!.zone.equityAccountId(),
                            state!!.connectedClients.values
                    )
                    if (createdPlayer.isNotEmpty()) {
                        state!!.players = state!!.players + createdPlayer
                        gameActionListeners.forEach { listener ->
                            createdPlayer.values.forEach {
                                listener.onPlayerAdded(it)
                            }
                        }
                        gameActionListeners.forEach { it.onPlayersUpdated(state!!.players) }
                    }
                    if (createdHiddenPlayer.isNotEmpty()) {
                        state!!.hiddenPlayers = state!!.hiddenPlayers + createdHiddenPlayer
                    }
                }
                is AccountUpdatedNotification -> {
                    val account = zoneNotification.account()
                    state!!.zone = state!!.zone.copy(
                            state!!.zone.id(),
                            state!!.zone.equityAccountId(),
                            state!!.zone.members(),
                            state!!.zone.accounts().`$plus`(Tuple2(account.id(), account)),
                            state!!.zone.transactions(),
                            state!!.zone.created(),
                            state!!.zone.expires(),
                            state!!.zone.name(),
                            state!!.zone.metadata()
                    )
                    state!!.memberIdsToAccountIds =
                            membersAccountsFromAccounts(state!!.zone.accounts().asKotlin())
                    state!!.accountIdsToMemberIds =
                            state!!.memberIdsToAccountIds
                                    .map { (memberId, accountId) ->
                                        Pair(accountId, memberId)
                                    }.toMap()
                    val (updatedIdentities, updatedHiddenIdentities) =
                            identitiesFromMembersAccounts(
                                    notificationZoneId,
                                    state!!.memberIdsToAccountIds,
                                    state!!.zone.accounts().asKotlin(),
                                    state!!.balances,
                                    state!!.currency,
                                    state!!.zone.members().asKotlin(),
                                    state!!.zone.equityAccountId(),
                                    serverConnection.clientKey()
                            )
                    if (updatedIdentities != state!!.identities) {
                        state!!.identities = updatedIdentities
                        gameActionListeners.forEach { it.onIdentitiesUpdated(updatedIdentities) }
                    }
                    if (updatedHiddenIdentities != state!!.hiddenIdentities) {
                        state!!.hiddenIdentities = updatedHiddenIdentities
                    }
                    updatePlayersAndTransactions()
                }
                is TransactionAddedNotification -> {
                    val transaction = zoneNotification.transaction()
                    state!!.zone = state!!.zone.copy(
                            state!!.zone.id(),
                            state!!.zone.equityAccountId(),
                            state!!.zone.members(),
                            state!!.zone.accounts(),
                            state!!.zone.transactions().`$plus`(
                                    Tuple2(transaction.id(), transaction)
                            ),
                            state!!.zone.created(),
                            state!!.zone.expires(),
                            state!!.zone.name(),
                            state!!.zone.metadata()
                    )
                    state!!.balances = state!!.balances +
                            Pair(
                                    transaction.from()!!,
                                    state!!.balances.getOrDefault(
                                            transaction.from(),
                                            BigDecimal(0)
                                    ) - transaction.value().bigDecimal()
                            ) +
                            Pair(
                                    transaction.to()!!,
                                    state!!.balances.getOrDefault(
                                            transaction.to(),
                                            BigDecimal(0)
                                    ) + transaction.value().bigDecimal()
                            )
                    val changedMembersAccounts = membersAccountsFromAccounts(mapOf(
                            Pair(
                                    transaction.from(),
                                    state!!.zone.accounts().asKotlin()[transaction.from()]!!
                            ),
                            Pair(
                                    transaction.to(),
                                    state!!.zone.accounts().asKotlin()[transaction.to()]!!
                            )
                    ))
                    val (changedIdentities, changedHiddenIdentities) =
                            identitiesFromMembersAccounts(
                                    notificationZoneId,
                                    changedMembersAccounts,
                                    state!!.zone.accounts().asKotlin(),
                                    state!!.balances,
                                    state!!.currency,
                                    state!!.zone.members().asKotlin(),
                                    state!!.zone.equityAccountId(),
                                    serverConnection.clientKey()
                            )
                    if (changedIdentities.isNotEmpty()) {
                        state!!.identities = state!!.identities + changedIdentities
                        gameActionListeners.forEach {
                            it.onIdentitiesUpdated(state!!.identities)
                        }
                    }
                    if (changedHiddenIdentities.isNotEmpty()) {
                        state!!.hiddenIdentities =
                                state!!.hiddenIdentities + changedHiddenIdentities
                    }
                    val (changedPlayers, changedHiddenPlayers) = playersFromMembersAccounts(
                            notificationZoneId,
                            changedMembersAccounts,
                            state!!.zone.accounts().asKotlin(),
                            state!!.balances,
                            state!!.currency,
                            state!!.zone.members().asKotlin(),
                            state!!.zone.equityAccountId(),
                            state!!.connectedClients.values
                    )
                    if (changedPlayers.isNotEmpty()) {
                        state!!.players = state!!.players + changedPlayers
                        gameActionListeners.forEach { listener ->
                            changedPlayers.values.forEach { listener.onPlayerChanged(it) }
                        }
                        gameActionListeners.forEach {
                            it.onPlayersUpdated(state!!.players)
                        }
                    }
                    if (changedHiddenPlayers.isNotEmpty()) {
                        state!!.hiddenPlayers = state!!.hiddenPlayers + changedHiddenPlayers
                    }
                    val createdTransfer = transfersFromTransactions(
                            mapOf(
                                    Pair(transaction.id(), transaction)
                            ),
                            state!!.currency,
                            state!!.accountIdsToMemberIds,
                            state!!.players + state!!.hiddenPlayers,
                            state!!.zone.accounts().asKotlin(),
                            state!!.zone.members().asKotlin()
                    )
                    if (createdTransfer.isNotEmpty()) {
                        state!!.transfers = state!!.transfers + createdTransfer
                        gameActionListeners.forEach { listener ->
                            createdTransfer.values.forEach {
                                listener.onTransferAdded(it)
                            }
                        }
                        gameActionListeners.forEach {
                            it.onTransfersUpdated(state!!.transfers)
                        }
                    }
                }
            }
        }
    }

    private fun createAndThenJoinZone(currency: Currency, name: String) {
        val metadata = Some(Struct(
                scala.collection.immutable.Map.Map1(
                        CURRENCY_CODE_KEY,
                        Value.defaultInstance()
                                .withStringValue(currency.currencyCode)
                )
        ))
        serverConnection.sendCreateZoneCommand(
                CreateZoneCommand(
                        serverConnection.clientKey(),
                        bankMemberName,
                        Option.empty(),
                        Option.empty(),
                        Option.empty(),
                        Some(name),
                        metadata
                )
        ).foreach(object : AbstractFunction1<ZoneResponse, Unit>() {
            override fun apply(zoneResponse: ZoneResponse) {
                if (_joinState == JoinState.CREATING) {
                    val result = (zoneResponse as CreateZoneResponse).result().toOption()
                    if (result.isEmpty) {
                        gameActionListeners.forEach {
                            it.onCreateGameError(Some(name))
                        }
                    } else {
                        val zone = result.get()!!
                        instances += Pair(zone.id(), this@BoardGame)
                        _zoneId = Some(zone.id())
                        state = null
                        _joinState = JoinState.JOINING
                        joinStateListeners.forEach {
                            it.onJoinStateChanged(_joinState)
                        }
                        join(zone.id())
                    }
                }
            }
        }, mainThreadEc)
    }

    private fun join(zoneId: ZoneId) {
        serverConnection.sendZoneCommand(
                zoneId,
                `JoinZoneCommand$`.`MODULE$`
        ).foreach(object : AbstractFunction1<ZoneResponse, Unit>() {
            override fun apply(zoneResponse: ZoneResponse) {
                if (_joinState == JoinState.JOINING) {
                    val result = (zoneResponse as JoinZoneResponse).result().toOption()
                    if (result.isEmpty) {
                        gameActionListeners.forEach {
                            it.onJoinGameError()
                        }
                    } else {
                        val zoneAndConnectedClients = result.get()
                        val zone = zoneAndConnectedClients._1!!
                        val connectedClients = zoneAndConnectedClients._2!!
                        var balances: Map<AccountId, BigDecimal> = HashMap()
                        zone.transactions().values().foreach(
                                object : AbstractFunction1<Transaction, Unit>() {
                                    override fun apply(transaction: Transaction) {
                                        balances = balances +
                                                Pair(
                                                        transaction.from()!!,
                                                        balances.getOrDefault(
                                                                transaction.from(),
                                                                BigDecimal(0)
                                                        ) - transaction.value().bigDecimal()
                                                ) +
                                                Pair(
                                                        transaction.to()!!,
                                                        balances.getOrDefault(
                                                                transaction.to(), BigDecimal(0)
                                                        ) + transaction.value().bigDecimal()
                                                )
                                    }
                                }
                        )
                        val currency = currencyFromMetadata(zone.metadata())
                        val memberIdsToAccountIds = membersAccountsFromAccounts(
                                zone.accounts().asKotlin()
                        )
                        val accountIdsToMemberIds =
                                memberIdsToAccountIds.map { (accountId, memberId) ->
                                    Pair(memberId, accountId)
                                }.toMap()
                        val (identities, hiddenIdentities) = identitiesFromMembersAccounts(
                                zoneId,
                                memberIdsToAccountIds,
                                zone.accounts().asKotlin(),
                                balances,
                                currency,
                                zone.members().asKotlin(),
                                zone.equityAccountId(),
                                serverConnection.clientKey()
                        )
                        val (players, hiddenPlayers) = playersFromMembersAccounts(
                                zoneId,
                                memberIdsToAccountIds,
                                zone.accounts().asKotlin(),
                                balances,
                                currency,
                                zone.members().asKotlin(),
                                zone.equityAccountId(),
                                asKotlin(connectedClients.values().toSeq())
                        )
                        val transfers = transfersFromTransactions(
                                zone.transactions().asKotlin(),
                                currency,
                                accountIdsToMemberIds,
                                players + hiddenPlayers,
                                zone.accounts().asKotlin(),
                                zone.members().asKotlin()
                        )
                        state = State(
                                zone,
                                connectedClients.asKotlin(),
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
                        _joinState = JoinState.JOINED
                        joinStateListeners.forEach {
                            it.onJoinStateChanged(_joinState)
                        }
                        gameActionListeners.forEach {
                            it.onGameNameChanged(zone.name())
                        }
                        gameActionListeners.forEach {
                            it.onIdentitiesUpdated(identities)
                        }
                        gameActionListeners.forEach {
                            it.onPlayersInitialized(players.values)
                        }
                        gameActionListeners.forEach {
                            it.onPlayersUpdated(players)
                        }
                        gameActionListeners.forEach {
                            it.onTransfersInitialized(transfers.values)
                        }
                        gameActionListeners.forEach {
                            it.onTransfersUpdated(transfers)
                        }
                        val partiallyCreatedIdentities = zone.members().asKotlin()
                                .filter { (memberId, member) ->
                                    member.ownerPublicKeys().size() == 1 &&
                                            member.ownerPublicKeys().head() ==
                                                    serverConnection.clientKey()
                                            && !asKotlin(zone.accounts().values().toSeq())
                                            .any { account ->
                                                account.ownerMemberIds().size() == 1 &&
                                                        account.ownerMemberIds().head() == memberId
                                            }
                                }.values
                        partiallyCreatedIdentities.forEach {
                            createAccount(it)
                        }

                        // Since we must only prompt for a required identity if none exist yet and
                        // since having one or more partially created identities implies that
                        // gameId would be set, we can proceed here without checking that
                        // partiallyCreatedIdentityIds is non empty.
                        //
                        // The second condition isn't usually of significance but exists to prevent
                        // incorrectly prompting for an identity if a user rejoins a game by
                        // scanning its code again rather than by clicking its list item.
                        if (gameId.isEmpty && !(identities + hiddenIdentities).values.any {
                            it.account.id() != zone.equityAccountId()
                        }) {
                            gameActionListeners.forEach {
                                it.onIdentityRequired()
                            }
                        }

                        // We don't set gameId until now as it also indicates above whether we've
                        // prompted for the required identity - which we must do at most once.
                        if (gameId.isEmpty) {
                            gameId = Some(
                                    `Future$`.`MODULE$`.apply(
                                            object : AbstractFunction0<Long>() {
                                                override fun apply(): Long {
                                                    // This is in case a user rejoins a game by
                                                    // scanning its code again rather than by
                                                    // clicking its list item - in such cases we
                                                    // mustn't attempt to insert an entry as that
                                                    // would silently fail (as it happens on the
                                                    // Future's worker thread), but we may need to
                                                    // update the existing entries name.
                                                    val gameId = Option.apply<Long>(
                                                            gameDatabase.checkAndUpdateGame(
                                                                    zoneId,
                                                                    if (zone.name().isEmpty) {
                                                                        null
                                                                    } else {
                                                                        zone.name().get()
                                                                    }
                                                            ))
                                                    return if (gameId.isEmpty) {
                                                        gameDatabase.insertGame(
                                                                zoneId,
                                                                zone.created(),
                                                                zone.expires(),
                                                                if (zone.name().isEmpty) {
                                                                    null
                                                                } else {
                                                                    zone.name().get()
                                                                }
                                                        )
                                                    } else {
                                                        gameId.get()
                                                    }
                                                }
                                            }, asyncTaskEc)
                            )
                        } else {
                            gameId.get().foreach(object : AbstractFunction1<Long, Unit>() {
                                override fun apply(gameId: Long) {
                                    `Future$`.`MODULE$`.apply(
                                            object : AbstractFunction0<Unit>() {
                                                override fun apply() {
                                                    gameDatabase.checkAndUpdateGame(
                                                            zoneId,
                                                            if (zone.name().isEmpty) {
                                                                null
                                                            } else {
                                                                zone.name().get()
                                                            }
                                                    )
                                                }
                                            }, asyncTaskEc)
                                }
                            }, mainThreadEc)
                        }
                    }
                }
            }
        }, mainThreadEc)
    }

    private fun createAccount(ownerMember: Member) {
        serverConnection.sendZoneCommand(
                _zoneId.get(),
                CreateAccountCommand(
                        scala.collection.immutable.Set.Set1(ownerMember.id()),
                        Option.empty(),
                        Option.empty()
                )
        ).foreach(object : AbstractFunction1<ZoneResponse, Unit>() {
            override fun apply(zoneResponse: ZoneResponse) {
                if (_joinState == JoinState.CREATING) {
                    val result = (zoneResponse as CreateAccountResponse).result().toOption()
                    if (result.isEmpty) {
                        gameActionListeners.forEach {
                            it.onCreateIdentityAccountError(ownerMember.name())
                        }
                    }
                }
            }
        }, mainThreadEc)
    }

}
