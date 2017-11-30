package com.dhpcs.liquidity.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import android.text.TextUtils

class LiquidityProvider : ContentProvider() {

    companion object {

        private class LiquidityDatabaseHelper internal constructor(context: Context) :
                SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

            companion object {

                private const val DATABASE_NAME = "liquidity.db"
                private const val DATABASE_VERSION = 1

                internal const val GAMES_TABLE_NAME = "game"
                private val CREATE_GAMES_SQL = """
                    |CREATE TABLE $GAMES_TABLE_NAME (
                    |  ${LiquidityContract.Games.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                    |  ${LiquidityContract.Games.ZONE_ID} TEXT NOT NULL,
                    |  ${LiquidityContract.Games.CREATED} INTEGER NOT NULL,
                    |  ${LiquidityContract.Games.EXPIRES} INTEGER NOT NULL,
                    |  ${LiquidityContract.Games.NAME} TEXT,
                    |  UNIQUE(${LiquidityContract.Games.ZONE_ID})
                    |);
                    """.trimMargin()

            }

            override fun onCreate(database: SQLiteDatabase) = database.execSQL(CREATE_GAMES_SQL)

            override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                throw SQLiteException(
                        "Can't upgrade database from version $oldVersion to $newVersion"
                )
            }

        }

        private const val URI_TYPE_GAMES = 0
        private const val URI_TYPE_GAME_ID = 1

        private val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH)

        init {
            URI_MATCHER.addURI(
                    LiquidityContract.AUTHORITY,
                    LiquidityContract.Games.BASE_PATH,
                    URI_TYPE_GAMES
            )
            URI_MATCHER.addURI(
                    LiquidityContract.AUTHORITY,
                    "${LiquidityContract.Games.BASE_PATH}/#",
                    URI_TYPE_GAME_ID
            )
        }

    }

    private var databaseHelper: LiquidityDatabaseHelper? = null

    override fun onCreate(): Boolean {
        databaseHelper = LiquidityDatabaseHelper(context)
        return true
    }

    override fun getType(uri: Uri): String = when (URI_MATCHER.match(uri)) {
        URI_TYPE_GAMES -> LiquidityContract.Games.CONTENT_TYPE
        URI_TYPE_GAME_ID -> LiquidityContract.Games.CONTENT_ITEM_TYPE
        else -> throw IllegalArgumentException("Unsupported URI: $uri")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val id = when (URI_MATCHER.match(uri)) {
            URI_TYPE_GAMES ->
                databaseHelper!!.writableDatabase.insert(
                        LiquidityDatabaseHelper.GAMES_TABLE_NAME, null,
                        values
                )
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
        context.contentResolver.notifyChange(uri, null)
        return ContentUris.withAppendedId(uri, id)
    }

    override fun update(uri: Uri, values: ContentValues?,
                        selection: String?,
                        selectionArgs: Array<String>?
    ): Int {
        val rowsAffected = when (URI_MATCHER.match(uri)) {
            URI_TYPE_GAMES ->
                databaseHelper!!.writableDatabase.update(
                        LiquidityDatabaseHelper.GAMES_TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                )
            URI_TYPE_GAME_ID -> {
                val where = "${LiquidityContract.Games.ID} = ${uri.lastPathSegment}"
                databaseHelper!!.writableDatabase.update(
                        LiquidityDatabaseHelper.GAMES_TABLE_NAME,
                        values,
                        if (TextUtils.isEmpty(selection)) {
                            where
                        } else {
                            "$where AND " + selection
                        },
                        selectionArgs
                )
            }
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
        if (rowsAffected > 0) context.contentResolver.notifyChange(uri, null)
        return rowsAffected
    }

    override fun query(uri: Uri,
                       projection: Array<String>?,
                       selection: String?,
                       selectionArgs: Array<String>?,
                       sortOrder: String?
    ): Cursor? {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.tables = LiquidityDatabaseHelper.GAMES_TABLE_NAME
        when (URI_MATCHER.match(uri)) {
            URI_TYPE_GAMES -> {
            }
            URI_TYPE_GAME_ID ->
                queryBuilder.appendWhere(
                        "${LiquidityContract.Games.ID} = ${uri.lastPathSegment}"
                )
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
        val cursor = queryBuilder.query(
                databaseHelper!!.writableDatabase,
                projection,
                selection,
                selectionArgs, null, null,
                if (TextUtils.isEmpty(sortOrder)) {
                    LiquidityContract.Games.SORT_ORDER_DEFAULT
                } else {
                    sortOrder
                }
        )
        cursor.setNotificationUri(context.contentResolver, uri)
        return cursor
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val rowsAffected = when (URI_MATCHER.match(uri)) {
            URI_TYPE_GAMES ->
                databaseHelper!!.writableDatabase.delete(
                        LiquidityDatabaseHelper.GAMES_TABLE_NAME,
                        selection,
                        selectionArgs
                )
            URI_TYPE_GAME_ID -> {
                val where = "${LiquidityContract.Games.ID} = ${uri.lastPathSegment}"
                databaseHelper!!.writableDatabase.delete(
                        LiquidityDatabaseHelper.GAMES_TABLE_NAME,
                        if (TextUtils.isEmpty(selection)) {
                            where
                        } else {
                            "$where AND " + selection
                        },
                        selectionArgs
                )
            }
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
        if (rowsAffected > 0) context.contentResolver.notifyChange(uri, null)
        return rowsAffected
    }

}
