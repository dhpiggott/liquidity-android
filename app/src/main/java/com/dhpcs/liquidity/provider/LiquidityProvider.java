package com.dhpcs.liquidity.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

public class LiquidityProvider extends ContentProvider {

    private static class LiquidityDatabaseHelper extends SQLiteOpenHelper {

        private static final String DATABASE_NAME = "liquidity.db";
        private static final int DATABASE_VERSION = 1;

        private static final String GAMES_TABLE_NAME = "game";

        private static final String CREATE_GAMES_SQL =
                "CREATE TABLE " + GAMES_TABLE_NAME
                        + "("
                        + LiquidityContract.Games._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + LiquidityContract.Games.ZONE_ID + " TEXT NOT NULL,"
                        + LiquidityContract.Games.NAME + " TEXT,"
                        + LiquidityContract.Games.CREATED + " INTEGER NOT NULL,"
                        + "UNIQUE(" + LiquidityContract.Games.ZONE_ID + ")"
                        + ");";

        public LiquidityDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL(CREATE_GAMES_SQL);
        }

        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            throw new SQLiteException(
                    "Can't upgrade database from version " + oldVersion + " to " + newVersion
            );
        }

    }

    private static final UriMatcher URI_MATCHER;

    private static final int URI_TYPE_GAMES = 0;
    private static final int URI_TYPE_GAME_ID = 1;

    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(
                LiquidityContract.AUTHORITY,
                LiquidityContract.Games.BASE_PATH,
                URI_TYPE_GAMES
        );
        URI_MATCHER.addURI(
                LiquidityContract.AUTHORITY,
                LiquidityContract.Games.BASE_PATH + "/#",
                URI_TYPE_GAME_ID
        );
    }

    private LiquidityDatabaseHelper databaseHelper;

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {

        int rowsAffected;
        switch (URI_MATCHER.match(uri)) {
            case URI_TYPE_GAMES:

                rowsAffected = databaseHelper.getWritableDatabase().delete(
                        LiquidityDatabaseHelper.GAMES_TABLE_NAME,
                        selection,
                        selectionArgs
                );

                break;
            case URI_TYPE_GAME_ID:

                String where = LiquidityContract.Games._ID + " = " + uri.getLastPathSegment();
                if (!TextUtils.isEmpty(selection)) {
                    where += " AND " + selection;
                }
                rowsAffected = databaseHelper.getWritableDatabase().delete(
                        LiquidityDatabaseHelper.GAMES_TABLE_NAME,
                        where,
                        selectionArgs
                );

                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        if (rowsAffected > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return rowsAffected;
    }

    @Override
    public String getType(Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case URI_TYPE_GAMES:
                return LiquidityContract.Games.CONTENT_TYPE;
            case URI_TYPE_GAME_ID:
                return LiquidityContract.Games.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {

        long id;
        switch (URI_MATCHER.match(uri)) {
            case URI_TYPE_GAMES:

                id = databaseHelper.getWritableDatabase().insert(
                        LiquidityDatabaseHelper.GAMES_TABLE_NAME,
                        null,
                        values
                );

                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);

        return ContentUris.withAppendedId(uri, id);
    }

    @Override
    public boolean onCreate() {
        databaseHelper = new LiquidityDatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri,
                        String[] projection,
                        String selection,
                        String[] selectionArgs,
                        String sortOrder) {

        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(LiquidityDatabaseHelper.GAMES_TABLE_NAME);
        if (TextUtils.isEmpty(sortOrder)) {
            sortOrder = LiquidityContract.Games.SORT_ORDER_DEFAULT;
        }
        switch (URI_MATCHER.match(uri)) {
            case URI_TYPE_GAMES:
                break;
            case URI_TYPE_GAME_ID:

                queryBuilder.appendWhere(
                        LiquidityContract.Games._ID + " = " + uri.getLastPathSegment()
                );

                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        Cursor cursor = queryBuilder.query(
                databaseHelper.getWritableDatabase(),
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder
        );
        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

        int rowsAffected;
        switch (URI_MATCHER.match(uri)) {
            case URI_TYPE_GAMES:

                rowsAffected = databaseHelper.getWritableDatabase().update(
                        LiquidityDatabaseHelper.GAMES_TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );

                break;
            case URI_TYPE_GAME_ID:

                String where = LiquidityContract.Games._ID + " = " + uri.getLastPathSegment();
                if (!TextUtils.isEmpty(selection)) {
                    where += " AND " + selection;
                }
                rowsAffected = databaseHelper.getWritableDatabase().update(
                        LiquidityDatabaseHelper.GAMES_TABLE_NAME,
                        values,
                        where,
                        selectionArgs
                );

                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }

        if (rowsAffected > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return rowsAffected;
    }

}
