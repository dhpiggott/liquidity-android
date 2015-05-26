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

import com.dhpcs.liquidity.ClientKey;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.Member;
import com.dhpcs.liquidity.models.MemberId;

import java.util.ArrayList;
import java.util.List;

// TODO: Extend ListFragment?
public class PlayersFragment extends Fragment implements AdapterView.OnItemClickListener {

    public interface Listener {

        void onPlayerClicked(Member member);

    }

    private Listener listener;

    private List<Member> members;
    private ListAdapter listAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        members = new ArrayList<>();
        // TODO
//        Iterator<ZoneStore> iterator = new ZoneStore.ZoneStoreIterator(getActivity(), gameType);
//        while (iterator.hasNext()) {
//            members.add(iterator.next());
//        }
        members.add(new Member("Player 1", ClientKey.getInstance(getActivity()).getPublicKey()));
        members.add(new Member("Player 2", ClientKey.getInstance(getActivity()).getPublicKey()));
        members.add(new Member("Player 3", ClientKey.getInstance(getActivity()).getPublicKey()));

        listAdapter = new ArrayAdapter<>(
                getActivity(),
                // TODO
                android.R.layout.simple_list_item_1,
                android.R.id.text1,
                members
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_player_list, container, false);

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
                    + " must implement PlayersFragment.Listener");
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
            listener.onPlayerClicked(
                    members.get(position)
            );
        }
    }

}
