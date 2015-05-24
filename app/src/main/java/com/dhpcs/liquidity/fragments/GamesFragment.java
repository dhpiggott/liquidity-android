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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GamesFragment extends Fragment implements AdapterView.OnItemClickListener {

    public static final String GAME_TYPE = "gameType";

    public interface Listener {

        void onGameClicked(String gameZoneId);

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
        View view = inflater.inflate(R.layout.fragment_game, container, false);

        AbsListView absListView = (AbsListView) view.findViewById(android.R.id.list);
        absListView.setAdapter(listAdapter);
        absListView.setEmptyView(view.findViewById(android.R.id.empty));
        absListView.setOnItemClickListener(this);

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
        if (null != listener) {
            listener.onGameClicked(
                    zoneStores.get(position).getZoneId().id().toString()
            );
        }
    }

}
