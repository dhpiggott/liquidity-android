package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;

import com.dhpcs.liquidity.GameType;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.ZoneStore;
import com.dhpcs.liquidity.models.Zone;
import com.dhpcs.liquidity.models.ZoneId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

// TODO: Extend ListFragment?
public class GamesFragment extends Fragment implements AdapterView.OnItemClickListener {

    public static final String GAME_TYPE = "game_type";

    public interface Listener {

        void onGameClicked(ZoneId zoneId);

    }

    public static GamesFragment newInstance(GameType gameType) {
        GamesFragment fragment = new GamesFragment();
        Bundle args = new Bundle();
        args.putSerializable(GAME_TYPE, gameType);
        fragment.setArguments(args);
        return fragment;
    }

    private Listener listener;

    private List<ZoneStore> zoneStores;
    private ListAdapter listAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GameType gameType = (GameType) getArguments().getSerializable(GAME_TYPE);
        zoneStores = new ArrayList<>();
        Iterator<ZoneStore> iterator = new ZoneStore.ZoneStoreIterator(getActivity(), gameType);
        while (iterator.hasNext()) {
            zoneStores.add(iterator.next());
        }

        Collections.sort(zoneStores, new Comparator<ZoneStore>() {

            @Override
            public int compare(ZoneStore lhs, ZoneStore rhs) {
                Zone lhsZone = lhs.load();
                Zone rhsZone = rhs.load();
                if (lhsZone.created() > rhsZone.created()) {
                    return 1;
                } else if (lhsZone.created() < rhsZone.created()) {
                    return -1;
                } else {
                    return 0;
                }
            }

        });

        listAdapter = new ArrayAdapter<>(
                getActivity(),
                // TODO
                android.R.layout.simple_list_item_1,
                android.R.id.text1,
                zoneStores
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_games, container, false);

        AbsListView absListViewGames = (AbsListView) view.findViewById(android.R.id.list);
        absListViewGames.setAdapter(listAdapter);
        absListViewGames.setEmptyView(view.findViewById(android.R.id.empty));
        absListViewGames.setOnItemClickListener(this);

        return view;
    }

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
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (listener != null) {
            listener.onGameClicked(
                    zoneStores.get(position).getZoneId()
            );
        }
    }

}
