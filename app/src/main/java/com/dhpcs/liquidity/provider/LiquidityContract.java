package com.dhpcs.liquidity.provider;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;

public class LiquidityContract {

    static final String AUTHORITY = "com.dhpcs.liquidity.provider";

    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    public static class Games implements BaseColumns {

        private static final String BASE_CONTENT_TYPE = "vnd.com.dhpcs.liquidity.game";

        static final String BASE_PATH = "games";

        static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/"
                + BASE_CONTENT_TYPE;

        static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/"
                + BASE_CONTENT_TYPE;

        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                LiquidityContract.CONTENT_URI,
                BASE_PATH
        );

        public static final String _ID = BaseColumns._ID;

        public static final String ZONE_ID = "zone_id";

        public static final String GAME_TYPE = "game_type";

        public static final String NAME = "name";

        public static final String CREATED = "created";

        public static final String SORT_ORDER_DEFAULT = CREATED + " ASC";

    }

}