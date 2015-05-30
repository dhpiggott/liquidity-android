package com.dhpcs.liquidity;

import android.content.Context;

import com.dhpcs.liquidity.models.Account;
import com.dhpcs.liquidity.models.CommandResponse;
import com.dhpcs.liquidity.models.CreateAccount;
import com.dhpcs.liquidity.models.CreateMember;
import com.dhpcs.liquidity.models.Member;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.models.ZoneCreated;
import com.dhpcs.liquidity.models.ZoneId;

import java.util.Collections;
import java.util.UUID;

import scala.collection.JavaConversions;

public class MonopolyGame {

    private static final GameType ZONE_TYPE = GameType.MONOPOLY;

    public static MonopolyGame initialize(
            final ServerConnection serverConnection,
            final Context context,
            final ZoneCreated zoneCreatedEvent) {
        // TODO: Reliability
        serverConnection.sendCommand(
                new CreateMember(
                        zoneCreatedEvent.zoneId(),
                        new Member(
                                // TODO
                                "Bank",
                                ClientKey.getInstance(context).getPublicKey()
                        )
                ),
                new ServerConnection.CommandResponseCallback() {

                    @Override
                    public void onCommandResponseReceived(CommandResponse commandResponse) {

                        // TODO: Need member ID from above call
                        serverConnection.sendCommand(
                                new CreateAccount(
                                        zoneCreatedEvent.zoneId(),
                                        new Account(
                                                "Bank",
                                                JavaConversions.asScalaSet(
                                                        Collections.singleton(new MemberId(UUID.randomUUID()))
                                                ).<MemberId>toSet()
                                        )
                                ),
                                new ServerConnection.CommandResponseCallback() {

                                    @Override
                                    public void onCommandResponseReceived(CommandResponse commandResponse) {
                                        // TODO: Reliability
                                        serverConnection.sendCommand(
                                                new CreateMember(
                                                        zoneCreatedEvent.zoneId(),
                                                        new Member(
                                                                // TODO
                                                                "Dave",
                                                                ClientKey.getInstance(context).getPublicKey()
                                                        )
                                                ),
                                                new ServerConnection.CommandResponseCallback() {
                                                    @Override
                                                    public void onCommandResponseReceived(CommandResponse commandResponse) {

                                                        // TODO: Need member ID from above call
                                                        serverConnection.sendCommand(
                                                                new CreateAccount(
                                                                        zoneCreatedEvent.zoneId(),
                                                                        new Account(
                                                                                "Dave",
                                                                                JavaConversions.asScalaSet(
                                                                                        Collections.singleton(new MemberId(UUID.randomUUID()))
                                                                                ).<MemberId>toSet()
                                                                        )
                                                                ),
                                                                new ServerConnection.CommandResponseCallback() {
                                                                    @Override
                                                                    public void onCommandResponseReceived(CommandResponse commandResponse) {

                                                                    }
                                                                }
                                                        );
                                                    }
                                                }
                                        );
                                    }

                                }
                        );
                    }

                }
        );
        return new MonopolyGame(serverConnection, context, zoneCreatedEvent.zoneId());
    }

    public static MonopolyGame rejoin(
            ServerConnection serverConnection,
            Context context,
            ZoneId zoneId) {
        return new MonopolyGame(serverConnection, context, zoneId);
    }

    private final ServerConnection serverConnection;
    private final ZoneStore zoneStore;
    private final ZoneId zoneId;

    private MonopolyGame(
            ServerConnection serverConnection,
            Context context,
            ZoneId zoneId) {
        this.serverConnection = serverConnection;
        this.zoneId = zoneId;
        this.zoneStore = new ZoneStore(context, ZONE_TYPE.typeName, zoneId);
    }

    public void join() {
//        serverConnection.execute(new JoinZone(zoneId));
    }

//    public void quit() {
//        serverConnection.execute(new QuitZone(zoneId));
//    }

}
