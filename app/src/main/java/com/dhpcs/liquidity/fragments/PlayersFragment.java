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

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.Member;
import com.dhpcs.liquidity.models.MemberId;

import java.util.Comparator;
import java.util.Map;

// TODO: Extend ListFragment?
public class PlayersFragment extends Fragment implements AdapterView.OnItemClickListener {

    public interface Listener {

        void onPlayerClicked(MemberId memberId);

    }

    private static class PlayerItem {

        public final MemberId memberId;
        public final Member member;

        public PlayerItem(MemberId memberId, Member member) {
            this.memberId = memberId;
            this.member = member;
        }

        @Override
        public String toString() {
            return this.member.name();
        }

    }

    private final Comparator<PlayerItem> playerItemComparator = new Comparator<PlayerItem>() {

        @Override
        public int compare(PlayerItem lhs, PlayerItem rhs) {
            return lhs.member.name().compareTo(rhs.member.name());
        }

    };

    private ArrayAdapter<PlayerItem> listAdapter;

    private Listener listener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        listAdapter = new ArrayAdapter<>(
                getActivity(),
                android.R.layout.simple_list_item_1,
                android.R.id.text1
        );
        listAdapter.setNotifyOnChange(false);

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
                    listAdapter.getItem(position).memberId
            );
        }
    }

    public void onPlayersChanged(Map<MemberId, Member> players) {
        listAdapter.clear();
        for (Map.Entry<MemberId, Member> memberIdMemberEntry : players.entrySet()) {
            listAdapter.add(
                    new PlayerItem(
                            memberIdMemberEntry.getKey(),
                            memberIdMemberEntry.getValue()
                    )
            );
        }
        listAdapter.sort(playerItemComparator);
        listAdapter.notifyDataSetChanged();
    }

}
