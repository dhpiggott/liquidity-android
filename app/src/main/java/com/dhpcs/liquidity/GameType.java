package com.dhpcs.liquidity;

public enum GameType {

    MONOPOLY("monopoly"),

    TEST("test");

    public final String typeName;

    GameType(String typeName) {
        this.typeName = typeName;
    }

}
