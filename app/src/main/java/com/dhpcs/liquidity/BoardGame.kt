package com.dhpcs.liquidity

import com.dhpcs.liquidity.BoardGame.Companion.JoinState.*
import com.dhpcs.liquidity.proto.grpc.protocol.GrpcProtocol
import com.dhpcs.liquidity.proto.model.Model
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
import java.math.BigDecimal
import java.util.*

class BoardGame constructor(
        private val serverConnection: ServerConnection,
        private val gameDatabase: GameDatabase
) {

    companion object {

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
                val currency: String,
                val isBanker: Boolean,
                val isConnected: Boolean
        )

        data class Identity(
                val zoneId: String,
                val memberId: String,
                val ownerPublicKey: ByteString,
                val name: String?,
                val isHidden: Boolean,
                val accountId: String,
                val balance: BigDecimal,
                val currency: String,
                val isBanker: Boolean
        )

        data class Transfer(
                val fromAccountId: String,
                val fromPlayer: Player?,
                val toAccountId: String,
                val toPlayer: Player?,
                val transactionId: String,
                val created: Long,
                val value: BigDecimal,
                val currency: String
        )

        const val MAXIMUM_TAG_LENGTH = 160

        private const val CURRENCY_CODE_KEY = "currency"
        private const val HIDDEN_FLAG_KEY = "hidden"

        private class State(
                val zone: Model.Zone,
                val connectedClients: Map<String, ByteString>,
                val balances: Map<String, BigDecimal>,
                val currency: String,
                val identities: Map<String, Identity>,
                val hiddenIdentities: Map<String, Identity>,
                val players: Map<String, Player>,
                val transfers: List<Transfer>
        )

        fun isGameNameValid(name: CharSequence): Boolean = isTagValid(name)

        private fun isTagValid(tag: CharSequence): Boolean {
            return tag.isNotBlank()
        }

        private fun isHidden(member: Model.Member): Boolean {
            return member.hasMetadata() &&
                    member.metadata.fieldsMap[HIDDEN_FLAG_KEY]?.boolValue ?: false
        }

    }

    private val joinStateSubject = BehaviorSubject.createDefault(QUIT)
    val joinState: JoinState get() = joinStateSubject.value!!
    fun joinStateObservable(): Observable<JoinState> = joinStateSubject

    private var gameId: Single<Long>? = null
    var zoneId: String? = null
        set(value) {
            field = value
            if (subscribers.isNotEmpty()) {
                join()
            }
        }
    private var state: State? = null

    private val currencySubject = BehaviorSubject.createDefault("")
    val currencyObservable: Observable<String> = currencySubject

    private val gameNameSubject = BehaviorSubject.createDefault("")
    val gameNameObservable: Observable<String> = gameNameSubject

    val gameName get() = gameNameSubject.value!!

    private val playersSubject = BehaviorSubject.createDefault(emptyMap<String, Player>())
    val playersObservable: Observable<Map<String, Player>> = playersSubject

    val players get() = playersSubject.value!!

    private val identitiesSubject = BehaviorSubject.createDefault(emptyMap<String, Identity>())
    val identitiesObservable: Observable<Map<String, Identity>> = identitiesSubject

    private val hiddenIdentitiesSubject =
            BehaviorSubject.createDefault(emptyMap<String, Identity>())
    val hiddenIdentities get() = hiddenIdentitiesSubject.value!!

    private val addedIdentitiesSubject = PublishSubject.create<Identity>()
    val addedIdentitiesObservable: Observable<Identity> = addedIdentitiesSubject

    private val identityRequiredSubject = PublishSubject.create<Unit>()
    val identityRequiredObservable: Observable<Unit> = identityRequiredSubject

    private val transfersSubject = BehaviorSubject.createDefault(emptyList<Transfer>())
    val transfersObservable: Observable<List<Transfer>> = transfersSubject

    private val addedTransfersSubject = PublishSubject.create<Transfer>()
    val addedTransfersObservable: Observable<Transfer> = addedTransfersSubject

    fun createGame(name: String, currency: Currency, bankMemberName: String): Single<String> {
        joinStateSubject.onNext(CREATING)
        val metadata = Struct.newBuilder()
                .putFields(
                        CURRENCY_CODE_KEY,
                        Value.newBuilder().setStringValue(currency.currencyCode).build()
                )
        return Single.create<String> { singleEmitter ->
            Single.fromFuture(serverConnection.commandStub.createZone(
                    GrpcProtocol.CreateZoneCommand.newBuilder()
                            .setEquityOwnerPublicKey(serverConnection.clientKey)
                            .setEquityOwnerName(StringValue.newBuilder().setValue(bankMemberName))
                            .setName(StringValue.newBuilder().setValue(name))
                            .setMetadata(metadata)
                            .build()
            )).subscribe({ createZoneResponse ->
                when (createZoneResponse.resultCase!!) {
                    GrpcProtocol.CreateZoneResponse.ResultCase.RESULT_NOT_SET ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                createZoneResponse.resultCase.name
                        ))
                    GrpcProtocol.CreateZoneResponse.ResultCase.ERRORS ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                createZoneResponse.resultCase.name
                        ))
                    GrpcProtocol.CreateZoneResponse.ResultCase.SUCCESS ->
                        singleEmitter.onSuccess(createZoneResponse.success.zone!!.id)
                }
            }, { error ->
                singleEmitter.tryOnError(error)
            })
        }
    }

    private var subscribers: Set<Any> = emptySet()

    fun onActive(subscriber: Any) {
        if (subscribers.isEmpty()) {
            join()
        }
        subscribers = subscribers + subscriber
    }

    fun onInactive(subscriber: Any) {
        subscribers = subscribers - subscriber
        if (subscribers.isEmpty()) {
            quit()
        }
    }

    private var zoneNotificationDisposable: Disposable? = null

    private fun join() {
        if (zoneId != null) {
            joinStateSubject.onNext(JOINING)
            zoneNotificationDisposable = serverConnection
                    .zoneNotifications(zoneId!!)
                    .subscribe(
                            {
                                if (joinState == JOINING || joinState == JOINED) {
                                    handleZoneNotification(it)
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
    }

    private fun quit() {
        zoneNotificationDisposable?.dispose()
        zoneNotificationDisposable = null
        gameNameSubject.onNext("")
        playersSubject.onNext(emptyMap())
        identitiesSubject.onNext(emptyMap())
        hiddenIdentitiesSubject.onNext(emptyMap())
        transfersSubject.onNext(emptyList())
        joinStateSubject.onNext(QUIT)
        state = null
        gameId = null
    }

    private fun handleZoneNotification(zoneNotification: GrpcProtocol.ZoneNotification) {
        fun updateState(
                zone: Model.Zone,
                connectedClients: Map<String, ByteString>,
                balances: Map<String, BigDecimal>,
                currency: String
        ): State {
            val memberIdsToAccountIds = zone.accountsList.filter {
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
                        zone.id,
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
                        zone.id,
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
            val transfers = zone.transactionsList.map { transaction ->
                val fromAccount = zone.accountsList.find { it.id == transaction.from }!!
                val fromMemberId = accountIdsToMemberIds[transaction.from]
                val toAccount = zone.accountsList.find { it.id == transaction.to }!!
                val toMemberId = accountIdsToMemberIds[transaction.to]
                Transfer(
                        fromAccount.id,
                        players[fromMemberId],
                        toAccount.id,
                        players[toMemberId],
                        transaction.id,
                        transaction.created,
                        BigDecimal(transaction.value),
                        currency
                )
            }
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
            val oldCurrency = oldState?.currency
            val newCurrency = newState.currency
            if (oldState == null || newCurrency != oldCurrency) {
                currencySubject.onNext(newCurrency)
            }
            val oldName = if (oldState?.zone?.hasName() == true) oldState.zone.name.value else null
            val newName = if (newState.zone.hasName()) newState.zone.name.value else null
            if (oldState == null || newName != oldName) {
                gameNameSubject.onNext(newName ?: "")
                @Suppress("RedundantLambdaArrow")
                gameId?.subscribeOn(Schedulers.io())?.subscribe { _ ->
                    gameDatabase.checkAndUpdateGame(newState.zone.id, newName)
                }
            }
            if (newState.identities != oldState?.identities) {
                identitiesSubject.onNext(newState.identities)
                hiddenIdentitiesSubject.onNext(newState.hiddenIdentities)
                if (oldState != null) {
                    val addedIdentities = newState.identities - oldState.identities.keys
                    addedIdentities.values.forEach {
                        addedIdentitiesSubject.onNext(it)
                    }
                }
            }
            if (newState.players != oldState?.players) {
                playersSubject.onNext(newState.players)
            }
            if (newState.transfers != oldState?.transfers) {
                transfersSubject.onNext(newState.transfers)
                if (oldState != null) {
                    val addedTransfers =
                            newState.transfers.associateBy { it.transactionId } -
                                    oldState.transfers.associateBy { it.transactionId }.keys
                    addedTransfers.values.forEach {
                        addedTransfersSubject.onNext(it)
                    }
                }
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
                    gameDatabase.checkAndUpdateGame(newState.zone.id, newName)
                            ?: gameDatabase.insertGame(
                                    newState.zone.id,
                                    newState.zone.created,
                                    newState.zone.expires,
                                    newName
                            )
                }.subscribeOn(Schedulers.io()).cache()
                gameId!!.subscribe()
            } else {
                @Suppress("RedundantLambdaArrow")
                gameId!!.subscribeOn(Schedulers.io()).subscribe { _ ->
                    gameDatabase.checkAndUpdateGame(newState.zone.id, newName)
                }
            }
        }

        val newState = when (zoneNotification.sealedValueCase!!) {
            GrpcProtocol.ZoneNotification.SealedValueCase.SEALEDVALUE_NOT_SET ->
                null
            GrpcProtocol.ZoneNotification.SealedValueCase.ERRORS ->
                null
            GrpcProtocol.ZoneNotification.SealedValueCase.ZONE_STATE_NOTIFICATION -> {
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
                val currency = zone.metadata?.fieldsMap?.get((CURRENCY_CODE_KEY))?.stringValue ?: ""
                updateState(
                        zone,
                        connectedClients,
                        balances,
                        currency
                )
            }
            GrpcProtocol.ZoneNotification.SealedValueCase.CLIENT_JOINED_ZONE_NOTIFICATION -> {
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
            GrpcProtocol.ZoneNotification.SealedValueCase.CLIENT_QUIT_ZONE_NOTIFICATION -> {
                val connectionId = zoneNotification.clientQuitZoneNotification.connectionId
                val connectedClients = state!!.connectedClients - connectionId
                updateState(
                        state!!.zone,
                        connectedClients,
                        state!!.balances,
                        state!!.currency
                )
            }
            GrpcProtocol.ZoneNotification.SealedValueCase.ZONE_NAME_CHANGED_NOTIFICATION -> {
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
            GrpcProtocol.ZoneNotification.SealedValueCase.MEMBER_CREATED_NOTIFICATION -> {
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
            GrpcProtocol.ZoneNotification.SealedValueCase.MEMBER_UPDATED_NOTIFICATION -> {
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
            GrpcProtocol.ZoneNotification.SealedValueCase.ACCOUNT_CREATED_NOTIFICATION -> {
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
            GrpcProtocol.ZoneNotification.SealedValueCase.ACCOUNT_UPDATED_NOTIFICATION -> {
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
            GrpcProtocol.ZoneNotification.SealedValueCase.TRANSACTION_ADDED_NOTIFICATION -> {
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
        }
        if (newState != null) {
            dispatchUpdates(state, newState)
            state = newState
        }
    }

    private fun createAccount(zoneId: String, ownerMemberId: String): Single<Model.Account> {
        return Single.create<Model.Account> { singleEmitter ->
            Single.fromFuture(serverConnection.commandStub.createAccount(
                    GrpcProtocol.CreateAccountCommand.newBuilder()
                            .setZoneId(zoneId)
                            .addOwnerMemberIds(ownerMemberId)
                            .build()
            )).subscribe({ createAccountResponse ->
                when (createAccountResponse.resultCase!!) {
                    GrpcProtocol.CreateAccountResponse.ResultCase.RESULT_NOT_SET ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                createAccountResponse.resultCase.name
                        ))
                    GrpcProtocol.CreateAccountResponse.ResultCase.ERRORS ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                createAccountResponse.errors.toString()
                        ))
                    GrpcProtocol.CreateAccountResponse.ResultCase.SUCCESS ->
                        singleEmitter.onSuccess(createAccountResponse.success.account)
                }
            }, { error ->
                singleEmitter.tryOnError(error)
            })
        }
    }

    fun changeGameName(name: String): Single<Unit> {
        return Single.create<Unit> { singleEmitter ->
            Single.fromFuture(serverConnection.commandStub.changeZoneName(
                    GrpcProtocol.ChangeZoneNameCommand.newBuilder()
                            .setZoneId(state!!.zone.id)
                            .setName(StringValue.newBuilder().setValue(name))
                            .build()
            )).subscribe({ changeZoneNameResponse ->
                when (changeZoneNameResponse.resultCase!!) {
                    GrpcProtocol.ChangeZoneNameResponse.ResultCase.RESULT_NOT_SET ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                changeZoneNameResponse.resultCase.name
                        ))
                    GrpcProtocol.ChangeZoneNameResponse.ResultCase.ERRORS ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                changeZoneNameResponse.errors.toString()
                        ))
                    GrpcProtocol.ChangeZoneNameResponse.ResultCase.SUCCESS ->
                        singleEmitter.onSuccess(Unit)
                }
            }, { error ->
                singleEmitter.tryOnError(error)
            })
        }
    }

    fun isIdentityNameValid(name: CharSequence): Boolean {
        val equityMemberId = state?.zone?.accountsList?.find {
            it.id == state!!.zone.equityAccountId && it.ownerMemberIdsCount == 1
        }?.getOwnerMemberIds(0)
        return isTagValid(name) &&
                name.toString() != state?.zone?.membersList?.find {
            it.id == equityMemberId
        }?.name?.value
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
        val zoneId = state!!.zone.id
        val member = if (partiallyCreatedIdentity != null) {
            Single.just(partiallyCreatedIdentity)
        } else {
            Single.create<Model.Member> { singleEmitter ->
                Single.fromFuture(serverConnection.commandStub.createMember(
                        GrpcProtocol.CreateMemberCommand.newBuilder()
                                .setZoneId(zoneId)
                                .addOwnerPublicKeys(serverConnection.clientKey)
                                .setName(StringValue.newBuilder().setValue(name))
                                .build()
                )).subscribe({ createMemberResponse ->
                    when (createMemberResponse.resultCase!!) {
                        GrpcProtocol.CreateMemberResponse.ResultCase.RESULT_NOT_SET ->
                            singleEmitter.tryOnError(IllegalArgumentException(
                                    createMemberResponse.resultCase.name
                            ))
                        GrpcProtocol.CreateMemberResponse.ResultCase.ERRORS ->
                            singleEmitter.tryOnError(IllegalArgumentException(
                                    createMemberResponse.errors.toString()
                            ))
                        GrpcProtocol.CreateMemberResponse.ResultCase.SUCCESS ->
                            singleEmitter.onSuccess(
                                    createMemberResponse.success.member
                            )
                    }
                }, { error ->
                    singleEmitter.tryOnError(error)
                })
            }
        }
        @Suppress("RedundantLambdaArrow")
        return member.flatMap { createAccount(zoneId, it.id).map { _ -> it } }
    }

    fun changeIdentityName(identityId: String, name: String): Single<Unit> {
        val member = state!!.zone.membersList.find {
            it.id == identityId
        }!!
        return Single.create<Unit> { singleEmitter ->
            Single.fromFuture(serverConnection.commandStub.updateMember(
                    GrpcProtocol.UpdateMemberCommand.newBuilder()
                            .setZoneId(state!!.zone.id)
                            .setMember(
                                    member.toBuilder()
                                            .setName(
                                                    StringValue.newBuilder()
                                                            .setValue(name)
                                            )
                            )
                            .build()
            )).subscribe({ updateMemberResponse ->
                when (updateMemberResponse.resultCase!!) {
                    GrpcProtocol.UpdateMemberResponse.ResultCase.RESULT_NOT_SET ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                updateMemberResponse.resultCase.name
                        ))
                    GrpcProtocol.UpdateMemberResponse.ResultCase.ERRORS ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                updateMemberResponse.errors.toString()
                        ))
                    GrpcProtocol.UpdateMemberResponse.ResultCase.SUCCESS ->
                        singleEmitter.onSuccess(Unit)
                }
            }, { error ->
                singleEmitter.tryOnError(error)
            })
        }
    }

    fun isPublicKeyConnectedAndImplicitlyValid(publicKey: ByteString): Boolean? {
        return state?.connectedClients?.values?.contains(publicKey)
    }

    fun transferIdentity(identityId: String, toPublicKey: ByteString): Single<Unit> {
        val member = state!!.zone.membersList.find {
            it.id == identityId
        }!!
        return Single.create<Unit> { singleEmitter ->
            Single.fromFuture(serverConnection.commandStub.updateMember(
                    GrpcProtocol.UpdateMemberCommand.newBuilder()
                            .setZoneId(state!!.zone.id)
                            .setMember(
                                    member.toBuilder()
                                    .clearOwnerPublicKeys()
                                    .addAllOwnerPublicKeys(
                                            member.ownerPublicKeysList
                                                    .minusElement(serverConnection.clientKey)
                                                    .plusElement(toPublicKey)
                                    )
                            )
                            .build()
            )).subscribe({ updateMemberResponse ->
                when (updateMemberResponse.resultCase!!) {
                    GrpcProtocol.UpdateMemberResponse.ResultCase.RESULT_NOT_SET ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                updateMemberResponse.resultCase.name
                        ))
                    GrpcProtocol.UpdateMemberResponse.ResultCase.ERRORS ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                updateMemberResponse.errors.toString()
                        ))
                    GrpcProtocol.UpdateMemberResponse.ResultCase.SUCCESS ->
                        singleEmitter.onSuccess(Unit)
                }
            }, { error ->
                singleEmitter.tryOnError(error)
            })
        }
    }

    fun deleteIdentity(identityId: String): Single<Unit> {
        val member = state!!.zone.membersList.find {
            it.id == identityId
        }!!
        val metadata = member.metadata.toBuilder()
                .putFields(HIDDEN_FLAG_KEY, Value.newBuilder().setBoolValue(true).build())
        return Single.create<Unit> { singleEmitter ->
            Single.fromFuture(serverConnection.commandStub.updateMember(
                                    GrpcProtocol.UpdateMemberCommand.newBuilder()
                                            .setZoneId(state!!.zone.id)
                                            .setMember(member.toBuilder().setMetadata(metadata))
                            .build()
            )).subscribe({ updateMemberResponse ->
                when (updateMemberResponse.resultCase!!) {
                    GrpcProtocol.UpdateMemberResponse.ResultCase.RESULT_NOT_SET ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                updateMemberResponse.resultCase.name
                        ))
                    GrpcProtocol.UpdateMemberResponse.ResultCase.ERRORS ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                updateMemberResponse.errors.toString()
                        ))
                    GrpcProtocol.UpdateMemberResponse.ResultCase.SUCCESS ->
                        singleEmitter.onSuccess(Unit)
                }
            }, { error ->
                singleEmitter.tryOnError(error)
            })
        }
    }

    fun restoreIdentity(identityId: String): Single<Unit> {
        val member = state!!.zone.membersList.find {
            it.id == identityId
        }!!
        val metadata = member.metadata.toBuilder()
                .removeFields(HIDDEN_FLAG_KEY)
        return Single.create<Unit> { singleEmitter ->
            Single.fromFuture(serverConnection.commandStub.updateMember(
                                    GrpcProtocol.UpdateMemberCommand.newBuilder()
                                            .setZoneId(state!!.zone.id)
                                            .setMember(member.toBuilder().setMetadata(metadata))
                            .build()
            )).subscribe({ updateMemberResponse ->
                when (updateMemberResponse.resultCase!!) {
                    GrpcProtocol.UpdateMemberResponse.ResultCase.RESULT_NOT_SET ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                updateMemberResponse.resultCase.name
                        ))
                    GrpcProtocol.UpdateMemberResponse.ResultCase.ERRORS ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                updateMemberResponse.errors.toString()
                        ))
                    GrpcProtocol.UpdateMemberResponse.ResultCase.SUCCESS ->
                        singleEmitter.onSuccess(Unit)
                }
            }, { error ->
                singleEmitter.tryOnError(error)
            })
        }
    }

    fun transferToPlayer(from: Identity,
                         to: Player,
                         value: BigDecimal
    ): Single<Unit> {
        return Single.create<Unit> { singleEmitter ->
            Single.fromFuture(serverConnection.commandStub.addTransaction(
                                    GrpcProtocol.AddTransactionCommand.newBuilder()
                                            .setZoneId(state!!.zone.id)
                                            .setActingAs(from.memberId)
                                            .setFrom(from.accountId)
                                            .setTo(to.accountId)
                                            .setValue(value.toString())
                            .build()
            )).subscribe({ addTransactionResponse ->
                when (addTransactionResponse.resultCase!!) {
                    GrpcProtocol.AddTransactionResponse.ResultCase.RESULT_NOT_SET ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                addTransactionResponse.resultCase.name
                        ))
                    GrpcProtocol.AddTransactionResponse.ResultCase.ERRORS ->
                        singleEmitter.tryOnError(IllegalArgumentException(
                                addTransactionResponse.errors.toString()
                        ))
                    GrpcProtocol.AddTransactionResponse.ResultCase.SUCCESS ->
                        singleEmitter.onSuccess(Unit)
                }
            }, { error ->
                singleEmitter.tryOnError(error)
            })
        }
    }

}
