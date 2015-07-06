package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.dhpcs.liquidity.GameType;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.ZoneId;
import com.dhpcs.liquidity.provider.LiquidityContract;

import java.text.DateFormat;
import java.util.UUID;

// TODO: Extend ListFragment?
// Example: http://developer.android.com/guide/components/loaders.html
public class GamesFragment extends Fragment
        implements AdapterView.OnItemClickListener,
        AdapterView.OnItemLongClickListener,
        LoaderManager.LoaderCallbacks<Cursor> {

    public static final String GAME_TYPE = "game_type";

    private static final int GAMES_LOADER = 0;

    public interface Listener {

        void onGameClicked(long gameId, ZoneId zoneId, String gameName);

    }

    public static GamesFragment newInstance(GameType gameType) {
        GamesFragment fragment = new GamesFragment();
        Bundle args = new Bundle();
        args.putSerializable(GAME_TYPE, gameType);
        fragment.setArguments(args);
        return fragment;
    }

    private Listener listener;

    private SimpleCursorAdapter simpleCursorAdapter;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (Listener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement GamesFragment.Listener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getLoaderManager().initLoader(GAMES_LOADER, null, this);

        simpleCursorAdapter = new SimpleCursorAdapter(
                getActivity(),
                android.R.layout.simple_list_item_2,
                null,
                new String[]{
                        LiquidityContract.Games.NAME,
                        LiquidityContract.Games.CREATED
                },
                new int[]{
                        android.R.id.text1,
                        android.R.id.text2
                },
                0
        );
        simpleCursorAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {

            private final DateFormat dateFormat = DateFormat.getDateTimeInstance();

            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                boolean bound = false;
                if (columnIndex == cursor.getColumnIndexOrThrow(LiquidityContract.Games.CREATED)) {
                    bound = true;
                    ((TextView) view).setText(
                            getActivity().getString(
                                    R.string.created_format_string,
                                    dateFormat.format(cursor.getLong(columnIndex))
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
                                LiquidityContract.Games.NAME,
                                LiquidityContract.Games.CREATED
                        },
                        LiquidityContract.Games.GAME_TYPE + " = ?",
                        new String[]{
                                ((GameType) getArguments().getSerializable(GAME_TYPE)).name()
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

        AbsListView absListViewGames = (AbsListView) view.findViewById(android.R.id.list);
        absListViewGames.setAdapter(simpleCursorAdapter);
        absListViewGames.setEmptyView(view.findViewById(android.R.id.empty));
        absListViewGames.setOnItemClickListener(this);
        absListViewGames.setOnItemLongClickListener(this);

        return view;
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
                    new ZoneId(UUID.fromString(
                            cursor.getString(cursor.getColumnIndexOrThrow(
                                    LiquidityContract.Games.ZONE_ID
                            ))
                    )),
                    cursor.getString(cursor.getColumnIndexOrThrow(
                            LiquidityContract.Games.NAME
                    ))
            );
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        // TODO
        getActivity().getContentResolver().delete(
                ContentUris.withAppendedId(LiquidityContract.Games.CONTENT_URI, id),
                null,
                null
        );
        return true;
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        simpleCursorAdapter.changeCursor(null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        simpleCursorAdapter.changeCursor(data);
    }

}
