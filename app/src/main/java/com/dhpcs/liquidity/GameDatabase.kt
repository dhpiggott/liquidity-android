package com.dhpcs.liquidity

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import com.dhpcs.liquidity.provider.LiquidityContract

class GameDatabase(private val contentResolver: ContentResolver) {

    fun insertGame(zoneId: String,
                   created: Long,
                   expires: Long,
                   name: String?
    ): Long {
        val contentValues = ContentValues()
        contentValues.put(LiquidityContract.Games.ZONE_ID, zoneId)
        contentValues.put(LiquidityContract.Games.CREATED, created)
        contentValues.put(LiquidityContract.Games.EXPIRES, expires)
        contentValues.put(LiquidityContract.Games.NAME, name)
        return ContentUris.parseId(
                contentResolver.insert(
                        LiquidityContract.Games.CONTENT_URI,
                        contentValues
                )
        )
    }

    fun checkAndUpdateGame(zoneId: String, name: String?): Long? {
        val existingEntry = contentResolver.query(
                LiquidityContract.Games.CONTENT_URI,
                arrayOf(LiquidityContract.Games.ID, LiquidityContract.Games.NAME),
                "${LiquidityContract.Games.ZONE_ID} = ?",
                arrayOf(zoneId), null
        )
        return existingEntry?.use {
            if (!existingEntry.moveToFirst()) {
                null
            } else {
                val gameId = existingEntry.getLong(
                        existingEntry.getColumnIndexOrThrow(
                                LiquidityContract.Games.ID
                        )
                )
                if (existingEntry.getString(
                                existingEntry.getColumnIndexOrThrow(
                                        LiquidityContract.Games.NAME
                                )
                        ) != name) {
                    val contentValues = ContentValues()
                    contentValues.put(LiquidityContract.Games.NAME, name)
                    contentResolver.update(
                            ContentUris.withAppendedId(
                                    LiquidityContract.Games.CONTENT_URI,
                                    gameId
                            ),
                            contentValues,
                            null,
                            null
                    )
                }
                gameId
            }
        }
    }

}