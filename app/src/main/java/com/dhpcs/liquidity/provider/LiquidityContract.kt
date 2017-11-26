package com.dhpcs.liquidity.provider

import android.content.ContentResolver
import android.net.Uri
import android.provider.BaseColumns

object LiquidityContract {

    internal const val AUTHORITY = "com.dhpcs.liquidity.provider"
    private val CONTENT_URI = Uri.parse("content://$AUTHORITY")

    object Games {

        internal const val BASE_PATH = "games"
        val CONTENT_URI: Uri = Uri.withAppendedPath(LiquidityContract.CONTENT_URI, BASE_PATH)

        private const val BASE_CONTENT_TYPE = "vnd.com.dhpcs.liquidity.game"
        internal const val CONTENT_ITEM_TYPE =
                "${ContentResolver.CURSOR_ITEM_BASE_TYPE}/$BASE_CONTENT_TYPE"
        internal val CONTENT_TYPE =
                "${ContentResolver.CURSOR_DIR_BASE_TYPE}/$BASE_CONTENT_TYPE"

        const val ID = BaseColumns._ID
        const val ZONE_ID = "zone_id"
        const val CREATED = "created"
        const val EXPIRES = "expires"
        const val NAME = "name"
        internal const val SORT_ORDER_DEFAULT = "$CREATED ASC"

    }

}
