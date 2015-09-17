package com.dhpcs.liquidity.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.dhpcs.liquidity.LiquidityApplication;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.ZoneId;
import com.dhpcs.liquidity.provider.LiquidityContract;

import org.joda.time.Instant;

import java.util.UUID;

public class GamesFragment extends Fragment implements AdapterView.OnItemClickListener,
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final int GAMES_LOADER = 0;

    private static final long REFRESH_INTERVAL = 60_000;
    private final Handler refreshHandler = new Handler();
    private final Runnable refreshRunnable = new Runnable() {

        @Override
        public void run() {
            getLoaderManager().restartLoader(GAMES_LOADER, null, GamesFragment.this);
        }

    };
    private Listener listener;
    private SimpleCursorAdapter gamesAdapter;

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        listener = (Listener) activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getLoaderManager().initLoader(GAMES_LOADER, null, this);

        gamesAdapter = new SimpleCursorAdapter(
                getActivity(),
                R.layout.linearlayout_game,
                null,
                new String[]{
                        LiquidityContract.Games.NAME,
                        LiquidityContract.Games.CREATED,
                        LiquidityContract.Games.EXPIRES
                },
                new int[]{
                        R.id.textview_name,
                        R.id.textview_created,
                        R.id.textview_expires
                },
                0
        );

        gamesAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {

            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                boolean bound = false;
                if (columnIndex == cursor.getColumnIndexOrThrow(LiquidityContract.Games.CREATED)) {
                    bound = true;
                    long createdTimeMillis = cursor.getLong(columnIndex);
                    long currentTimeMillis = System.currentTimeMillis();
                    ((TextView) view).setText(
                            getActivity().getString(
                                    R.string.game_created_format_string,
                                    LiquidityApplication.getRelativeTimeSpanString(
                                            getActivity(),
                                            new Instant(createdTimeMillis),
                                            new Instant(
                                                    currentTimeMillis < createdTimeMillis
                                                            ?
                                                            createdTimeMillis
                                                            :
                                                            currentTimeMillis
                                            ),
                                            REFRESH_INTERVAL
                                    )
                            )
                    );
                }
                if (columnIndex == cursor.getColumnIndexOrThrow(LiquidityContract.Games.EXPIRES)) {
                    bound = true;
                    long expiresTimeMillis = cursor.getLong(columnIndex);
                    long currentTimeMillis = System.currentTimeMillis();
                    ((TextView) view).setText(
                            getActivity().getString(
                                    R.string.game_expires_format_string,
                                    LiquidityApplication.getRelativeTimeSpanString(
                                            getActivity(),
                                            new Instant(expiresTimeMillis),
                                            new Instant(
                                                    currentTimeMillis >= expiresTimeMillis
                                                            ?
                                                            expiresTimeMillis
                                                            :
                                                            currentTimeMillis
                                            ),
                                            REFRESH_INTERVAL
                                    )
                            )
                    );
                }
                return bound;
            }

        });
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case GAMES_LOADER:

                return new CursorLoader(
                        getActivity(),
                        LiquidityContract.Games.CONTENT_URI,
                        new String[]{
                                LiquidityContract.Games._ID,
                                LiquidityContract.Games.ZONE_ID,
                                LiquidityContract.Games.CREATED,
                                LiquidityContract.Games.EXPIRES,
                                LiquidityContract.Games.NAME
                        },
                        LiquidityContract.Games.EXPIRES + " > ?",
                        new String[]{
                                Long.toString(System.currentTimeMillis())
                        },
                        LiquidityContract.Games.CREATED + " DESC"
                );

            default:
                return null;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_games, container, false);

        ListView listViewGames = (ListView) view.findViewById(R.id.listview_games);
        listViewGames.setAdapter(gamesAdapter);
        listViewGames.setEmptyView(view.findViewById(R.id.textview_empty));
        listViewGames.setOnItemClickListener(this);

        return view;
    }

    @Override
    public void onDestroy() {
        refreshHandler.removeCallbacks(refreshRunnable);
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (listener != null) {
            Cursor cursor = ((Cursor) parent.getItemAtPosition(position));
            listener.onGameClicked(
                    id,
                    new ZoneId(
                            UUID.fromString(
                                    cursor.getString(cursor.getColumnIndexOrThrow(
                                            LiquidityContract.Games.ZONE_ID
                                    ))
                            )
                    ),
                    cursor.getString(cursor.getColumnIndexOrThrow(
                            LiquidityContract.Games.NAME
                    ))
            );
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        gamesAdapter.changeCursor(null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        gamesAdapter.changeCursor(data);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    public interface Listener {

        void onGameClicked(long gameId, ZoneId zoneId, String gameName);

    }

}
