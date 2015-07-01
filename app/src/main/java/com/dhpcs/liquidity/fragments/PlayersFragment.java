package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.dhpcs.liquidity.MonopolyGame;
import com.dhpcs.liquidity.MonopolyGame.PlayerWithBalanceAndConnectionState;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;
import com.dhpcs.liquidity.models.MemberId;

import java.text.Collator;
import java.util.Comparator;

import scala.Tuple2;
import scala.collection.Iterator;

// TODO: Extend ListFragment? http://developer.android.com/reference/android/app/ListFragment.html
public class PlayersFragment extends Fragment implements AdapterView.OnItemClickListener {

    public interface Listener {

        void onPlayerClicked(Tuple2<MemberId, MonopolyGame.PlayerWithBalanceAndConnectionState>
                                     player);

    }

    private static final Comparator<Tuple2<MemberId, PlayerWithBalanceAndConnectionState>>
            playerComparator =
            new Comparator<Tuple2<MemberId, PlayerWithBalanceAndConnectionState>>() {

                private final Collator collator = Collator.getInstance();

                @Override
                public int compare(Tuple2<MemberId, PlayerWithBalanceAndConnectionState> lhs,
                                   Tuple2<MemberId, PlayerWithBalanceAndConnectionState> rhs) {
                    return collator.compare(
                            lhs._2().member().name(),
                            rhs._2().member().name()
                    );
                }

            };

    private static class PlayersAdapter
            extends ArrayAdapter<Tuple2<MemberId, PlayerWithBalanceAndConnectionState>> {

        public PlayersAdapter(Context context) {
            super(context,
                    android.R.layout.simple_list_item_2,
                    android.R.id.text1);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            TextView text1 = (TextView) view.findViewById(android.R.id.text1);
            TextView text2 = (TextView) view.findViewById(android.R.id.text2);

            Tuple2<MemberId, PlayerWithBalanceAndConnectionState> player = getItem(position);

            String name = player._2().member().name();
            String balance = MonopolyGameActivity.formatBalance(
                    player._2().balanceWithCurrency()
            );

            text1.setText(
                    getContext().getString(
                            R.string.player_format_string,
                            name,
                            player._2().isConnected() ?
                                    getContext().getString(R.string.player_connected) :
                                    getContext().getString(R.string.player_disconnected)
                    )
            );
            text2.setText(balance);

            return view;
        }

    }

    private ArrayAdapter<Tuple2<MemberId, PlayerWithBalanceAndConnectionState>> listAdapter;

    private Listener listener;

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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        listAdapter = new PlayersAdapter(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_player_list, container, false);

        AbsListView absListViewPlayers = (AbsListView) view.findViewById(android.R.id.list);
        absListViewPlayers.setAdapter(listAdapter);
        absListViewPlayers.setEmptyView(view.findViewById(android.R.id.empty));
        absListViewPlayers.setOnItemClickListener(this);

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
            listener.onPlayerClicked(listAdapter.getItem(position));
        }
    }

    public void onPlayersChanged(scala.collection.immutable
                                         .Map<MemberId, PlayerWithBalanceAndConnectionState>
                                         players) {
        listAdapter.setNotifyOnChange(false);
        listAdapter.clear();
        Iterator<Tuple2<MemberId, PlayerWithBalanceAndConnectionState>> iterator = players
                .iterator();
        while (iterator.hasNext()) {
            Tuple2<MemberId, PlayerWithBalanceAndConnectionState> changedPlayer = iterator.next();
            listAdapter.add(changedPlayer);
        }
        listAdapter.sort(playerComparator);
        listAdapter.notifyDataSetChanged();
    }

}
