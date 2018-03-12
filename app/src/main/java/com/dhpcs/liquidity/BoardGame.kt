package com.dhpcs.liquidity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import com.dhpcs.liquidity.BoardGame.Companion.JoinState.*
import com.dhpcs.liquidity.proto.model.Model
import com.dhpcs.liquidity.proto.ws.protocol.WsProtocol
import com.google.protobuf.ByteString
import com.google.protobuf.StringValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.Serializable
import java.math.BigDecimal
import java.util.Currency
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.component1
import kotlin.collections.component2

@Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
class BoardGame private constructor(
        private val context: Context,
        private val serverConnection: ServerConnection,
        private val gameDatabase: GameDatabase,
        private val _currency: Currency?,
        private val _gameName: String?,
        private val bankMemberName: String?,
        _zoneId: String?,
        _gameId: Long?
) {

    companion object {

        private val connectionStateFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)

        interface GameDatabase {

            fun insertGame(zoneId: String, created: Long, expires: Long, name: String?): Long

            fun checkAndUpdateGame(zoneId: String, name: String?): Long?

            fun updateGameName(gameId: Long, name: String?)

        }

        enum class JoinState {
            UNAVAILABLE,
            FAILED,
            AVAILABLE,
            CREATING,
            JOINING,
            JOINED
        }

        interface Player : Serializable {
            val zoneId: String
            val memberId: String
            val ownerPublicKey: ByteString
            val name: String?
            val isHidden: Boolean
            val accountId: String
            val isBanker: Boolean
        }

        interface Identity : Player

        data class PlayerWithBalanceAndConnectionState(
                override val zoneId: String,
                override val memberId: String,
                override val ownerPublicKey: ByteString,
                override val name: String?,
                override val isHidden: Boolean,
                override val accountId: String,
                val balance: BigDecimal,
                val currency: String?,
                override val isBanker: Boolean,
                val isConnected: Boolean
        ) : Player

        data class IdentityWithBalance(
                override val zoneId: String,
                override val memberId: String,
                override val ownerPublicKey: ByteString,
                override val name: String?,
                override val isHidden: Boolean,
                override val accountId: String,
                val balance: BigDecimal,
                val currency: String?,
                override val isBanker: Boolean
        ) : Identity

        interface Transfer : Serializable {
            val fromAccountName: String?
            val fromAccountId: String
            val fromPlayer: Player?
            val toAccountId: String
            val toAccountName: String?
            val toPlayer: Player?
            val transactionId: String
            val created: Long
            val value: BigDecimal
        }

        data class TransferWithCurrency(
                override val fromAccountId: String,
                override val fromAccountName: String?,
                override val fromPlayer: Player?,
                override val toAccountId: String,
                override val toAccountName: String?,
                override val toPlayer: Player?,
                override val transactionId: String,
                override val created: Long,
                override val value: BigDecimal,
                val currency: String?
        ) : Transfer

        interface GameActionListener {

            fun onJoinStateChanged(joinState: JoinState)

            fun onChangeGameNameError(name: String?)

            fun onChangeIdentityNameError(name: String?)

            fun onCreateIdentityAccountError(name: String?)

            fun onCreateIdentityMemberError(name: String?)

            fun onCreateGameError(name: String?)

            fun onDeleteIdentityError(name: String?)

            fun onGameNameChanged(name: String?)

            fun onIdentitiesUpdated(identities: Map<String, IdentityWithBalance>)

            fun onIdentityCreated(identity: IdentityWithBalance)

            fun onIdentityReceived(identity: IdentityWithBalance)

            fun onIdentityRequired()

            fun onIdentityRestored(identity: IdentityWithBalance)

            fun onJoinGameError()

            fun onPlayerAdded(addedPlayer: PlayerWithBalanceAndConnectionState)

            fun onPlayerChanged(changedPlayer: PlayerWithBalanceAndConnectionState)

            fun onPlayersInitialized(players: Collection<PlayerWithBalanceAndConnectionState>)

            fun onPlayerRemoved(removedPlayer: PlayerWithBalanceAndConnectionState)

            fun onPlayersUpdated(players: Map<String, PlayerWithBalanceAndConnectionState>)

            fun onQuitGameError()

            fun onRestoreIdentityError(name: String?)

            fun onTransferAdded(addedTransfer: TransferWithCurrency)

            fun onTransferIdentityError(name: String?)

            fun onTransferToPlayerError(name: String?)

            fun onTransfersChanged(changedTransfers: Collection<TransferWithCurrency>)

            fun onTransfersInitialized(transfers: Collection<TransferWithCurrency>)

            fun onTransfersUpdated(transfers: Map<String, TransferWithCurrency>)

        }

        class JoinRequestToken

        const val MAXIMUM_TAG_LENGTH = 160

        private const val CURRENCY_CODE_KEY = "currency"
        private const val HIDDEN_FLAG_KEY = "hidden"

        private class State(
                var zone: Model.Zone,
                var connectedClients: Map<String, ByteString>,
                var balances: Map<String, BigDecimal>,
                var currency: String?,
                var memberIdsToAccountIds: Map<String, String>,
                var accountIdsToMemberIds: Map<String, String>,
                var identities: Map<String, IdentityWithBalance>,
                var hiddenIdentities: Map<String, IdentityWithBalance>,
                var players: Map<String, PlayerWithBalanceAndConnectionState>,
                var hiddenPlayers: Map<String, PlayerWithBalanceAndConnectionState>,
                var transfers: Map<String, TransferWithCurrency>
        )

        private var instances: Map<String, BoardGame> = HashMap()

        fun getInstance(zoneId: String): BoardGame? = instances[zoneId]

        fun <K, V> getOrDefault(map: Map<K, V>, key: K, defaultValue: V): V {
            return map[key] ?: defaultValue
        }

        fun isGameNameValid(name: CharSequence): Boolean = isTagValid(name)

        fun isTagValid(tag: CharSequence): Boolean {
            return tag.isNotEmpty() && tag.length <= MAXIMUM_TAG_LENGTH
        }

        private fun currencyFromMetadata(metadata: Struct?): String? {
            return metadata?.fieldsMap?.get((CURRENCY_CODE_KEY))?.stringValue
        }

        private fun membersAccountsFromAccounts(accounts: List<Model.Account>
        ): Map<String, String> {
            return accounts.filter {
                it.ownerMemberIdsCount == 1
            }.groupBy {
                it.getOwnerMemberIds(0)
            }.filterValues {
                it.size == 1
            }.mapValues {
                it.value.first().id
            }
        }

        private fun identitiesFromMembersAccounts(
                zoneId: String,
                membersAccounts: Map<String, String>,
                accounts: List<Model.Account>,
                balances: Map<String, BigDecimal>,
                currency: String?,
                members: List<Model.Member>,
                equityAccountId: String,
                clientKey: ByteString
        ): Pair<Map<String, IdentityWithBalance>, Map<String, IdentityWithBalance>> {
            val identitiesFromMembersAccounts = membersAccounts.filterKeys { memberId ->
                val member = members.find { it.id == memberId }!!
                member.ownerPublicKeysCount == 1 &&
                        member.getOwnerPublicKeys(0) == clientKey
            }.mapValues { (memberId, accountId) ->
                val member = members.find { it.id == memberId }!!
                IdentityWithBalance(
                        zoneId,
                        member.id,
                        member.getOwnerPublicKeys(0),
                        if (!member.hasName()) null else member.name.value,
                        isHidden(member),
                        accounts.find { it.id == accountId }!!.id,
                        getOrDefault(balances, accountId, BigDecimal(0)),
                        currency,
                        accountId == equityAccountId
                )
            }
            val notHidden = identitiesFromMembersAccounts.filterValues { !it.isHidden }
            val hidden = identitiesFromMembersAccounts.filterValues { it.isHidden }
            return Pair(notHidden, hidden)
        }

        private fun playersFromMembersAccounts(zoneId: String,
                                               membersAccounts: Map<String, String>,
                                               accounts: List<Model.Account>,
                                               balances: Map<String, BigDecimal>,
                                               currency: String?,
                                               members: List<Model.Member>,
                                               equityAccountId: String,
                                               connectedClients: Collection<ByteString>
        ): Pair<Map<String, PlayerWithBalanceAndConnectionState>,
                Map<String, PlayerWithBalanceAndConnectionState>> {
            val playersFromMembersAccounts = membersAccounts.filterKeys { memberId ->
                members.find { it.id == memberId }!!.ownerPublicKeysCount == 1
            }.mapValues { (memberId, accountId) ->
                val member = members.find { it.id == memberId }!!
                PlayerWithBalanceAndConnectionState(
                        zoneId,
                        member.id,
                        member.getOwnerPublicKeys(0),
                        if (!member.hasName()) null else member.name.value,
                        isHidden(member),
                        accounts.find { it.id == accountId }!!.id,
                        getOrDefault(balances, accountId, BigDecimal(0)),
                        currency,
                        accountId == equityAccountId,
                        connectedClients.any { member.ownerPublicKeysList.contains(it) }
                )
            }
            val notHidden = playersFromMembersAccounts.filterValues { !it.isHidden }
            val hidden = playersFromMembersAccounts.filterValues { it.isHidden }
            return Pair(notHidden, hidden)
        }

        private fun isHidden(member: Model.Member): Boolean {
            return if (!member.hasMetadata()) {
                false
            } else {
                val hidden = member.metadata.fieldsMap[HIDDEN_FLAG_KEY]
                hidden?.boolValue ?: false
            }
        }

        private fun transfersFromTransactions(transactions: List<Model.Transaction>,
                                              currency: String?,
                                              accountsMembers: Map<String, String>,
                                              players: Map<String, Player>,
                                              accounts: List<Model.Account>
        ): Map<String, TransferWithCurrency> {
            return transactions.map { transaction ->
                val fromAccount = accounts.find { it.id == transaction.from }!!
                val fromMemberId = accountsMembers[transaction.from]
                val toAccount = accounts.find { it.id == transaction.to }!!
                val toMemberId = accountsMembers[transaction.to]
                TransferWithCurrency(
                        fromAccount.id,
                        if (!fromAccount.hasName()) null else fromAccount.name.value,
                        players[fromMemberId]!!,
                        toAccount.id,
                        if (!toAccount.hasName()) null else toAccount.name.value,
                        players[toMemberId]!!,
                        transaction.id,
                        transaction.created,
                        BigDecimal(transaction.value),
                        currency
                )
            }.associateBy { it.transactionId }
        }

    }

    private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val connectionStateReceiver = object : BroadcastReceiver() {

        override fun onReceive(context1: Context, intent: Intent) {
            handleConnectivityStateChange()
        }

    }

    constructor (context: Context,
                 serverConnection: ServerConnection,
                 gameDatabase: GameDatabase,
                 currency: Currency,
                 gameName: String,
                 bankMemberName: String
    ) : this(
            context,
            serverConnection,
            gameDatabase,
            currency,
            gameName,
            bankMemberName,
            null,
            null
    )

    constructor(context: Context,
                serverConnection: ServerConnection,
                gameDatabase: GameDatabase,
                _zoneId: String
    ) : this(
            context,
            serverConnection,
            gameDatabase,
            null,
            null,
            null,
            _zoneId,
            null
    )

    constructor(context: Context,
                serverConnection: ServerConnection,
                gameDatabase: GameDatabase,
                _zoneId: String,
                _gameId: Long
    ) : this(context,
            serverConnection,
            gameDatabase,
            null,
            null,
            null,
            _zoneId,
            _gameId
    )

    private var joinRequestTokens: Set<JoinRequestToken> = HashSet()
    private var gameActionListeners: Set<GameActionListener> = HashSet()

    var zoneId = _zoneId
        private set

    private var gameId: Single<Long>? = if (_gameId == null) null else Single.just(_gameId)

    var joinState: JoinState = UNAVAILABLE
        private set

    private var state: State? = null

    val currency get() = state!!.currency

    val gameName: String? get() = if (!state!!.zone.hasName()) null else state!!.zone.name.value

    val hiddenIdentities get() = state!!.hiddenIdentities.values

    val identities get() = state!!.identities.values

    val players get() = state!!.players.values

    init {
        handleConnectivityStateChange()
    }

    private fun handleConnectivityStateChange() {
        if (connectivityManager.activeNetworkInfo?.isConnected != true &&
                (joinState == AVAILABLE || joinState == FAILED)) {
            joinState = UNAVAILABLE
            gameActionListeners.forEach {
                it.onJoinStateChanged(joinState)
            }
        } else if (joinState == UNAVAILABLE) {
            joinState = AVAILABLE
            gameActionListeners.forEach {
                it.onJoinStateChanged(joinState)
            }
        }
    }

    fun registerListener(listener: GameActionListener) {
        if (gameActionListeners.isEmpty()) {
            context.registerReceiver(connectionStateReceiver, connectionStateFilter)
        }
        gameActionListeners += listener
        listener.onJoinStateChanged(joinState)
        if (joinState == JOINED) {
            listener.onGameNameChanged(
                    if (!state!!.zone.hasName()) null else state!!.zone.name.value
            )
            listener.onIdentitiesUpdated(state!!.identities)
            listener.onPlayersInitialized(state!!.players.values)
            listener.onPlayersUpdated(state!!.players)
            listener.onTransfersInitialized(state!!.transfers.values)
            listener.onTransfersUpdated(state!!.transfers)
        }
    }

    fun requestJoin(token: JoinRequestToken, retryIfFailed: Boolean = false) {
        val zoneId = zoneId
        if (joinRequestTokens.isEmpty()) {
            if (zoneId != null && !instances.contains(zoneId)) {
                instances += Pair(zoneId, this)
            }
        }
        joinRequestTokens += token
        if (joinState == AVAILABLE || (joinState == FAILED && retryIfFailed)) {
            if (zoneId == null) {
                createAndThenJoinZone(_currency!!, _gameName!!)
            } else {
                join(zoneId)
            }
        }
    }

    fun unrequestJoin(token: JoinRequestToken) {
        joinRequestTokens -= token
        if (joinRequestTokens.isEmpty()) {
            quit()
            val zoneId = zoneId
            if (zoneId != null && instances.contains(zoneId)) {
                instances -= zoneId
            }
        }
    }

    fun unregisterListener(listener: GameActionListener) {
        gameActionListeners -= listener
        if (gameActionListeners.isEmpty()) {
            context.unregisterReceiver(connectionStateReceiver)
        }
    }

    private fun createAndThenJoinZone(currency: Currency, name: String) {
        state = null
        joinState = CREATING
        gameActionListeners.forEach {
            it.onJoinStateChanged(joinState)
        }

        fun onError() {
            gameActionListeners.forEach {
                it.onCreateGameError(name)
            }
        }

        val metadata = Struct.newBuilder()
                .putFields(
                        CURRENCY_CODE_KEY,
                        Value.newBuilder().setStringValue(currency.currencyCode).build()
                )
        serverConnection.sendCreateZoneCommand(
                WsProtocol.ZoneCommand.CreateZoneCommand.newBuilder()
                        .setEquityOwnerPublicKey(serverConnection.clientKey)
                        .setEquityOwnerName(StringValue.newBuilder().setValue(bankMemberName))
                        .setName(StringValue.newBuilder().setValue(name))
                        .setMetadata(metadata)
                        .build()
        ).subscribe({ zoneResponse ->
            when (zoneResponse.createZoneResponse.resultCase) {
                WsProtocol.ZoneResponse.CreateZoneResponse.ResultCase.RESULT_NOT_SET -> {
                }
                WsProtocol.ZoneResponse.CreateZoneResponse.ResultCase.ERRORS ->
                    onError()
                WsProtocol.ZoneResponse.CreateZoneResponse.ResultCase.SUCCESS -> {
                    val zone = zoneResponse.createZoneResponse.success.zone!!
                    instances += Pair(zone.id, this)
                    zoneId = zone.id
                    join(zone.id)
                }
            }
        }, {
            onError()
        })
    }

    private var zoneNotificationDisposable: Disposable? = null

    private fun join(zoneId: String) {
        state = null
        joinState = JOINING
        gameActionListeners.forEach {
            it.onJoinStateChanged(joinState)
        }
        zoneNotificationDisposable = serverConnection
                .zoneNotifications(zoneId)
                .subscribe(
                        {
                            if (joinState == JOINING || joinState == JOINED) {
                                onZoneNotificationReceived(zoneId, it)
                            }
                        },
                        {
                            joinState = FAILED
                            gameActionListeners.forEach {
                                it.onJoinStateChanged(joinState)
                            }
                        },
                        {
                            joinState = AVAILABLE
                            gameActionListeners.forEach {
                                it.onJoinStateChanged(joinState)
                            }
                        }
                )
    }

    private fun quit() {
        zoneNotificationDisposable?.dispose()
        zoneNotificationDisposable = null
        joinState = AVAILABLE
        gameActionListeners.forEach {
            it.onJoinStateChanged(joinState)
        }
    }

    private fun onZoneNotificationReceived(
            zoneId: String,
            zoneNotification: WsProtocol.ZoneNotification
    ) {
        fun updatePlayersAndTransactions() {
            val (updatedPlayers, updatedHiddenPlayers) = playersFromMembersAccounts(
                    zoneId,
                    state!!.memberIdsToAccountIds,
                    state!!.zone.accountsList,
                    state!!.balances,
                    state!!.currency,
                    state!!.zone.membersList,
                    state!!.zone.equityAccountId,
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
                    state!!.zone.transactionsList,
                    state!!.currency,
                    state!!.accountIdsToMemberIds,
                    state!!.players + state!!.hiddenPlayers,
                    state!!.zone.accountsList
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
        when (zoneNotification.zoneNotificationCase) {
            WsProtocol.ZoneNotification.ZoneNotificationCase.ZONENOTIFICATION_NOT_SET -> {
            }
            WsProtocol.ZoneNotification.ZoneNotificationCase
                    .CLIENT_JOINED_ZONE_NOTIFICATION -> {
                val connectionId = zoneNotification.clientJoinedZoneNotification.connectionId
                val publicKey = zoneNotification.clientJoinedZoneNotification.publicKey
                val wasPublicKeyAlreadyJoined =
                        state!!.connectedClients.values.contains(publicKey)
                if (!wasPublicKeyAlreadyJoined) {
                    state!!.connectedClients =
                            state!!.connectedClients + Pair(connectionId, publicKey)
                    val (joinedPlayers, joinedHiddenPlayers) = playersFromMembersAccounts(
                            zoneId,
                            state!!.memberIdsToAccountIds.filterKeys { memberId ->
                                val member = state!!.zone.membersList.find {
                                    it.id == memberId
                                }!!
                                member.ownerPublicKeysCount == 1 &&
                                        member.getOwnerPublicKeys(0) == publicKey
                            },
                            state!!.zone.accountsList,
                            state!!.balances,
                            state!!.currency,
                            state!!.zone.membersList,
                            state!!.zone.equityAccountId,
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
            WsProtocol.ZoneNotification.ZoneNotificationCase.CLIENT_QUIT_ZONE_NOTIFICATION -> {
                val connectionId = zoneNotification.clientQuitZoneNotification.connectionId
                val publicKey = zoneNotification.clientQuitZoneNotification.publicKey
                state!!.connectedClients = state!!.connectedClients - connectionId
                val isPublicKeyStillJoined = state!!.connectedClients.values.contains(publicKey)
                if (!isPublicKeyStillJoined) {
                    val (quitPlayers, quitHiddenPlayers) = playersFromMembersAccounts(
                            zoneId,
                            state!!.memberIdsToAccountIds.filterKeys { memberId ->
                                val member = state!!.zone.membersList.find {
                                    it.id == memberId
                                }!!
                                member.ownerPublicKeysCount == 1 &&
                                        member.getOwnerPublicKeys(0) == publicKey
                            },
                            state!!.zone.accountsList,
                            state!!.balances,
                            state!!.currency,
                            state!!.zone.membersList,
                            state!!.zone.equityAccountId,
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
            WsProtocol.ZoneNotification.ZoneNotificationCase.ZONE_NAME_CHANGED_NOTIFICATION -> {
                val name = zoneNotification.zoneNameChangedNotification.name
                state!!.zone = state!!.zone.toBuilder()
                        .setName(name)
                        .build()
                gameActionListeners.forEach {
                    it.onGameNameChanged(
                            if (!zoneNotification.zoneNameChangedNotification.hasName()) {
                                null
                            } else {
                                zoneNotification.zoneNameChangedNotification.name.value
                            }
                    )
                }
                gameId!!.subscribeOn(Schedulers.io()).subscribe { gameId ->
                    gameDatabase.updateGameName(
                            gameId,
                            if (!zoneNotification
                                            .zoneNameChangedNotification
                                            .hasName()) {
                                null
                            } else {
                                zoneNotification
                                        .zoneNameChangedNotification
                                        .name.value
                            }
                    )
                }
            }
            WsProtocol.ZoneNotification.ZoneNotificationCase.MEMBER_CREATED_NOTIFICATION -> {
                val member = zoneNotification.memberCreatedNotification.member
                state!!.zone = state!!.zone.toBuilder()
                        .addMembers(member)
                        .build()
            }
            WsProtocol.ZoneNotification.ZoneNotificationCase.MEMBER_UPDATED_NOTIFICATION -> {
                val member = zoneNotification.memberUpdatedNotification.member
                state!!.zone = state!!.zone.toBuilder()
                        .removeMembers(
                                state!!.zone.membersList.indexOfFirst {
                                    it.id == member.id
                                }
                        )
                        .addMembers(member)
                        .build()
                val (updatedIdentities, updatedHiddenIdentities) =
                        identitiesFromMembersAccounts(
                                zoneId,
                                state!!.memberIdsToAccountIds,
                                state!!.zone.accountsList,
                                state!!.balances,
                                state!!.currency,
                                state!!.zone.membersList,
                                state!!.zone.equityAccountId,
                                serverConnection.clientKey
                        )
                if (updatedIdentities != state!!.identities) {
                    val receivedIdentity =
                            if (!state!!.identities.contains(member.id) &&
                                    !state!!.hiddenIdentities.contains(member.id)) {
                                updatedIdentities[member.id]
                            } else {
                                null
                            }
                    val restoredIdentity =
                            if (!state!!.identities.contains(member.id) &&
                                    state!!.hiddenIdentities.contains(member.id)) {
                                updatedIdentities[member.id]
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
            WsProtocol.ZoneNotification.ZoneNotificationCase.ACCOUNT_CREATED_NOTIFICATION -> {
                val account = zoneNotification.accountCreatedNotification.account
                state!!.zone = state!!.zone.toBuilder()
                        .addAccounts(account)
                        .build()
                val createdMembersAccounts = membersAccountsFromAccounts(
                        state!!.zone.accountsList.filter { it.id == account.id }
                )
                state!!.memberIdsToAccountIds =
                        state!!.memberIdsToAccountIds + createdMembersAccounts
                state!!.accountIdsToMemberIds =
                        state!!.accountIdsToMemberIds + createdMembersAccounts
                        .map { (memberId, accountId) ->
                            Pair(accountId, memberId)
                        }
                val (createdIdentity, createdHiddenIdentity) = identitiesFromMembersAccounts(
                        zoneId,
                        createdMembersAccounts,
                        state!!.zone.accountsList,
                        state!!.balances,
                        state!!.currency,
                        state!!.zone.membersList,
                        state!!.zone.equityAccountId,
                        serverConnection.clientKey
                )
                if (createdIdentity.isNotEmpty()) {
                    state!!.identities = state!!.identities + createdIdentity
                    gameActionListeners.forEach { it.onIdentitiesUpdated(state!!.identities) }
                    gameActionListeners.forEach {
                        it.onIdentityCreated(
                                state!!.identities[account.getOwnerMemberIds(0)]!!
                        )
                    }
                }
                if (createdHiddenIdentity.isNotEmpty()) {
                    state!!.hiddenIdentities = state!!.hiddenIdentities + createdHiddenIdentity
                }
                val (createdPlayer, createdHiddenPlayer) = playersFromMembersAccounts(
                        zoneId,
                        createdMembersAccounts,
                        state!!.zone.accountsList,
                        state!!.balances,
                        state!!.currency,
                        state!!.zone.membersList,
                        state!!.zone.equityAccountId,
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
            WsProtocol.ZoneNotification.ZoneNotificationCase.ACCOUNT_UPDATED_NOTIFICATION -> {
                val account = zoneNotification.accountUpdatedNotification.account
                state!!.zone = state!!.zone.toBuilder()
                        .removeAccounts(
                                state!!.zone.accountsList.indexOfFirst {
                                    it.id == account.id
                                }
                        )
                        .addAccounts(account)
                        .build()
                state!!.memberIdsToAccountIds =
                        membersAccountsFromAccounts(state!!.zone.accountsList)
                state!!.accountIdsToMemberIds =
                        state!!.memberIdsToAccountIds
                                .map { (memberId, accountId) ->
                                    Pair(accountId, memberId)
                                }.toMap()
                val (updatedIdentities, updatedHiddenIdentities) =
                        identitiesFromMembersAccounts(
                                zoneId,
                                state!!.memberIdsToAccountIds,
                                state!!.zone.accountsList,
                                state!!.balances,
                                state!!.currency,
                                state!!.zone.membersList,
                                state!!.zone.equityAccountId,
                                serverConnection.clientKey
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
            WsProtocol.ZoneNotification.ZoneNotificationCase.TRANSACTION_ADDED_NOTIFICATION -> {
                val transaction = zoneNotification.transactionAddedNotification.transaction
                state!!.zone = state!!.zone.toBuilder()
                        .addTransactions(transaction)
                        .build()
                state!!.balances = state!!.balances +
                        Pair(
                                transaction.from!!,
                                getOrDefault(
                                        state!!.balances,
                                        transaction.from,
                                        BigDecimal(0)
                                ) - BigDecimal(transaction.value)
                        ) +
                        Pair(
                                transaction.to!!,
                                getOrDefault(
                                        state!!.balances,
                                        transaction.to,
                                        BigDecimal(0)
                                ) + BigDecimal(transaction.value)
                        )
                val changedMembersAccounts = membersAccountsFromAccounts(listOf(
                        state!!.zone.accountsList.find { it.id == transaction.from }!!,
                        state!!.zone.accountsList.find { it.id == transaction.to }!!
                ))
                val (changedIdentities, changedHiddenIdentities) =
                        identitiesFromMembersAccounts(
                                zoneId,
                                changedMembersAccounts,
                                state!!.zone.accountsList,
                                state!!.balances,
                                state!!.currency,
                                state!!.zone.membersList,
                                state!!.zone.equityAccountId,
                                serverConnection.clientKey
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
                        zoneId,
                        changedMembersAccounts,
                        state!!.zone.accountsList,
                        state!!.balances,
                        state!!.currency,
                        state!!.zone.membersList,
                        state!!.zone.equityAccountId,
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
                        listOf(transaction),
                        state!!.currency,
                        state!!.accountIdsToMemberIds,
                        state!!.players + state!!.hiddenPlayers,
                        state!!.zone.accountsList
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
            WsProtocol.ZoneNotification.ZoneNotificationCase.ZONE_STATE_NOTIFICATION -> {
                val zone = zoneNotification.zoneStateNotification.zone
                val connectedClients =
                        zoneNotification.zoneStateNotification.connectedClientsMap
                var balances: Map<String, BigDecimal> = HashMap()
                zone.transactionsList.forEach { transaction ->
                    balances = balances +
                            Pair(
                                    transaction.from!!,
                                    getOrDefault(
                                            balances,
                                            transaction.from,
                                            BigDecimal(0)
                                    ) - BigDecimal(transaction.value)
                            ) +
                            Pair(
                                    transaction.to!!,
                                    getOrDefault(
                                            balances,
                                            transaction.to,
                                            BigDecimal(0)
                                    ) + BigDecimal(transaction.value)
                            )
                }
                val currency = currencyFromMetadata(zone.metadata)
                val memberIdsToAccountIds = membersAccountsFromAccounts(zone.accountsList)
                val accountIdsToMemberIds =
                        memberIdsToAccountIds.map { (accountId, memberId) ->
                            Pair(memberId, accountId)
                        }.toMap()
                val (identities, hiddenIdentities) = identitiesFromMembersAccounts(
                        zoneId,
                        memberIdsToAccountIds,
                        zone.accountsList,
                        balances,
                        currency,
                        zone.membersList,
                        zone.equityAccountId,
                        serverConnection.clientKey
                )
                val (players, hiddenPlayers) = playersFromMembersAccounts(
                        zoneId,
                        memberIdsToAccountIds,
                        zone.accountsList,
                        balances,
                        currency,
                        zone.membersList,
                        zone.equityAccountId,
                        connectedClients.values
                )
                val transfers = transfersFromTransactions(
                        zone.transactionsList,
                        currency,
                        accountIdsToMemberIds,
                        players + hiddenPlayers,
                        zone.accountsList
                )
                state = State(
                        zone,
                        connectedClients,
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
                joinState = JOINED
                gameActionListeners.forEach {
                    it.onJoinStateChanged(joinState)
                }
                gameActionListeners.forEach {
                    it.onGameNameChanged(if (!zone.hasName()) null else zone.name.value)
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
                val partiallyCreatedIdentities = zone.membersList.filter { member ->
                    member.ownerPublicKeysCount == 1 &&
                            member.getOwnerPublicKeys(0) ==
                            serverConnection.clientKey &&
                            !zone.accountsList.any { account ->
                                account.ownerMemberIdsCount == 1 &&
                                        account.getOwnerMemberIds(0) == member.id
                            }
                }
                partiallyCreatedIdentities.forEach {
                    createAccount(it)
                }

                // Since we must only prompt for a required identity if none exist yet and since
                // having one or more partially created identities implies that gameId would be
                // set, we can proceed here without checking that partiallyCreatedIdentityIds is
                // non empty.
                //
                // The second condition isn't usually of significance but exists to prevent
                // incorrectly prompting for an identity if a user rejoins a game by scanning its
                // code again rather than by clicking its list item.
                if (gameId == null &&
                        !(identities + hiddenIdentities).values.any {
                            it.accountId != zone.equityAccountId
                        }) {
                    gameActionListeners.forEach {
                        it.onIdentityRequired()
                    }
                }

                // We don't set gameId until now as it also indicates above whether we've prompted
                // for the required identity - which we must do at most once.
                val gameId = gameId
                if (gameId == null) {
                    this.gameId = Single.fromCallable {
                        // This is in case a user rejoins a game by scanning its code again rather
                        // than by clicking its list item - in such cases we mustn't attempt to
                        // insert an entry as that would silently fail (as it happens on the
                        // Future's worker thread), but we may need to update the existing entry's
                        // name.
                        gameDatabase.checkAndUpdateGame(
                                zoneId,
                                if (!zone.hasName()) null else zone.name.value
                        ) ?: gameDatabase.insertGame(
                                zoneId,
                                zone.created,
                                zone.expires,
                                if (!zone.hasName()) null else zone.name.value
                        )
                    }.subscribeOn(Schedulers.io()).cache()
                    this.gameId!!.subscribe()
                } else {
                    gameId.subscribeOn(Schedulers.io()).subscribe { _ ->
                        gameDatabase.checkAndUpdateGame(
                                zoneId,
                                if (!zone.hasName()) null else zone.name.value
                        )
                    }
                }
            }
            WsProtocol.ZoneNotification.ZoneNotificationCase.PING_NOTIFICATION -> {
            }
        }
    }

    private fun createAccount(ownerMember: Model.Member) {
        fun onError() {
            gameActionListeners.forEach {
                it.onCreateIdentityAccountError(
                        if (!ownerMember.hasName()) {
                            null
                        } else {
                            ownerMember.name.value
                        }
                )
            }
        }
        serverConnection.sendZoneCommand(
                zoneId!!,
                WsProtocol.ZoneCommand.newBuilder()
                        .setCreateAccountCommand(
                                WsProtocol.ZoneCommand.CreateAccountCommand.newBuilder()
                                        .addOwnerMemberIds(ownerMember.id)
                        )
                        .build()
        ).subscribe({ zoneResponse ->
            when (zoneResponse.createAccountResponse.resultCase) {
                WsProtocol.ZoneResponse.CreateAccountResponse.ResultCase.RESULT_NOT_SET -> {
                }
                WsProtocol.ZoneResponse.CreateAccountResponse.ResultCase.ERRORS ->
                    onError()
                WsProtocol.ZoneResponse.CreateAccountResponse.ResultCase.SUCCESS -> {
                }
            }
        }, {
            onError()
        })
    }

    fun changeGameName(name: String?) {
        fun onError() {
            gameActionListeners.forEach {
                it.onChangeGameNameError(name)
            }
        }
        serverConnection.sendZoneCommand(
                zoneId!!,
                WsProtocol.ZoneCommand.newBuilder()
                        .setChangeZoneNameCommand(
                                WsProtocol.ZoneCommand.ChangeZoneNameCommand.newBuilder()
                                        .setName(StringValue.newBuilder().setValue(name))
                        )
                        .build()
        ).subscribe({ zoneResponse ->
            when (zoneResponse.changeZoneNameResponse.resultCase) {
                WsProtocol.ZoneResponse.ChangeZoneNameResponse.ResultCase.RESULT_NOT_SET -> {
                }
                WsProtocol.ZoneResponse.ChangeZoneNameResponse.ResultCase.ERRORS ->
                    onError()
                WsProtocol.ZoneResponse.ChangeZoneNameResponse.ResultCase.SUCCESS -> {
                }
            }
        }, {
            onError()
        })
    }

    fun isIdentityNameValid(name: CharSequence): Boolean {
        return isTagValid(name) &&
                name.toString() != state!!.zone.membersList.find {
            it.id == state!!.accountIdsToMemberIds[state!!.zone.equityAccountId]
        }!!.name?.value
    }

    fun createIdentity(name: String?) {
        fun onError() {
            gameActionListeners.forEach {
                it.onCreateIdentityMemberError(name)
            }
        }
        serverConnection.sendZoneCommand(
                zoneId!!,
                WsProtocol.ZoneCommand.newBuilder()
                        .setCreateMemberCommand(
                                WsProtocol.ZoneCommand.CreateMemberCommand.newBuilder()
                                        .addOwnerPublicKeys(serverConnection.clientKey)
                                        .setName(StringValue.newBuilder().setValue(name))
                        )
                        .build()
        ).subscribe({ zoneResponse ->
            when (zoneResponse.createMemberResponse.resultCase) {
                WsProtocol.ZoneResponse.CreateMemberResponse.ResultCase.RESULT_NOT_SET -> {
                }
                WsProtocol.ZoneResponse.CreateMemberResponse.ResultCase.ERRORS ->
                    onError()
                WsProtocol.ZoneResponse.CreateMemberResponse.ResultCase.SUCCESS -> {
                    createAccount(zoneResponse.createMemberResponse.success.member)
                }
            }
        }, {
            onError()
        })
    }

    fun changeIdentityName(identity: Identity, name: String?) {
        fun onError() {
            gameActionListeners.forEach {
                it.onChangeIdentityNameError(name)
            }
        }

        val member = state!!.zone.membersList.find {
            it.id == identity.memberId
        }!!
        serverConnection.sendZoneCommand(
                zoneId!!,
                WsProtocol.ZoneCommand.newBuilder()
                        .setUpdateMemberCommand(
                                WsProtocol.ZoneCommand.UpdateMemberCommand.newBuilder()
                                        .setMember(
                                                member.toBuilder()
                                                        .setName(
                                                                StringValue.newBuilder()
                                                                        .setValue(name)
                                                        )
                                        )
                        )
                        .build()
        ).subscribe({ zoneResponse ->
            when (zoneResponse.updateMemberResponse.resultCase) {
                WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.RESULT_NOT_SET -> {
                }
                WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.ERRORS ->
                    onError()
                WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.SUCCESS -> {
                }
            }
        }, {
            onError()
        })
    }

    fun isPublicKeyConnectedAndImplicitlyValid(publicKey: ByteString): Boolean {
        return state!!.connectedClients.values.contains(publicKey)
    }

    fun transferIdentity(identity: Identity, toPublicKey: ByteString) {
        fun onError() {
            gameActionListeners.forEach {
                it.onTransferIdentityError(identity.name)
            }
        }

        val member = state!!.zone.membersList.find {
            it.id == identity.memberId
        }!!
        serverConnection.sendZoneCommand(
                zoneId!!,
                WsProtocol.ZoneCommand.newBuilder()
                        .setUpdateMemberCommand(
                                WsProtocol.ZoneCommand.UpdateMemberCommand.newBuilder()
                                        .setMember(
                                                member.toBuilder()
                                                        .clearOwnerPublicKeys()
                                                        .addAllOwnerPublicKeys(
                                                                member.ownerPublicKeysList
                                                                        .minusElement(
                                                                                serverConnection
                                                                                        .clientKey
                                                                        )
                                                                        .plusElement(
                                                                                toPublicKey
                                                                        )
                                                        )
                                        )
                        )
                        .build()
        ).subscribe({ zoneResponse ->
            when (zoneResponse.updateMemberResponse.resultCase) {
                WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.RESULT_NOT_SET -> {
                }
                WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.ERRORS ->
                    onError()
                WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.SUCCESS -> {
                }
            }
        }, {
            onError()
        })
    }

    fun deleteIdentity(identity: Identity) {
        fun onError() {
            gameActionListeners.forEach {
                it.onDeleteIdentityError(identity.name)
            }
        }

        val member = state!!.zone.membersList.find {
            it.id == identity.memberId
        }!!
        val metadata = member.metadata.toBuilder()
                .putFields(HIDDEN_FLAG_KEY, Value.newBuilder().setBoolValue(true).build())
        serverConnection.sendZoneCommand(
                zoneId!!,
                WsProtocol.ZoneCommand.newBuilder()
                        .setUpdateMemberCommand(
                                WsProtocol.ZoneCommand.UpdateMemberCommand.newBuilder()
                                        .setMember(member.toBuilder().setMetadata(metadata))
                        )
                        .build()
        ).subscribe({ zoneResponse ->
            when (zoneResponse.updateMemberResponse.resultCase) {
                WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.RESULT_NOT_SET -> {
                }
                WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.ERRORS ->
                    onError()
                WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.SUCCESS -> {
                }
            }
        }, {
            onError()
        })
    }

    fun restoreIdentity(identity: Identity) {
        fun onError() {
            gameActionListeners.forEach {
                it.onRestoreIdentityError(identity.name)
            }
        }

        val member = state!!.zone.membersList.find {
            it.id == identity.memberId
        }!!
        val metadata = member.metadata.toBuilder()
                .removeFields(HIDDEN_FLAG_KEY)
        serverConnection.sendZoneCommand(
                zoneId!!,
                WsProtocol.ZoneCommand.newBuilder()
                        .setUpdateMemberCommand(
                                WsProtocol.ZoneCommand.UpdateMemberCommand.newBuilder()
                                        .setMember(member.toBuilder().setMetadata(metadata))
                        )
                        .build()
        ).subscribe({ zoneResponse ->
            when (zoneResponse.updateMemberResponse.resultCase) {
                WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.RESULT_NOT_SET -> {
                }
                WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.ERRORS ->
                    onError()
                WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.SUCCESS -> {
                }
            }
        }, {
            onError()
        })
    }

    fun transferToPlayer(actingAs: Identity,
                         from: Identity,
                         to: Player,
                         value: BigDecimal
    ) {
        fun onError() {
            gameActionListeners.forEach {
                it.onTransferToPlayerError(to.name)
            }
        }
        serverConnection.sendZoneCommand(
                zoneId!!,
                WsProtocol.ZoneCommand.newBuilder()
                        .setAddTransactionCommand(
                                WsProtocol.ZoneCommand.AddTransactionCommand.newBuilder()
                                        .setActingAs(actingAs.memberId)
                                        .setFrom(from.accountId)
                                        .setTo(to.accountId)
                                        .setValue(value.toString())
                        )
                        .build()
        ).subscribe({ zoneResponse ->
            when (zoneResponse.addTransactionResponse.resultCase) {
                WsProtocol.ZoneResponse.AddTransactionResponse.ResultCase.RESULT_NOT_SET -> {
                }
                WsProtocol.ZoneResponse.AddTransactionResponse.ResultCase.ERRORS ->
                    onError()
                WsProtocol.ZoneResponse.AddTransactionResponse.ResultCase.SUCCESS -> {
                }
            }
        }, {
            onError()
        })
    }

}