package com.dhpcs.liquidity

import com.dhpcs.liquidity.BoardGame.Companion.JoinState.*
import com.dhpcs.liquidity.proto.model.Model
import com.dhpcs.liquidity.proto.ws.protocol.WsProtocol
import com.google.protobuf.ByteString
import com.google.protobuf.StringValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import java.io.Serializable
import java.math.BigDecimal
import java.util.Currency
import kotlin.collections.component1
import kotlin.collections.component2

@Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
class BoardGame private constructor(
        private val serverConnection: ServerConnection,
        private val gameDatabase: GameDatabase,
        private val _currency: Currency?,
        private val _gameName: String?,
        private val bankMemberName: String?,
        _zoneId: String?,
        _gameId: Long?
) {

    companion object {

        interface GameDatabase {

            fun insertGame(zoneId: String, created: Long, expires: Long, name: String?): Long

            fun checkAndUpdateGame(zoneId: String, name: String?): Long?

            fun updateGameName(gameId: Long, name: String?)

        }

        enum class JoinState {
            QUIT,
            FAILED,
            ERROR,
            CREATING,
            JOINING,
            JOINED
        }

        data class Player(
                val zoneId: String,
                val memberId: String,
                val ownerPublicKey: ByteString,
                val name: String?,
                val isHidden: Boolean,
                val accountId: String,
                val balance: BigDecimal,
                val currency: String?,
                val isBanker: Boolean,
                val isConnected: Boolean
        ) : Serializable

        data class Identity(
                val zoneId: String,
                val memberId: String,
                val ownerPublicKey: ByteString,
                val name: String?,
                val isHidden: Boolean,
                val accountId: String,
                val balance: BigDecimal,
                val currency: String?,
                val isBanker: Boolean
        ) : Serializable

        data class Transfer(
                val fromAccountId: String,
                val fromPlayer: Player?,
                val toAccountId: String,
                val toPlayer: Player?,
                val transactionId: String,
                val created: Long,
                val value: BigDecimal,
                val currency: String?
        ) : Serializable

        class JoinRequestToken

        const val MAXIMUM_TAG_LENGTH = 160

        private const val CURRENCY_CODE_KEY = "currency"
        private const val HIDDEN_FLAG_KEY = "hidden"

        private class State(
                val zone: Model.Zone,
                val connectedClients: Map<String, ByteString>,
                val balances: Map<String, BigDecimal>,
                val currency: String?,
                val identities: Map<String, Identity>,
                val hiddenIdentities: Map<String, Identity>,
                val players: Map<String, Player>,
                val transfers: Map<String, Transfer>
        )

        private var instances: Map<String, BoardGame> = emptyMap()

        fun getInstance(zoneId: String): BoardGame? = instances[zoneId]

        fun isGameNameValid(name: CharSequence): Boolean = isTagValid(name)

        fun isTagValid(tag: CharSequence): Boolean {
            return tag.isNotEmpty() && tag.length <= MAXIMUM_TAG_LENGTH
        }

        private fun currencyFromMetadata(metadata: Struct?): String? {
            return metadata?.fieldsMap?.get((CURRENCY_CODE_KEY))?.stringValue
        }

        private fun isHidden(member: Model.Member): Boolean {
            return if (!member.hasMetadata()) {
                false
            } else {
                val hidden = member.metadata.fieldsMap[HIDDEN_FLAG_KEY]
                hidden?.boolValue ?: false
            }
        }

    }

    constructor (serverConnection: ServerConnection,
                 gameDatabase: GameDatabase,
                 currency: Currency,
                 gameName: String,
                 bankMemberName: String
    ) : this(
            serverConnection,
            gameDatabase,
            currency,
            gameName,
            bankMemberName,
            null,
            null
    )

    constructor(serverConnection: ServerConnection,
                gameDatabase: GameDatabase,
                zoneId: String,
                gameId: Long?
    ) : this(serverConnection,
            gameDatabase,
            null,
            null,
            null,
            zoneId,
            gameId
    )

    private var joinRequestTokens: Set<JoinRequestToken> = emptySet()

    var zoneId = _zoneId
        private set

    private var gameId: Single<Long>? = if (_gameId == null) null else Single.just(_gameId)

    private val joinStateSubject = BehaviorSubject.createDefault(QUIT)
    val joinState: JoinState get() = joinStateSubject.value!!
    val joinStateObservable: Observable<JoinState> = joinStateSubject

    private var state: State? = null

    val currency get() = state!!.currency

    private val createGameErrorSubject = PublishSubject.create<Unit>()
    val createGameErrorObservable: Observable<Unit> = createGameErrorSubject

    private val gameNameSubject = BehaviorSubject.createDefault("")
    val gameNameObservable: Observable<String> = gameNameSubject
    val gameName: String get() = gameNameSubject.value!!

    private val identityRequiredSubject = PublishSubject.create<Unit>()
    val identityRequiredObservable: Observable<Unit> = identityRequiredSubject

    private val addedIdentitiesSubject = PublishSubject.create<Identity>()
    val addedIdentitiesObservable: Observable<Identity> = addedIdentitiesSubject

    private val addedTransfersSubject = PublishSubject.create<Transfer>()
    val addedTransfersObservable: Observable<Transfer> = addedTransfersSubject

    private val playersSubject = BehaviorSubject.createDefault(emptyMap<String, Player>())
    val playersObservable: Observable<Map<String, Player>> = playersSubject
    val players get() = playersSubject.value

    private val identitiesSubject = BehaviorSubject.createDefault(emptyMap<String, Identity>())
    val identitiesObservable: Observable<Map<String, Identity>> = identitiesSubject
    val identities get() = identitiesSubject.value

    private val hiddenIdentitiesSubject =
            BehaviorSubject.createDefault(emptyMap<String, Identity>())
    val hiddenIdentities get() = hiddenIdentitiesSubject.value

    private val transfersSubject = BehaviorSubject.createDefault(emptyMap<String, Transfer>())
    val transfersObservable: Observable<Map<String, Transfer>> = transfersSubject
    val transfers get() = transfersSubject.value

    fun requestJoin(token: JoinRequestToken) {
        if (joinRequestTokens.isEmpty()) {
            val zoneId = zoneId
            if (zoneId != null && !instances.contains(zoneId)) {
                instances += Pair(zoneId, this)
            }
            if (zoneId == null) {
                createAndThenJoinZone(_currency!!, _gameName!!)
            } else {
                join(zoneId)
            }
        }
        joinRequestTokens += token
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

    private var createZoneDisposable: Disposable? = null

    private fun createAndThenJoinZone(currency: Currency, name: String) {
        state = null
        joinStateSubject.onNext(CREATING)
        val metadata = Struct.newBuilder()
                .putFields(
                        CURRENCY_CODE_KEY,
                        Value.newBuilder().setStringValue(currency.currencyCode).build()
                )
        createZoneDisposable = serverConnection.createZone(
                WsProtocol.ZoneCommand.CreateZoneCommand.newBuilder()
                        .setEquityOwnerPublicKey(serverConnection.clientKey)
                        .setEquityOwnerName(StringValue.newBuilder().setValue(bankMemberName))
                        .setName(StringValue.newBuilder().setValue(name))
                        .setMetadata(metadata)
                        .build()
        ).subscribe({ zoneResponse ->
            when (zoneResponse.createZoneResponse.resultCase) {
                WsProtocol.ZoneResponse.CreateZoneResponse.ResultCase.RESULT_NOT_SET ->
                    createGameErrorSubject.onNext(Unit)
                WsProtocol.ZoneResponse.CreateZoneResponse.ResultCase.ERRORS ->
                    createGameErrorSubject.onNext(Unit)
                WsProtocol.ZoneResponse.CreateZoneResponse.ResultCase.SUCCESS -> {
                    val zone = zoneResponse.createZoneResponse.success.zone!!
                    instances += Pair(zone.id, this)
                    zoneId = zone.id
                    join(zone.id)
                }
            }
        }, { _ ->
            createGameErrorSubject.onNext(Unit)
        })
    }

    private var zoneNotificationDisposable: Disposable? = null

    private fun join(zoneId: String) {
        state = null
        joinStateSubject.onNext(JOINING)
        zoneNotificationDisposable = serverConnection
                .zoneNotifications(zoneId)
                .subscribe(
                        {
                            if (joinState == JOINING || joinState == JOINED) {
                                onZoneNotificationReceived(zoneId, it)
                            }
                        },
                        {
                            joinStateSubject.onNext(FAILED)
                        },
                        {
                            if (joinState == JOINING) {
                                joinStateSubject.onNext(ERROR)
                            } else {
                                joinStateSubject.onNext(FAILED)
                            }
                        }
                )
    }

    private fun quit() {
        createZoneDisposable?.dispose()
        createZoneDisposable = null
        zoneNotificationDisposable?.dispose()
        zoneNotificationDisposable = null
        joinStateSubject.onNext(QUIT)
    }

    private fun onZoneNotificationReceived(
            zoneId: String,
            zoneNotification: WsProtocol.ZoneNotification
    ) {
        fun updateState(
                zone: Model.Zone,
                connectedClients: Map<String, ByteString>,
                balances: Map<String, BigDecimal>,
                currency: String?
        ): State {
            val memberIdsToAccountIds = zone.accountsList.asSequence().filter {
                it.ownerMemberIdsCount == 1
            }.groupBy {
                it.getOwnerMemberIds(0)
            }.filterValues {
                it.size == 1
            }.mapValues {
                it.value.first().id
            }
            val accountIdsToMemberIds = memberIdsToAccountIds
                    .map { (accountId, memberId) ->
                        Pair(memberId, accountId)
                    }
                    .toMap()
            val allIdentities = memberIdsToAccountIds.filterKeys { memberId ->
                val member = zone.membersList.find { it.id == memberId }!!
                member.ownerPublicKeysCount == 1 &&
                        member.getOwnerPublicKeys(0) == serverConnection.clientKey
            }.mapValues { (memberId, accountId) ->
                val member = zone.membersList.find { it.id == memberId }!!
                Identity(
                        zoneId,
                        member.id,
                        member.getOwnerPublicKeys(0),
                        if (!member.hasName()) null else member.name.value,
                        isHidden(member),
                        zone.accountsList.find { it.id == accountId }!!.id,
                        balances.getOrElse(accountId) { BigDecimal.ZERO },
                        currency,
                        accountId == zone.equityAccountId
                )
            }
            val identities = allIdentities.filterValues { !it.isHidden }
            val hiddenIdentities = allIdentities.filterValues { it.isHidden }
            val allPlayers = memberIdsToAccountIds.filterKeys { memberId ->
                zone.membersList.find { it.id == memberId }!!.ownerPublicKeysCount == 1
            }.mapValues { (memberId, accountId) ->
                val member = zone.membersList.find { it.id == memberId }!!
                Player(
                        zoneId,
                        member.id,
                        member.getOwnerPublicKeys(0),
                        if (!member.hasName()) null else member.name.value,
                        isHidden(member),
                        zone.accountsList.find { it.id == accountId }!!.id,
                        balances.getOrElse(accountId) { BigDecimal.ZERO },
                        currency,
                        accountId == zone.equityAccountId,
                        connectedClients.values.any { member.ownerPublicKeysList.contains(it) }
                )
            }
            val players = allPlayers.filterValues { !it.isHidden }
            val transfers = zone.transactionsList.asSequence().map { transaction ->
                val fromAccount = zone.accountsList.find { it.id == transaction.from }!!
                val fromMemberId = accountIdsToMemberIds[transaction.from]
                val toAccount = zone.accountsList.find { it.id == transaction.to }!!
                val toMemberId = accountIdsToMemberIds[transaction.to]
                Transfer(
                        fromAccount.id,
                        players[fromMemberId]!!,
                        toAccount.id,
                        players[toMemberId]!!,
                        transaction.id,
                        transaction.created,
                        BigDecimal(transaction.value),
                        currency
                )
            }.associateBy { it.transactionId }
            return State(
                    zone,
                    connectedClients,
                    balances,
                    currency,
                    identities,
                    hiddenIdentities,
                    players,
                    transfers
            )
        }

        fun dispatchUpdates(oldState: State?, newState: State) {
            if (oldState == null) {
                joinStateSubject.onNext(JOINED)
            }
            val oldName = if (oldState?.zone?.hasName() == true) oldState.zone.name.value else null
            val newName = if (newState.zone.hasName()) newState.zone.name.value else null
            if (oldState == null || newName != oldName) {
                gameNameSubject.onNext(newName ?: "")
                gameId?.subscribeOn(Schedulers.io())?.subscribe { gameId ->
                    gameDatabase.updateGameName(gameId, newName)
                }
            }
            if (newState.identities != oldState?.identities) {
                val addedIdentities = newState.identities - oldState?.identities
                if (oldState != null) {
                    addedIdentities.values.forEach {
                        addedIdentitiesSubject.onNext(it)
                    }
                }
                identitiesSubject.onNext(newState.identities)
                hiddenIdentitiesSubject.onNext(newState.hiddenIdentities)
            }
            if (newState.players != oldState?.players) {
                playersSubject.onNext(newState.players)
            }
            if (newState.transfers != oldState?.transfers) {
                val addedTransfers = newState.transfers - oldState?.transfers
                if (oldState != null) {
                    addedTransfers.values.forEach {
                        addedTransfersSubject.onNext(it)
                    }
                }
                transfersSubject.onNext(newState.transfers)
            }

            if (oldState == null &&
                    !(newState.identities + newState.hiddenIdentities).values.any {
                        it.accountId != newState.zone.equityAccountId
                    }) {
                identityRequiredSubject.onNext(Unit)
            }

            if (gameId == null) {
                gameId = Single.fromCallable {
                    // This is in case a user rejoins a game by scanning its code again rather
                    // than by clicking its list item.
                    gameDatabase.checkAndUpdateGame(zoneId, newName) ?: gameDatabase.insertGame(
                            zoneId,
                            newState.zone.created,
                            newState.zone.expires,
                            newName
                    )
                }.subscribeOn(Schedulers.io()).cache()
                gameId!!.subscribe()
            } else {
                gameId!!.subscribeOn(Schedulers.io()).subscribe { gameId ->
                    gameDatabase.updateGameName(gameId, newName)
                }
            }
        }

        val newState = when (zoneNotification.zoneNotificationCase) {
            WsProtocol.ZoneNotification.ZoneNotificationCase.ZONENOTIFICATION_NOT_SET ->
                null
            WsProtocol.ZoneNotification.ZoneNotificationCase.CLIENT_JOINED_ZONE_NOTIFICATION -> {
                val connectionId = zoneNotification.clientJoinedZoneNotification.connectionId
                val publicKey = zoneNotification.clientJoinedZoneNotification.publicKey
                val connectedClients = state!!.connectedClients + Pair(connectionId, publicKey)
                updateState(
                        state!!.zone,
                        connectedClients,
                        state!!.balances,
                        state!!.currency
                )
            }
            WsProtocol.ZoneNotification.ZoneNotificationCase.CLIENT_QUIT_ZONE_NOTIFICATION -> {
                val connectionId = zoneNotification.clientQuitZoneNotification.connectionId
                val connectedClients = state!!.connectedClients - connectionId
                updateState(
                        state!!.zone,
                        connectedClients,
                        state!!.balances,
                        state!!.currency
                )
            }
            WsProtocol.ZoneNotification.ZoneNotificationCase.ZONE_NAME_CHANGED_NOTIFICATION -> {
                val name = zoneNotification.zoneNameChangedNotification.name
                val zone = state!!.zone.toBuilder()
                        .setName(name)
                        .build()
                updateState(
                        zone,
                        state!!.connectedClients,
                        state!!.balances,
                        state!!.currency
                )
            }
            WsProtocol.ZoneNotification.ZoneNotificationCase.MEMBER_CREATED_NOTIFICATION -> {
                val member = zoneNotification.memberCreatedNotification.member
                val zone = state!!.zone.toBuilder()
                        .addMembers(member)
                        .build()
                updateState(
                        zone,
                        state!!.connectedClients,
                        state!!.balances,
                        state!!.currency
                )
            }
            WsProtocol.ZoneNotification.ZoneNotificationCase.MEMBER_UPDATED_NOTIFICATION -> {
                val member = zoneNotification.memberUpdatedNotification.member
                val zone = state!!.zone.toBuilder()
                        .removeMembers(
                                state!!.zone.membersList.indexOfFirst {
                                    it.id == member.id
                                }
                        )
                        .addMembers(member)
                        .build()
                updateState(
                        zone,
                        state!!.connectedClients,
                        state!!.balances,
                        state!!.currency
                )
            }
            WsProtocol.ZoneNotification.ZoneNotificationCase.ACCOUNT_CREATED_NOTIFICATION -> {
                val account = zoneNotification.accountCreatedNotification.account
                val zone = state!!.zone.toBuilder()
                        .addAccounts(account)
                        .build()
                updateState(
                        zone,
                        state!!.connectedClients,
                        state!!.balances,
                        state!!.currency
                )
            }
            WsProtocol.ZoneNotification.ZoneNotificationCase.ACCOUNT_UPDATED_NOTIFICATION -> {
                val account = zoneNotification.accountUpdatedNotification.account
                val zone = state!!.zone.toBuilder()
                        .removeAccounts(
                                state!!.zone.accountsList.indexOfFirst {
                                    it.id == account.id
                                }
                        )
                        .addAccounts(account)
                        .build()
                updateState(
                        zone,
                        state!!.connectedClients,
                        state!!.balances,
                        state!!.currency
                )
            }
            WsProtocol.ZoneNotification.ZoneNotificationCase.TRANSACTION_ADDED_NOTIFICATION -> {
                val transaction = zoneNotification.transactionAddedNotification.transaction
                val zone = state!!.zone.toBuilder()
                        .addTransactions(transaction)
                        .build()
                val balances = state!!.balances +
                        Pair(
                                transaction.from!!,
                                state!!.balances.getOrElse(transaction.from) { BigDecimal.ZERO } -
                                        BigDecimal(transaction.value)
                        ) +
                        Pair(
                                transaction.to!!,
                                state!!.balances.getOrElse(transaction.to) { BigDecimal.ZERO } +
                                        BigDecimal(transaction.value)
                        )
                updateState(
                        zone,
                        state!!.connectedClients,
                        balances,
                        state!!.currency
                )
            }
            WsProtocol.ZoneNotification.ZoneNotificationCase.ZONE_STATE_NOTIFICATION -> {
                val zone = zoneNotification.zoneStateNotification.zone
                val connectedClients = zoneNotification.zoneStateNotification.connectedClientsMap
                val balances = zone.transactionsList
                        .fold(emptyMap<String, BigDecimal>()) { acc, transaction ->
                            acc +
                                    Pair(
                                            transaction.from,
                                            acc.getOrElse(transaction.from) { BigDecimal.ZERO } -
                                                    BigDecimal(transaction.value)
                                    ) +
                                    Pair(
                                            transaction.to,
                                            acc.getOrElse(transaction.to) { BigDecimal.ZERO } +
                                                    BigDecimal(transaction.value)
                                    )
                        }
                val currency = currencyFromMetadata(zone.metadata)
                updateState(
                        zone,
                        connectedClients,
                        balances,
                        currency
                )
            }
            WsProtocol.ZoneNotification.ZoneNotificationCase.PING_NOTIFICATION ->
                null
        }
        if (newState != null) {
            dispatchUpdates(state, newState)
            state = newState
        }
    }

    private fun createAccount(ownerMember: Model.Member): Single<Model.Account> {
        return Single.create<Model.Account> { singleEmitter ->
            serverConnection.execZoneCommand(
                    zoneId!!,
                    WsProtocol.ZoneCommand.newBuilder()
                            .setCreateAccountCommand(
                                    WsProtocol.ZoneCommand.CreateAccountCommand.newBuilder()
                                            .addOwnerMemberIds(ownerMember.id)
                            )
                            .build()
            ).subscribe({ zoneResponse ->
                when (zoneResponse.createAccountResponse.resultCase) {
                    WsProtocol.ZoneResponse.CreateAccountResponse.ResultCase.RESULT_NOT_SET ->
                        singleEmitter.onError(IllegalArgumentException(
                                zoneResponse.createAccountResponse.resultCase.name
                        ))
                    WsProtocol.ZoneResponse.CreateAccountResponse.ResultCase.ERRORS ->
                        singleEmitter.onError(IllegalArgumentException(
                                zoneResponse.createAccountResponse.errors.toString()
                        ))
                    WsProtocol.ZoneResponse.CreateAccountResponse.ResultCase.SUCCESS ->
                        singleEmitter.onSuccess(zoneResponse.createAccountResponse.success.account)
                }
            }, { error ->
                singleEmitter.onError(error)
            })
        }
    }

    fun changeGameName(name: String): Single<Unit> {
        return Single.create<Unit> { singleEmitter ->
            serverConnection.execZoneCommand(
                    zoneId!!,
                    WsProtocol.ZoneCommand.newBuilder()
                            .setChangeZoneNameCommand(
                                    WsProtocol.ZoneCommand.ChangeZoneNameCommand.newBuilder()
                                            .setName(StringValue.newBuilder().setValue(name))
                            )
                            .build()
            ).subscribe({ zoneResponse ->
                when (zoneResponse.changeZoneNameResponse.resultCase) {
                    WsProtocol.ZoneResponse.ChangeZoneNameResponse.ResultCase.RESULT_NOT_SET ->
                        singleEmitter.onError(IllegalArgumentException(
                                zoneResponse.changeZoneNameResponse.resultCase.name
                        ))
                    WsProtocol.ZoneResponse.ChangeZoneNameResponse.ResultCase.ERRORS ->
                        singleEmitter.onError(IllegalArgumentException(
                                zoneResponse.changeZoneNameResponse.errors.toString()
                        ))
                    WsProtocol.ZoneResponse.ChangeZoneNameResponse.ResultCase.SUCCESS ->
                        singleEmitter.onSuccess(Unit)
                }
            }, { error ->
                singleEmitter.onError(error)
            })
        }
    }

    fun isIdentityNameValid(name: CharSequence): Boolean {
        val equityMemberId = state!!.zone.accountsList.find {
            it.id == state!!.zone.equityAccountId && it.ownerMemberIdsCount == 1
        }?.getOwnerMemberIds(0)
        return isTagValid(name) &&
                name.toString() != state!!.zone.membersList.find {
            it.id == equityMemberId
        }!!.name?.value
    }

    fun createIdentity(name: String): Single<Model.Member> {
        val partiallyCreatedIdentity = state!!.zone.membersList.find { member ->
            member.ownerPublicKeysCount == 1 &&
                    member.getOwnerPublicKeys(0) ==
                    serverConnection.clientKey &&
                    member.hasName() && member.name.value == name &&
                    !state!!.zone.accountsList.any { account ->
                        account.ownerMemberIdsCount == 1 &&
                                account.getOwnerMemberIds(0) == member.id
                    }
        }
        val member = if (partiallyCreatedIdentity != null) {
            Single.just(partiallyCreatedIdentity)
        } else {
            Single.create<Model.Member> { singleEmitter ->
                serverConnection.execZoneCommand(
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
                        WsProtocol.ZoneResponse.CreateMemberResponse.ResultCase.RESULT_NOT_SET ->
                            singleEmitter.onError(IllegalArgumentException(
                                    zoneResponse.createMemberResponse.resultCase.name
                            ))
                        WsProtocol.ZoneResponse.CreateMemberResponse.ResultCase.ERRORS ->
                            singleEmitter.onError(IllegalArgumentException(
                                    zoneResponse.createMemberResponse.errors.toString()
                            ))
                        WsProtocol.ZoneResponse.CreateMemberResponse.ResultCase.SUCCESS ->
                            singleEmitter.onSuccess(zoneResponse.createMemberResponse.success.member)
                    }
                }, { error ->
                    singleEmitter.onError(error)
                })
            }
        }
        return member.flatMap { createAccount(it).map { _ -> it } }
    }

    fun changeIdentityName(identity: Identity, name: String): Single<Unit> {
        val member = state!!.zone.membersList.find {
            it.id == identity.memberId
        }!!
        return Single.create<Unit> { singleEmitter ->
            serverConnection.execZoneCommand(
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
                    WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.RESULT_NOT_SET ->
                        singleEmitter.onError(IllegalArgumentException(
                                zoneResponse.updateMemberResponse.resultCase.name
                        ))
                    WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.ERRORS ->
                        singleEmitter.onError(IllegalArgumentException(
                                zoneResponse.updateMemberResponse.errors.toString()
                        ))
                    WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.SUCCESS ->
                        singleEmitter.onSuccess(Unit)
                }
            }, { error ->
                singleEmitter.onError(error)
            })
        }
    }

    fun isPublicKeyConnectedAndImplicitlyValid(publicKey: ByteString): Boolean {
        return state!!.connectedClients.values.contains(publicKey)
    }

    fun transferIdentity(identity: Identity, toPublicKey: ByteString): Single<Unit> {
        val member = state!!.zone.membersList.find {
            it.id == identity.memberId
        }!!
        return Single.create<Unit> { singleEmitter ->
            serverConnection.execZoneCommand(
                    zoneId!!,
                    WsProtocol.ZoneCommand
                            .newBuilder()
                            .setUpdateMemberCommand(
                                    WsProtocol.ZoneCommand.UpdateMemberCommand.newBuilder()
                                            .setMember(member.toBuilder()
                                                    .clearOwnerPublicKeys()
                                                    .addAllOwnerPublicKeys(
                                                            member.ownerPublicKeysList
                                                                    .asSequence()
                                                                    .minusElement(
                                                                            serverConnection
                                                                                    .clientKey
                                                                    )
                                                                    .plusElement(
                                                                            toPublicKey
                                                                    )
                                                                    .toList()
                                                    )
                                            )
                            )
                            .build()
            ).subscribe({ zoneResponse ->
                when (zoneResponse.updateMemberResponse.resultCase) {
                    WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.RESULT_NOT_SET ->
                        singleEmitter.onError(IllegalArgumentException(
                                zoneResponse.updateMemberResponse.resultCase.name
                        ))
                    WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.ERRORS ->
                        singleEmitter.onError(IllegalArgumentException(
                                zoneResponse.updateMemberResponse.errors.toString()
                        ))
                    WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.SUCCESS ->
                        singleEmitter.onSuccess(Unit)
                }
            }, { error ->
                singleEmitter.onError(error)
            })
        }
    }

    fun deleteIdentity(identity: Identity): Single<Unit> {
        val member = state!!.zone.membersList.find {
            it.id == identity.memberId
        }!!
        val metadata = member.metadata.toBuilder()
                .putFields(HIDDEN_FLAG_KEY, Value.newBuilder().setBoolValue(true).build())
        return Single.create<Unit> { singleEmitter ->
            serverConnection.execZoneCommand(
                    zoneId!!,
                    WsProtocol.ZoneCommand.newBuilder()
                            .setUpdateMemberCommand(
                                    WsProtocol.ZoneCommand.UpdateMemberCommand.newBuilder()
                                            .setMember(member.toBuilder().setMetadata(metadata))
                            )
                            .build()
            ).subscribe({ zoneResponse ->
                when (zoneResponse.updateMemberResponse.resultCase) {
                    WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.RESULT_NOT_SET ->
                        singleEmitter.onError(IllegalArgumentException(
                                zoneResponse.updateMemberResponse.resultCase.name
                        ))
                    WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.ERRORS ->
                        singleEmitter.onError(IllegalArgumentException(
                                zoneResponse.updateMemberResponse.errors.toString()
                        ))
                    WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.SUCCESS ->
                        singleEmitter.onSuccess(Unit)
                }
            }, { error ->
                singleEmitter.onError(error)
            })
        }
    }

    fun restoreIdentity(identity: Identity): Single<Unit> {
        val member = state!!.zone.membersList.find {
            it.id == identity.memberId
        }!!
        val metadata = member.metadata.toBuilder()
                .removeFields(HIDDEN_FLAG_KEY)
        return Single.create<Unit> { singleEmitter ->
            serverConnection.execZoneCommand(
                    zoneId!!,
                    WsProtocol.ZoneCommand.newBuilder()
                            .setUpdateMemberCommand(
                                    WsProtocol.ZoneCommand.UpdateMemberCommand.newBuilder()
                                            .setMember(member.toBuilder().setMetadata(metadata))
                            )
                            .build()
            ).subscribe({ zoneResponse ->
                when (zoneResponse.updateMemberResponse.resultCase) {
                    WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.RESULT_NOT_SET ->
                        singleEmitter.onError(IllegalArgumentException(
                                zoneResponse.updateMemberResponse.resultCase.name
                        ))
                    WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.ERRORS ->
                        singleEmitter.onError(IllegalArgumentException(
                                zoneResponse.updateMemberResponse.errors.toString()
                        ))
                    WsProtocol.ZoneResponse.UpdateMemberResponse.ResultCase.SUCCESS ->
                        singleEmitter.onSuccess(Unit)
                }
            }, { error ->
                singleEmitter.onError(error)
            })
        }
    }

    fun transferToPlayer(actingAs: Identity,
                         from: Identity,
                         to: Player,
                         value: BigDecimal
    ): Single<Unit> {
        return Single.create<Unit> { singleEmitter ->
            serverConnection.execZoneCommand(
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
                    WsProtocol.ZoneResponse.AddTransactionResponse.ResultCase.RESULT_NOT_SET ->
                        singleEmitter.onError(IllegalArgumentException(
                                zoneResponse.updateMemberResponse.resultCase.name
                        ))
                    WsProtocol.ZoneResponse.AddTransactionResponse.ResultCase.ERRORS ->
                        singleEmitter.onError(IllegalArgumentException(
                                zoneResponse.updateMemberResponse.errors.toString()
                        ))
                    WsProtocol.ZoneResponse.AddTransactionResponse.ResultCase.SUCCESS ->
                        singleEmitter.onSuccess(Unit)
                }
            }, { error ->
                singleEmitter.onError(error)
            })
        }
    }

}
