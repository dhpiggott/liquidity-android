package com.dhpcs.liquidity;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;

import com.dhpcs.liquidity.models.Account;
import com.dhpcs.liquidity.models.AccountCreatedNotification;
import com.dhpcs.liquidity.models.AccountId;
import com.dhpcs.liquidity.models.AccountUpdatedNotification;
import com.dhpcs.liquidity.models.ClientJoinedZoneNotification;
import com.dhpcs.liquidity.models.ClientQuitZoneNotification;
import com.dhpcs.liquidity.models.CreateAccountCommand;
import com.dhpcs.liquidity.models.CreateAccountResponse;
import com.dhpcs.liquidity.models.CreateMemberCommand;
import com.dhpcs.liquidity.models.CreateMemberResponse;
import com.dhpcs.liquidity.models.CreateZoneCommand;
import com.dhpcs.liquidity.models.CreateZoneResponse;
import com.dhpcs.liquidity.models.JoinZoneCommand;
import com.dhpcs.liquidity.models.JoinZoneResponse;
import com.dhpcs.liquidity.models.Member;
import com.dhpcs.liquidity.models.MemberCreatedNotification;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.models.MemberUpdatedNotification;
import com.dhpcs.liquidity.models.Notification;
import com.dhpcs.liquidity.models.PublicKey;
import com.dhpcs.liquidity.models.QuitZoneCommand;
import com.dhpcs.liquidity.models.QuitZoneResponse$;
import com.dhpcs.liquidity.models.ResultResponse;
import com.dhpcs.liquidity.models.Transaction;
import com.dhpcs.liquidity.models.TransactionAddedNotification;
import com.dhpcs.liquidity.models.TransactionId;
import com.dhpcs.liquidity.models.Zone;
import com.dhpcs.liquidity.models.ZoneId;
import com.dhpcs.liquidity.models.ZoneNameSetNotification;
import com.dhpcs.liquidity.models.ZoneNotification;
import com.dhpcs.liquidity.models.ZoneTerminatedNotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.JavaConverters;

public class MonopolyGame implements ServerConnection.ConnectionStateListener,
        ServerConnection.NotificationListener {

    public interface Listener {

        void onConnectedPlayersChanged(Map<MemberId, Member> connectedPlayers);

        void onJoined(ZoneId zoneId);

        void onOtherPlayersChanged(Map<MemberId, Member> otherPlayers);

        void onPlayerBalancesChanged(Map<MemberId, BigDecimal> playerBalances);

        void onQuit();

        void onUserIdentitiesChanged(Map<MemberId, Member> userIdentities);

    }

    private static final GameType ZONE_TYPE = GameType.MONOPOLY;

    private static final String BANK_MEMBER_NAME = "Banker";
    private static final String BANK_ACCOUNT_NAME = "Bank";
    private static final String GAME_NAME_PREFIX = "Bank of ";
    private static final String ACCOUNT_NAME_SUFFIX = "'s account";

    private static String getUserName(Context context, String aliasConstant) {
        Cursor cursor = context.getContentResolver().query(
                ContactsContract.Profile.CONTENT_URI,
                new String[]{aliasConstant},
                null,
                null,
                null
        );

        String userName = null;
        if (cursor.moveToNext()) {
            userName = cursor.getString(cursor.getColumnIndex(aliasConstant));
        }
        cursor.close();

        return String.valueOf(userName);
    }


    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Context context;
    private final Handler mainHandler;
    private final ServerConnection serverConnection;

    private final Set<PublicKey> connectedClients = new HashSet<>();
    private final Map<MemberId, Member> userMembers = new HashMap<>();
    private final Map<MemberId, Member> otherMembers = new HashMap<>();
    private final Map<MemberId, Member> connectedMembers = new HashMap<>();

    private BigDecimal initialCapital;
    private ZoneId zoneId;

    private Zone zone;
    private scala.collection.immutable.Map<AccountId, scala.math.BigDecimal> accountBalances;
    private Map<MemberId, BigDecimal> memberBalances;

    private Listener listener;

    public MonopolyGame(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.serverConnection = new ServerConnection(context, this, this);
    }

    public void connectCreateAndOrJoinZone() {
        serverConnection.connect();
    }

    private void createAndThenJoinZone() {
        final String playerName = getUserName(
                context,
                ContactsContract.Profile.DISPLAY_NAME
        );

        log.debug("playerName={}", playerName);

        serverConnection.sendCommand(
                new CreateZoneCommand(
                        GAME_NAME_PREFIX + playerName,
                        ZONE_TYPE.typeName,
                        new Member(
                                BANK_MEMBER_NAME,
                                ClientKey.getInstance(context).getPublicKey()
                        ),
                        new Account(
                                BANK_ACCOUNT_NAME,
                                JavaConversions.asScalaSet(
                                        Collections.emptySet()
                                ).<MemberId>toSet()
                        )),
                new ServerConnection.ResponseCallback() {

                    @Override
                    void onResultReceived(ResultResponse resultResponse) {
                        final CreateZoneResponse createZoneResponse = (CreateZoneResponse) resultResponse;

                        log.debug("createZoneResponse={}", createZoneResponse);

                        mainHandler.post(new Runnable() {

                            @Override
                            public void run() {
                                setZoneId(createZoneResponse.zoneId());
                                join();
                            }

                        });

                    }

                });

    }

    private void createPlayer(final ZoneId zoneId) {

        final String playerName = getUserName(
                context,
                ContactsContract.Profile.DISPLAY_NAME
        );

        log.debug("playerName={}", playerName);

        serverConnection.sendCommand(
                new CreateMemberCommand(
                        zoneId,
                        new Member(
                                playerName,
                                ClientKey.getInstance(context).getPublicKey()
                        )
                ),
                new ServerConnection.ResponseCallback() {

                    @Override
                    void onResultReceived(ResultResponse resultResponse) {
                        final CreateMemberResponse createMemberResponse = (CreateMemberResponse) resultResponse;

                        log.debug("createMemberResponse={}", createMemberResponse);

                        mainHandler.post(new Runnable() {

                            @Override
                            public void run() {
                                serverConnection.sendCommand(
                                        new CreateAccountCommand(
                                                zoneId,
                                                new Account(
                                                        playerName + ACCOUNT_NAME_SUFFIX,
                                                        JavaConversions.asScalaSet(
                                                                Collections.singleton(createMemberResponse.memberId())
                                                        ).<MemberId>toSet()
                                                )
                                        ),
                                        new ServerConnection.ResponseCallback() {

                                            @Override
                                            void onResultReceived(ResultResponse resultResponse) {
                                                CreateAccountResponse createAccountResponse = (CreateAccountResponse) resultResponse;

                                                log.debug("createAccountResponse={}", createAccountResponse);

                                            }

                                        }
                                );
                            }

                        });

                    }

                }
        );
    }

    private void disconnect() {
        serverConnection.disconnect();
    }

    private void join() {
        serverConnection.sendCommand(
                new JoinZoneCommand(zoneId),
                new ServerConnection.ResponseCallback() {

                    @Override
                    void onResultReceived(ResultResponse resultResponse) {
                        final JoinZoneResponse joinZoneResponse = (JoinZoneResponse) resultResponse;

                        log.debug("joinZoneResponse={}", joinZoneResponse);

                        mainHandler.post(new Runnable() {

                            @Override
                            public void run() {

                                if (listener != null) {
                                    listener.onJoined(zoneId);
                                }

                                connectedClients.addAll(
                                        JavaConverters.setAsJavaSetConverter(
                                                joinZoneResponse.connectedClients()
                                        ).asJava()
                                );

                                zone = joinZoneResponse.zone();

                                // TODO: Name

                                PublicKey clientPublicKey =
                                        ClientKey.getInstance(context).getPublicKey();

                                userMembers.putAll(
                                        ZoneHelper.clientMembersAsJavaMap(
                                                zone,
                                                clientPublicKey
                                        )
                                );
                                otherMembers.putAll(
                                        ZoneHelper.otherMembersAsJavaMap(
                                                zone,
                                                clientPublicKey
                                        )
                                );
                                connectedMembers.putAll(
                                        ZoneHelper.connectedMembersAsJavaMap(
                                                otherMembers,
                                                connectedClients
                                        )
                                );

                                Iterator<Transaction> iterator = zone.transactions().valuesIterator();
                                while (iterator.hasNext()) {
                                    accountBalances = Zone.checkAndUpdateBalances(
                                            iterator.next(),
                                            zone,
                                            accountBalances
                                    ).right().get();
                                }

                                memberBalances = ZoneHelper.aggregateMembersAccountBalancesAsJavaMap(
                                        zone,
                                        accountBalances
                                );

                                if (listener != null) {
                                    listener.onUserIdentitiesChanged(userMembers);
                                    listener.onOtherPlayersChanged(otherMembers);
                                    listener.onConnectedPlayersChanged(connectedMembers);
                                    listener.onPlayerBalancesChanged(memberBalances);
                                }

                                if (userMembers.isEmpty()) {

                                    log.debug("No identities for user, must have disconnected after create/join - creating player now");

                                    createPlayer(zoneId);

                                }

                            }

                        });

                    }

                }
        );
    }

    @Override
    public void onNotificationReceived(final Notification notification) {
        log.debug("notification={}", notification);

        if (notification instanceof ZoneNotification) {
            final ZoneNotification zoneNotification = (ZoneNotification) notification;

            if (!zoneNotification.zoneId().equals(zoneId)) {
                throw new RuntimeException(
                        "zoneNotification.zoneId() != zoneId ("
                                + zoneNotification.zoneId() + " != " + zoneId
                                + ")"
                );
            }

            mainHandler.post(new Runnable() {

                @Override
                public void run() {

                    if (zoneNotification instanceof ClientJoinedZoneNotification) {
                        ClientJoinedZoneNotification clientJoinedZoneNotification =
                                (ClientJoinedZoneNotification) zoneNotification;

                        log.debug("clientJoinedZoneNotification={}", clientJoinedZoneNotification);

                        connectedClients.add(clientJoinedZoneNotification.publicKey());

                        connectedMembers.putAll(
                                ZoneHelper.connectedMembersAsJavaMap(
                                        otherMembers,
                                        Collections.singleton(
                                                clientJoinedZoneNotification.publicKey()
                                        )
                                )
                        );

                        if (listener != null) {
                            listener.onConnectedPlayersChanged(connectedMembers);
                        }

                    } else if (zoneNotification instanceof ClientQuitZoneNotification) {
                        ClientQuitZoneNotification clientQuitZoneNotification =
                                (ClientQuitZoneNotification) zoneNotification;

                        log.debug("clientQuitZoneNotification={}", clientQuitZoneNotification);

                        connectedClients.remove(clientQuitZoneNotification.publicKey());

                        connectedMembers.keySet().removeAll(
                                ZoneHelper.connectedMembersAsJavaMap(
                                        otherMembers,
                                        Collections.singleton(
                                                clientQuitZoneNotification.publicKey()
                                        )
                                ).keySet()
                        );

                        if (listener != null) {
                            listener.onConnectedPlayersChanged(connectedMembers);
                        }

                    } else if (zoneNotification instanceof ZoneTerminatedNotification) {

                        connectedClients.clear();
                        connectedMembers.clear();
                        otherMembers.clear();
                        userMembers.clear();
                        zone = null;
                        accountBalances = null;
                        memberBalances = null;

                        if (listener != null) {
                            listener.onQuit();
                        }

                        serverConnection.sendCommand(
                                new JoinZoneCommand(
                                        zoneId
                                ),
                                new ServerConnection.ResponseCallback() {

                                    @Override
                                    void onResultReceived(ResultResponse resultResponse) {
                                        JoinZoneResponse joinZoneResponse = (JoinZoneResponse) resultResponse;

                                        log.debug("joinZoneResponse={}", joinZoneResponse);

                                        // TODO:

                                    }

                                });

                    } else if (zoneNotification instanceof ZoneNameSetNotification) {
                        ZoneNameSetNotification zoneNameSetNotification =
                                (ZoneNameSetNotification) zoneNotification;

                        log.debug("zoneNameSetNotification={}", zoneNameSetNotification);

                        String name = zoneNameSetNotification.name();

                        // TODO: Name

                        zone = new Zone(
                                name,
                                zone.zoneType(),
                                zone.equityHolderMemberId(),
                                zone.equityHolderAccountId(),
                                zone.members(),
                                zone.accounts(),
                                zone.transactions(),
                                zone.created()
                        );

                    } else if (zoneNotification instanceof MemberCreatedNotification) {
                        MemberCreatedNotification memberCreatedNotification =
                                (MemberCreatedNotification) zoneNotification;

                        log.debug("memberCreatedNotification={}", memberCreatedNotification);

                        MemberId createdMemberId = memberCreatedNotification.memberId();
                        Member createdMember = memberCreatedNotification.member();

                        zone = new Zone(
                                zone.name(),
                                zone.zoneType(),
                                zone.equityHolderMemberId(),
                                zone.equityHolderAccountId(),
                                zone.members().updated(
                                        createdMemberId,
                                        createdMember
                                ),
                                zone.accounts(),
                                zone.transactions(),
                                zone.created()
                        );

                        PublicKey clientPublicKey = ClientKey.getInstance(context).getPublicKey();

                        if (!createdMemberId.equals(zone.equityHolderMemberId())) {

                            if (createdMember.publicKey().equals(clientPublicKey)) {

                                userMembers.put(createdMemberId, createdMember);

                                if (listener != null) {
                                    listener.onUserIdentitiesChanged(userMembers);
                                }

                            } else {

                                otherMembers.put(createdMemberId, createdMember);

                                if (listener != null) {
                                    listener.onOtherPlayersChanged(otherMembers);
                                }

                                if (connectedClients.contains(createdMember.publicKey())) {

                                    connectedMembers.put(createdMemberId, createdMember);

                                    if (listener != null) {
                                        listener.onConnectedPlayersChanged(connectedMembers);
                                    }

                                }

                            }

                        }

                    } else if (zoneNotification instanceof MemberUpdatedNotification) {
                        MemberUpdatedNotification memberUpdatedNotification =
                                (MemberUpdatedNotification) zoneNotification;

                        log.debug("memberUpdatedNotification={}", memberUpdatedNotification);

                        MemberId updatedMemberId = memberUpdatedNotification.memberId();
                        Member updatedMember = memberUpdatedNotification.member();

                        zone = new Zone(
                                zone.name(),
                                zone.zoneType(),
                                zone.equityHolderMemberId(),
                                zone.equityHolderAccountId(),
                                zone.members().updated(
                                        updatedMemberId,
                                        updatedMember
                                ),
                                zone.accounts(),
                                zone.transactions(),
                                zone.created()
                        );

                        PublicKey clientPublicKey = ClientKey.getInstance(context).getPublicKey();

                        if (!updatedMemberId.equals(zone.equityHolderMemberId())) {

                            if (userMembers.containsKey(updatedMemberId)) {

                                if (!updatedMember.publicKey().equals(clientPublicKey)) {

                                    userMembers.remove(updatedMemberId);
                                    otherMembers.put(updatedMemberId, updatedMember);

                                    if (listener != null) {
                                        listener.onUserIdentitiesChanged(userMembers);
                                        listener.onOtherPlayersChanged(otherMembers);
                                    }

                                    if (connectedClients.contains(updatedMember.publicKey())) {

                                        connectedMembers.put(updatedMemberId, updatedMember);

                                        if (listener != null) {
                                            listener.onConnectedPlayersChanged(connectedMembers);
                                        }

                                    }

                                } else {

                                    userMembers.put(updatedMemberId, updatedMember);

                                    if (listener != null) {
                                        listener.onUserIdentitiesChanged(userMembers);
                                    }

                                }

                            } else if (otherMembers.containsKey(updatedMemberId)) {

                                if (updatedMember.publicKey().equals(clientPublicKey)) {

                                    otherMembers.remove(updatedMemberId);
                                    userMembers.put(updatedMemberId, updatedMember);

                                    if (listener != null) {
                                        listener.onUserIdentitiesChanged(userMembers);
                                        listener.onOtherPlayersChanged(otherMembers);
                                    }

                                    if (connectedClients.contains(updatedMember.publicKey())) {

                                        connectedMembers.remove(updatedMemberId);

                                        if (listener != null) {
                                            listener.onConnectedPlayersChanged(connectedMembers);
                                        }

                                    }

                                } else {

                                    otherMembers.put(updatedMemberId, updatedMember);

                                    if (listener != null) {
                                        listener.onOtherPlayersChanged(otherMembers);
                                    }

                                }

                            } else {
                                throw new RuntimeException(
                                        "Received update of non-existent memberId" + updatedMemberId
                                );
                            }

                        }

                    } else if (zoneNotification instanceof AccountCreatedNotification) {
                        AccountCreatedNotification accountCreatedNotification =
                                (AccountCreatedNotification) zoneNotification;

                        log.debug("accountCreatedNotification={}", accountCreatedNotification);

                        AccountId createdAccountId = accountCreatedNotification.accountId();
                        Account createdAccount = accountCreatedNotification.account();

                        zone = new Zone(
                                zone.name(),
                                zone.zoneType(),
                                zone.equityHolderMemberId(),
                                zone.equityHolderAccountId(),
                                zone.members(),
                                zone.accounts().updated(
                                        createdAccountId,
                                        createdAccount
                                ),
                                zone.transactions(),
                                zone.created()
                        );

                        Map<MemberId, BigDecimal> newMemberBalances = ZoneHelper.aggregateMembersAccountBalancesAsJavaMap(
                                zone,
                                accountBalances
                        );

                        if (!newMemberBalances.equals(memberBalances)) {

                            memberBalances = newMemberBalances;

                            if (listener != null) {
                                listener.onPlayerBalancesChanged(memberBalances);
                            }

                        }

                    } else if (zoneNotification instanceof AccountUpdatedNotification) {
                        AccountUpdatedNotification accountUpdatedNotification =
                                (AccountUpdatedNotification) zoneNotification;

                        log.debug("accountUpdatedNotification={}", accountUpdatedNotification);

                        AccountId updatedAccountId = accountUpdatedNotification.accountId();
                        Account updatedAccount = accountUpdatedNotification.account();

                        zone = new Zone(
                                zone.name(),
                                zone.zoneType(),
                                zone.equityHolderMemberId(),
                                zone.equityHolderAccountId(),
                                zone.members(),
                                zone.accounts().updated(
                                        updatedAccountId,
                                        updatedAccount
                                ),
                                zone.transactions(),
                                zone.created()
                        );

                        Map<MemberId, BigDecimal> newMemberBalances = ZoneHelper.aggregateMembersAccountBalancesAsJavaMap(
                                zone,
                                accountBalances
                        );

                        if (!newMemberBalances.equals(memberBalances)) {

                            memberBalances = newMemberBalances;

                            if (listener != null) {
                                listener.onPlayerBalancesChanged(memberBalances);
                            }

                        }

                    } else if (zoneNotification instanceof TransactionAddedNotification) {
                        TransactionAddedNotification transactionAddedNotification =
                                (TransactionAddedNotification) zoneNotification;

                        log.debug("transactionAddedNotification={}", transactionAddedNotification);

                        TransactionId addedTransactionId = transactionAddedNotification.transactionId();
                        Transaction addedTransaction = transactionAddedNotification.transaction();

                        zone = new Zone(
                                zone.name(),
                                zone.zoneType(),
                                zone.equityHolderMemberId(),
                                zone.equityHolderAccountId(),
                                zone.members(),
                                zone.accounts(),
                                zone.transactions().updated(
                                        addedTransactionId,
                                        addedTransaction
                                ),
                                zone.created()
                        );

                        accountBalances = Zone.checkAndUpdateBalances(
                                addedTransaction,
                                zone,
                                accountBalances
                        ).right().get();


                        Map<MemberId, BigDecimal> newMemberBalances = ZoneHelper.aggregateMembersAccountBalancesAsJavaMap(
                                zone,
                                accountBalances
                        );

                        if (!newMemberBalances.equals(memberBalances)) {

                            memberBalances = newMemberBalances;

                            if (listener != null) {
                                listener.onPlayerBalancesChanged(memberBalances);
                            }

                        }

                    }

                }

            });

        }
    }

    @Override
    public void onStateChanged(ServerConnection.ConnectionState connectionState) {
        log.debug("connectionState={}", connectionState);

        switch (connectionState) {
            case CONNECTING:
                break;
            case CONNECTED:

                mainHandler.post(new Runnable() {

                    @Override
                    public void run() {

                        if (zoneId != null) {
                            join();
                        } else if (initialCapital != null) {
                            createAndThenJoinZone();
                        } else {
                            throw new RuntimeException(
                                    "Neither zoneId nor initialCapital were set"
                            );
                        }

                    }

                });

                break;
            case DISCONNECTING:
                break;
            case DISCONNECTED:

                // TODO

                break;
        }
    }

    public void quitAndOrDisconnect() {
        if (zone == null) {
            disconnect();
        } else {
            serverConnection.sendCommand(
                    new QuitZoneCommand(zoneId),
                    new ServerConnection.ResponseCallback() {

                        @Override
                        void onResultReceived(ResultResponse resultResponse) {
                            QuitZoneResponse$ quitZoneResponse = (QuitZoneResponse$) resultResponse;

                            log.debug("quitZoneResponse", quitZoneResponse);

                            mainHandler.post(new Runnable() {

                                @Override
                                public void run() {

                                    if (listener != null) {
                                        listener.onQuit();
                                    }

                                    zone = null;

                                    disconnect();

                                }

                            });

                        }

                    }
            );
        }
    }

    public void setInitialCapital(BigDecimal initialCapital) {
        this.initialCapital = initialCapital;
    }

    // TODO
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setZoneId(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

}
