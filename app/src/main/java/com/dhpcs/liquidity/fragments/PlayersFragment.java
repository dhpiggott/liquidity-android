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

import com.dhpcs.liquidity.MonopolyGame.Player;
import com.dhpcs.liquidity.MonopolyGame.PlayerWithBalanceAndConnectionState;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;
import com.dhpcs.liquidity.models.Identifier;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.views.Identicon;

import scala.collection.Iterator;

// TODO: Extend ListFragment? Or switch to RecyclerView?
public class PlayersFragment extends Fragment implements AdapterView.OnItemClickListener,
        AdapterView.OnItemLongClickListener {

    public interface Listener {

        void onPlayerClicked(Player player);

        void onPlayerLongClicked(Player player);

    }

    private static class PlayersAdapter extends ArrayAdapter<PlayerWithBalanceAndConnectionState> {

        public PlayersAdapter(Context context) {
            super(context,
                    R.layout.relativelayout_player,
                    R.id.textview_name);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            Identicon identiconId = (Identicon) view.findViewById(R.id.identicon_id);
            TextView textViewName = (TextView) view.findViewById(R.id.textview_name);
            TextView textViewBalance = (TextView) view.findViewById(R.id.textview_balance);

            PlayerWithBalanceAndConnectionState player = getItem(position);

            Identifier identifier = player.memberId();
            String name = player.member().name();
            String balance = MonopolyGameActivity.formatCurrency(
                    player.balanceWithCurrency()._1(),
                    player.balanceWithCurrency()._2()
            );

            identiconId.show(identifier);
            textViewName.setText(
                    getContext().getString(
                            R.string.player_format_string,
                            name,
                            player.isConnected() ?
                                    getContext().getString(R.string.player_connected) :
                                    getContext().getString(R.string.player_disconnected)
                    )
            );
            textViewBalance.setText(balance);

            return view;
        }

    }

    private ArrayAdapter<PlayerWithBalanceAndConnectionState> listAdapter;

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
        absListViewPlayers.setOnItemLongClickListener(this);

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

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (listener != null) {
            listener.onPlayerLongClicked(listAdapter.getItem(position));
        }
        return true;
    }

    public void onPlayersChanged(MemberId selectedIdentityId,
                                 scala.collection.Iterable<PlayerWithBalanceAndConnectionState>
                                         players) {
        listAdapter.setNotifyOnChange(false);
        listAdapter.clear();
        Iterator<PlayerWithBalanceAndConnectionState> iterator = players.iterator();
        while (iterator.hasNext()) {
            PlayerWithBalanceAndConnectionState player = iterator.next();
            if (!player.memberId().equals(selectedIdentityId)) {
                listAdapter.add(player);
            }
        }
        listAdapter.sort(MonopolyGameActivity.playerComparator);
        listAdapter.notifyDataSetChanged();
    }

}
