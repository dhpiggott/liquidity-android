package com.dhpcs.liquidity;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;

import com.dhpcs.liquidity.models.Account;
import com.dhpcs.liquidity.models.AccountCreated;
import com.dhpcs.liquidity.models.AccountId;
import com.dhpcs.liquidity.models.ClientJoinedZone;
import com.dhpcs.liquidity.models.ClientQuitZone;
import com.dhpcs.liquidity.models.CommandResultResponse;
import com.dhpcs.liquidity.models.CreateAccount;
import com.dhpcs.liquidity.models.CreateMember;
import com.dhpcs.liquidity.models.CreateZone;
import com.dhpcs.liquidity.models.JoinZone;
import com.dhpcs.liquidity.models.Member;
import com.dhpcs.liquidity.models.MemberCreated;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.models.Notification;
import com.dhpcs.liquidity.models.PublicKey;
import com.dhpcs.liquidity.models.QuitZone;
import com.dhpcs.liquidity.models.Zone;
import com.dhpcs.liquidity.models.Zone$;
import com.dhpcs.liquidity.models.ZoneCreated;
import com.dhpcs.liquidity.models.ZoneId;
import com.dhpcs.liquidity.models.ZoneJoined;
import com.dhpcs.liquidity.models.ZoneNotification;
import com.dhpcs.liquidity.models.ZoneQuit$;
import com.dhpcs.liquidity.models.ZoneState;
import com.dhpcs.liquidity.models.ZoneTerminated;

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

        void onCreated(ZoneId zoneId);

        void onDisconnected();

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

    // TODO: Only one?
    private final Set<Listener> listeners = new HashSet<>();
    private final Context context;
    private final Handler mainHandler;
    private final ServerConnection serverConnection;

    private ZoneStore zoneStore;
    private Set<PublicKey> connectedClients;
    private Map<MemberId, Member> connectedMembers;
    private Map<MemberId, Member> otherMembers;
    private Map<MemberId, Member> userMembers;
    private Map<AccountId, BigDecimal> memberBalances;

    public MonopolyGame(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.serverConnection = new ServerConnection(context, this, this);
    }

    public void addListener(Listener listener) {
        // TODO: Call them if we're connected etc.
        this.listeners.add(listener);
    }

    public void connect() {
        serverConnection.connect();
    }

    public void createBanker() {

        serverConnection.sendCommand(
                new CreateMember(
                        zoneStore.getZoneId(),
                        new Member(
                                BANK_MEMBER_NAME,
                                ClientKey.getInstance(context).getPublicKey()
                        )
                ),
                new ServerConnection.CommandResponseCallback() {

                    @Override
                    public void onResultReceived(CommandResultResponse commandResultResponse) {
                        final MemberCreated memberCreated = (MemberCreated) commandResultResponse;

                        log.debug("memberCreated={}", memberCreated);

                        mainHandler.post(new Runnable() {

                            @Override
                            public void run() {
                                serverConnection.sendCommand(
                                        new CreateAccount(
                                                zoneStore.getZoneId(),
                                                new Account(
                                                        BANK_ACCOUNT_NAME,
                                                        JavaConversions.asScalaSet(
                                                                Collections.singleton(memberCreated.memberId())
                                                        ).<MemberId>toSet()
                                                )
                                        ),
                                        new ServerConnection.CommandResponseCallback() {

                                            @Override
                                            public void onResultReceived(CommandResultResponse commandResultResponse) {
                                                AccountCreated accountCreated = (AccountCreated) commandResultResponse;

                                                log.debug("accountCreated={}", accountCreated);

                                            }

                                        }
                                );
                            }

                        });

                    }

                }
        );

    }

    public void createPlayer() {

        final String playerName = getUserName(
                context,
                ContactsContract.Profile.DISPLAY_NAME
        );

        log.debug("playerName={}", playerName);

        serverConnection.sendCommand(
                new CreateMember(
                        zoneStore.getZoneId(),
                        new Member(
                                playerName,
                                ClientKey.getInstance(context).getPublicKey()
                        )
                ),
                new ServerConnection.CommandResponseCallback() {

                    @Override
                    public void onResultReceived(CommandResultResponse commandResultResponse) {
                        final MemberCreated memberCreated = (MemberCreated) commandResultResponse;

                        log.debug("memberCreated={}", memberCreated);

                        mainHandler.post(new Runnable() {

                            @Override
                            public void run() {
                                serverConnection.sendCommand(
                                        new CreateAccount(
                                                zoneStore.getZoneId(),
                                                new Account(
                                                        playerName + ACCOUNT_NAME_SUFFIX,
                                                        JavaConversions.asScalaSet(
                                                                Collections.singleton(memberCreated.memberId())
                                                        ).<MemberId>toSet()
                                                )
                                        ),
                                        new ServerConnection.CommandResponseCallback() {

                                            @Override
                                            public void onResultReceived(CommandResultResponse commandResultResponse) {
                                                AccountCreated accountCreated = (AccountCreated) commandResultResponse;

                                                log.debug("accountCreated={}", accountCreated);

                                            }

                                        }
                                );
                            }

                        });

                    }

                }
        );

    }

    public void createAndJoinZone() {

        final String playerName = getUserName(
                context,
                ContactsContract.Profile.DISPLAY_NAME
        );

        log.debug("playerName={}", playerName);

        serverConnection.sendCommand(
                new CreateZone(
                        GAME_NAME_PREFIX + playerName,
                        ZONE_TYPE.typeName),
                new ServerConnection.CommandResponseCallback() {

                    @Override
                    public void onResultReceived(CommandResultResponse commandResultResponse) {
                        final ZoneCreated zoneCreated = (ZoneCreated) commandResultResponse;

                        mainHandler.post(new Runnable() {

                            @Override
                            public void run() {
                                zoneStore = new ZoneStore(context, ZONE_TYPE.typeName, zoneCreated.zoneId());
                                connectedClients = new HashSet<>();
                                connectedMembers = new HashMap<>();
                                otherMembers = new HashMap<>();
                                userMembers = new HashMap<>();
                                memberBalances = new HashMap<>();

                                for (Listener listener : listeners) {
                                    listener.onCreated(zoneCreated.zoneId());
                                }

                                join(zoneCreated.zoneId());
                            }

                        });

                    }

                });

    }

    public void disconnect() {
        serverConnection.disconnect();
    }

    public void join(ZoneId zoneId) {
        zoneStore = new ZoneStore(context, ZONE_TYPE.typeName, zoneId);
        connectedClients = new HashSet<>();
        connectedMembers = new HashMap<>();
        otherMembers = new HashMap<>();
        userMembers = new HashMap<>();
        memberBalances = new HashMap<>();
        serverConnection.sendCommand(
                new JoinZone(zoneStore.getZoneId()),
                new ServerConnection.CommandResponseCallback() {

                    @Override
                    void onResultReceived(CommandResultResponse commandResultResponse) {
                        final ZoneJoined zoneJoined = (ZoneJoined) commandResultResponse;

                        mainHandler.post(new Runnable() {

                            @Override
                            public void run() {

                                zoneStore.save(zoneJoined.zone());

                                connectedClients.addAll(
                                        JavaConverters.setAsJavaSetConverter(
                                                zoneJoined.connectedClients()
                                        ).asJava()
                                );

                                // TODO: Name

                                notifyMembersChanged(zoneJoined.zone());

                                // TODO: Balances

                            }

                        });

                    }

                }
        );
    }

    private void notifyMembersChanged(Zone nextZone) {

        userMembers.clear();
        userMembers.putAll(
                Zone$.MODULE$.userMembersAsJavaMap(
                        nextZone,
                        ClientKey.getInstance(context).getPublicKey()
                )
        );

        for (Listener listener : listeners) {
            listener.onUserMembersChanged(userMembers);
        }

        otherMembers.clear();
        otherMembers.putAll(
                Zone$.MODULE$.otherMembersAsJavaMap(
                        nextZone,
                        ClientKey.getInstance(context).getPublicKey()
                )
        );

        for (Listener listener : listeners) {
            listener.onOtherMembersChanged(otherMembers);
        }

        connectedMembers.clear();
        connectedMembers.putAll(
                Zone$.MODULE$.connectedMembersAsJavaMap(
                        otherMembers,
                        connectedClients
                )
        );

        for (Listener listener : listeners) {
            listener.onConnectedMembersChanged(connectedMembers);
        }

    }

    @Override
    public void onNotificationReceived(final Notification notification) {

        log.debug("notification={}", notification);

        if (notification instanceof ZoneNotification) {
            final ZoneNotification zoneNotification = (ZoneNotification) notification;

            mainHandler.post(new Runnable() {

                @Override
                public void run() {
                    if (zoneNotification.zoneId() == zoneStore.getZoneId()) {

                        if (notification instanceof ZoneState) {
                            ZoneState zoneState = (ZoneState) zoneNotification;

                            Zone previousZone = zoneStore.load();
                            Zone nextZone = zoneState.zone();

                            zoneStore.save(nextZone);

                            if (!previousZone.name().equals(nextZone.name())) {
                                // TODO
                            }

                            if (!previousZone.members().equals(nextZone.members())) {

                                notifyMembersChanged(nextZone);

                            }

                            if (!previousZone.transactions().equals(nextZone.transactions())) {
                                // TODO: Balances
                            }

                            zoneStore.save(zoneState.zone());

                        } else if (notification instanceof ZoneTerminated) {

                            connectedClients= null;
                            connectedMembers = null;
                            otherMembers = null;
                            userMembers = null;
                            memberBalances = null;
                            serverConnection.sendCommand(
                                    new JoinZone(
                                            zoneStore.getZoneId()
                                    ),
                                    new ServerConnection.CommandResponseCallback() {

                                        @Override
                                        void onResultReceived(CommandResultResponse commandResultResponse) {
                                            ZoneJoined zoneJoined = (ZoneJoined) commandResultResponse;
                                            // TODO:
                                        }

                                    });

                        } else if (notification instanceof ClientJoinedZone) {
                            ClientJoinedZone clientJoinedZone = (ClientJoinedZone) zoneNotification;

                            connectedClients.add(clientJoinedZone.publicKey());

                            connectedMembers.putAll(
                                    Zone$.MODULE$.connectedMembersAsJavaMap(
                                            otherMembers,
                                            Collections.singleton(clientJoinedZone.publicKey())
                                    )
                            );

                            for (Listener listener : listeners) {
                                listener.onConnectedMembersChanged(connectedMembers);
                            }

                        } else if (notification instanceof ClientQuitZone) {
                            ClientQuitZone clientQuitZone = (ClientQuitZone) zoneNotification;

                            connectedClients.remove(clientQuitZone.publicKey());

                            connectedMembers.keySet().removeAll(
                                    Zone$.MODULE$.connectedMembersAsJavaMap(
                                            otherMembers,
                                            Collections.singleton(clientQuitZone.publicKey())
                                    ).keySet()
                            );

                            for (Listener listener : listeners) {
                                listener.onConnectedMembersChanged(connectedMembers);
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
                        for (Listener listener : listeners) {
                            listener.onConnected();
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
                        for (Listener listener : listeners) {
                            listener.onDisconnected();
                        }
                    }

                });

                break;
        }

    }

    public void quit() {
        serverConnection.sendCommand(
                new QuitZone(zoneStore.getZoneId()),
                new ServerConnection.CommandResponseCallback() {

                    @Override
                    void onResultReceived(CommandResultResponse commandResultResponse) {
                        ZoneQuit$ zoneQuit = (ZoneQuit$) commandResultResponse;

                        log.debug("zoneQuit={}", zoneQuit);

                    }

                }
        );
    }

    public void removeListener(Listener listener) {
        this.listeners.remove(listener);
    }

}
