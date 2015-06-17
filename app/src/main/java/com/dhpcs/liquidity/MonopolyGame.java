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
import com.dhpcs.liquidity.models.ResultResponse;
import com.dhpcs.liquidity.models.TransactionAddedNotification;
import com.dhpcs.liquidity.models.Zone;
import com.dhpcs.liquidity.models.Zone$;
import com.dhpcs.liquidity.models.ZoneId;
import com.dhpcs.liquidity.models.ZoneNameSetNotification;
import com.dhpcs.liquidity.models.ZoneNotification;
import com.dhpcs.liquidity.models.ZoneStateNotification;
import com.dhpcs.liquidity.models.ZoneTerminatedNotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import scala.collection.JavaConversions;
import scala.collection.JavaConverters;

public class MonopolyGame implements ServerConnection.ConnectionStateListener,
        ServerConnection.NotificationListener {

    public interface Listener {

        // TODO: Names

        void onConnectedMembersChanged(Map<MemberId, Member> connectedMembers);

        void onConnected();

        void onDisconnected();

        void onJoined(ZoneId zoneId);

        void onQuit();

        void onMemberBalanceChanged(MemberId memberId, BigDecimal balance);

        void onOtherMembersChanged(Map<MemberId, Member> otherMembers);

        void onUserMembersChanged(Map<MemberId, Member> userMembers);

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
    private final Map<MemberId, Member> connectedMembers = new HashMap<>();
    private final Map<MemberId, Member> otherMembers = new HashMap<>();
    private final Map<MemberId, Member> userMembers = new HashMap<>();
    private final Map<AccountId, BigDecimal> memberBalances = new HashMap<>();

    private ZoneId zoneId;
    private BigDecimal capitalToStartWith;

    private Listener listener;

    public MonopolyGame(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.serverConnection = new ServerConnection(context, this, this);
    }

    public void connect() {
        serverConnection.connect();
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
                    public void onResultReceived(ResultResponse resultResponse) {
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
                                            public void onResultReceived(ResultResponse resultResponse) {
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
                    public void onResultReceived(ResultResponse resultResponse) {
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

    public void disconnect() {
        serverConnection.disconnect();
    }

    private void join() {
        serverConnection.sendCommand(
                new JoinZoneCommand(zoneId),
                new ServerConnection.ResponseCallback() {

                    @Override
                    void onResultReceived(ResultResponse resultResponse) {
                        final JoinZoneResponse joinZoneResponse = (JoinZoneResponse) resultResponse;

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

                                Zone zone = joinZoneResponse.zone();

                                // TODO: Name

                                PublicKey userPublicKey =
                                        ClientKey.getInstance(context).getPublicKey();

                                userMembers.putAll(
                                        Zone$.MODULE$.userMembersAsJavaMap(
                                                zone,
                                                userPublicKey
                                        )
                                );
                                otherMembers.putAll(
                                        Zone$.MODULE$.otherMembersAsJavaMap(
                                                zone,
                                                userPublicKey
                                        )
                                );
                                connectedMembers.putAll(
                                        Zone$.MODULE$.connectedMembersAsJavaMap(
                                                otherMembers,
                                                connectedClients
                                        )
                                );

                                if (listener != null) {
                                    listener.onUserMembersChanged(userMembers);
                                    listener.onOtherMembersChanged(otherMembers);
                                    listener.onConnectedMembersChanged(connectedMembers);
                                }

                                // TODO: Balances

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
                                Zone$.MODULE$.connectedMembersAsJavaMap(
                                        otherMembers,
                                        Collections.singleton(
                                                clientJoinedZoneNotification.publicKey()
                                        )
                                )
                        );

                        if (listener != null) {
                            listener.onConnectedMembersChanged(connectedMembers);
                        }

                    } else if (zoneNotification instanceof ClientQuitZoneNotification) {
                        ClientQuitZoneNotification clientQuitZoneNotification =
                                (ClientQuitZoneNotification) zoneNotification;

                        log.debug("clientQuitZoneNotification={}", clientQuitZoneNotification);

                        connectedClients.remove(clientQuitZoneNotification.publicKey());

                        connectedMembers.keySet().removeAll(
                                Zone$.MODULE$.connectedMembersAsJavaMap(
                                        otherMembers,
                                        Collections.singleton(
                                                clientQuitZoneNotification.publicKey()
                                        )
                                ).keySet()
                        );

                        if (listener != null) {
                            listener.onConnectedMembersChanged(connectedMembers);
                        }

                    } else if (zoneNotification instanceof ZoneTerminatedNotification) {

                        connectedClients.clear();
                        connectedMembers.clear();
                        otherMembers.clear();
                        userMembers.clear();
                        memberBalances.clear();
                        serverConnection.sendCommand(
                                new JoinZoneCommand(
                                        zoneId
                                ),
                                new ServerConnection.ResponseCallback() {

                                    @Override
                                    void onResultReceived(ResultResponse resultResponse) {
                                        JoinZoneResponse joinZoneResponse = (JoinZoneResponse) resultResponse;
                                        // TODO:
                                    }

                                });

                    } else if (zoneNotification instanceof ZoneStateNotification) {
                        final ZoneStateNotification zoneStateNotification = (ZoneStateNotification) zoneNotification;

                        // TODO: lastModified

                        if (zoneStateNotification instanceof ZoneNameSetNotification) {
                            ZoneNameSetNotification zoneNameSetNotification =
                                    (ZoneNameSetNotification) zoneStateNotification;

                            log.debug("zoneNameSetNotification={}", zoneNameSetNotification);

                            // TODO

                        } else if (zoneStateNotification instanceof MemberCreatedNotification) {
                            MemberCreatedNotification memberCreatedNotification =
                                    (MemberCreatedNotification) zoneStateNotification;

                            log.debug("memberCreatedNotification={}", memberCreatedNotification);

                            MemberId createdMemberId = memberCreatedNotification.memberId();
                            Member createdMember = memberCreatedNotification.member();

                            PublicKey userPublicKey = ClientKey.getInstance(context).getPublicKey();

                            if (createdMember.publicKey().equals(userPublicKey)) {
                                userMembers.put(createdMemberId, createdMember);
                                if (listener != null) {
                                    listener.onUserMembersChanged(userMembers);
                                }
                            } else {
                                otherMembers.put(createdMemberId, createdMember);
                                if (connectedClients.contains(createdMember.publicKey())) {
                                    connectedMembers.put(createdMemberId, createdMember);
                                    if (listener != null) {
                                        listener.onOtherMembersChanged(otherMembers);
                                        listener.onConnectedMembersChanged(connectedMembers);
                                    }
                                } else {
                                    if (listener != null) {
                                        listener.onOtherMembersChanged(otherMembers);
                                    }
                                }
                            }

                        } else if (zoneStateNotification instanceof MemberUpdatedNotification) {
                            MemberUpdatedNotification memberUpdatedNotification =
                                    (MemberUpdatedNotification) zoneStateNotification;

                            log.debug("memberUpdatedNotification={}", memberUpdatedNotification);

                            MemberId updatedMemberId = memberUpdatedNotification.memberId();
                            Member updatedMember = memberUpdatedNotification.member();

                            PublicKey userPublicKey = ClientKey.getInstance(context).getPublicKey();

                            if (userMembers.containsKey(updatedMemberId)) {

                                if (!updatedMember.publicKey().equals(userPublicKey)) {
                                    userMembers.remove(updatedMemberId);
                                    otherMembers.put(updatedMemberId, updatedMember);
                                    if (connectedClients.contains(updatedMember.publicKey())) {
                                        connectedMembers.put(updatedMemberId, updatedMember);
                                        if (listener != null) {
                                            listener.onUserMembersChanged(userMembers);
                                            listener.onOtherMembersChanged(otherMembers);
                                            listener.onConnectedMembersChanged(connectedMembers);
                                        }
                                    } else {
                                        if (listener != null) {
                                            listener.onUserMembersChanged(userMembers);
                                            listener.onOtherMembersChanged(otherMembers);
                                        }
                                    }
                                } else {
                                    userMembers.put(updatedMemberId, updatedMember);
                                    if (listener != null) {
                                        listener.onUserMembersChanged(userMembers);
                                    }
                                }

                            } else if (otherMembers.containsKey(updatedMemberId)) {

                                if (updatedMember.publicKey().equals(userPublicKey)) {
                                    otherMembers.remove(updatedMemberId);
                                    userMembers.put(updatedMemberId, updatedMember);
                                    if (connectedClients.contains(updatedMember.publicKey())) {
                                        connectedMembers.remove(updatedMemberId);
                                        if (listener != null) {
                                            listener.onUserMembersChanged(userMembers);
                                            listener.onOtherMembersChanged(otherMembers);
                                            listener.onConnectedMembersChanged(connectedMembers);
                                        }
                                    } else {
                                        if (listener != null) {
                                            listener.onUserMembersChanged(userMembers);
                                            listener.onOtherMembersChanged(otherMembers);
                                        }
                                    }
                                } else {
                                    otherMembers.put(updatedMemberId, updatedMember);
                                    if (listener != null) {
                                        listener.onOtherMembersChanged(otherMembers);
                                    }
                                }

                            } else {
                                throw new RuntimeException(
                                        "Received update of non-existent memberId" + updatedMemberId
                                );
                            }

                        } else if (zoneStateNotification instanceof AccountCreatedNotification) {
                            AccountCreatedNotification accountCreatedNotification =
                                    (AccountCreatedNotification) zoneStateNotification;

                            log.debug("accountCreatedNotification={}", accountCreatedNotification);

                            // TODO

                        } else if (zoneStateNotification instanceof AccountUpdatedNotification) {
                            AccountUpdatedNotification accountUpdatedNotification =
                                    (AccountUpdatedNotification) zoneStateNotification;

                            log.debug("accountUpdatedNotification={}", accountUpdatedNotification);

                            // TODO

                        } else if (zoneStateNotification instanceof TransactionAddedNotification) {
                            TransactionAddedNotification transactionAddedNotification =
                                    (TransactionAddedNotification) zoneStateNotification;

                            log.debug("transactionAddedNotification={}", transactionAddedNotification);

                            // TODO: Balances

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

                        if (listener != null) {
                            listener.onConnected();
                        }

                        if (zoneId != null) {
                            join();
                        } else if (capitalToStartWith != null) {
                            createAndThenJoinZone();
                        } else {
                            throw new RuntimeException(
                                    "Neither zoneId nor capitalToStartWith were set"
                            );
                        }

                    }

                });

                break;
            case DISCONNECTING:
                break;
            case DISCONNECTED:

                mainHandler.post(new Runnable() {

                    @Override
                    public void run() {

                        if (listener != null) {
                            listener.onDisconnected();
                        }

                    }

                });

                break;
        }

    }

    public void quit() {
        // TODO
//        serverConnection.sendCommand(
//                new QuitZoneCommand(zoneStore.getZoneId()),
//                new ServerConnection.ResponseCallback() {
//
//                    @Override
//                    void onResultReceived(ResultResponse resultResponse) {
//                        QuitZoneResponse$ quitZoneResponse = (QuitZoneResponse$) resultResponse;
//
//        if(listener != null) {
//            listener.onQuit();
//        }
//
//                        log.debug("zoneQuit={}", quitZoneResponse);
//
//                    }
//
//                }
//        );
    }

    public void setCapitalToStartWith(BigDecimal capitalToStartWith) {
        this.capitalToStartWith = capitalToStartWith;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setZoneId(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

}
