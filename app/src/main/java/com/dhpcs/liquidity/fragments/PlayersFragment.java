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

import com.dhpcs.liquidity.MonopolyGame.PlayerWithBalanceAndConnectionState;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;
import com.dhpcs.liquidity.models.Identifier;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.views.Identicon;

import java.text.Collator;
import java.util.Comparator;

import scala.collection.Iterator;

// TODO: Extend ListFragment? http://developer.android.com/reference/android/app/ListFragment.html
public class PlayersFragment extends Fragment implements AdapterView.OnItemClickListener {

    public interface Listener {

        void onPlayerClicked(PlayerWithBalanceAndConnectionState player);

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
            String balance = MonopolyGameActivity.formatBalance(player.balanceWithCurrency());

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

    private static final Comparator<PlayerWithBalanceAndConnectionState> playerComparator =
            new Comparator<PlayerWithBalanceAndConnectionState>() {

                private final Collator collator = Collator.getInstance();

                @Override
                public int compare(PlayerWithBalanceAndConnectionState lhs,
                                   PlayerWithBalanceAndConnectionState rhs) {
                    return collator.compare(
                            lhs.member().name(),
                            rhs.member().name()
                    );
                }

            };

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
        listAdapter.sort(playerComparator);
        listAdapter.notifyDataSetChanged();
    }

}
