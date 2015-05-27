package com.dhpcs.liquidity;

import android.content.Context;

import com.dhpcs.liquidity.models.CreateZone;
import com.dhpcs.liquidity.models.ZoneCreated;
import com.dhpcs.liquidity.models.ZoneId;

public class MonopolyGame {

    private static final GameType ZONE_TYPE = GameType.MONOPOLY;

    public static CreateZone getCreateZoneCommand(String name) {
        return new CreateZone(name, ZONE_TYPE.typeName);
    }

    public static MonopolyGame initialize(
            ServerConnection serverConnection,
            Context context,
            ZoneCreated zoneCreatedEvent) {
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
