package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.util.SortedList;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.util.SortedListAdapterCallback;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dhpcs.liquidity.MonopolyGame.Player;
import com.dhpcs.liquidity.MonopolyGame.PlayerWithBalanceAndConnectionState;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;
import com.dhpcs.liquidity.models.Identifier;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.views.Identicon;

import java.text.Collator;

import scala.collection.Iterator;

public class PlayersFragment extends Fragment {

    public interface Listener {

        void onPlayerClicked(Player player);

    }

    private class PlayersAdapter extends RecyclerView.Adapter<PlayersAdapter.PlayerViewHolder> {

        public class PlayerViewHolder extends RecyclerView.ViewHolder
                implements View.OnClickListener {

            private final Identicon identiconId;
            private final TextView textViewName;
            private final TextView textViewBalance;

            private Player player;

            public PlayerViewHolder(View itemView) {
                super(itemView);
                itemView.setOnClickListener(this);
                identiconId = (Identicon) itemView.findViewById(R.id.identicon_id);
                textViewName = (TextView) itemView.findViewById(R.id.textview_name);
                textViewBalance = (TextView) itemView.findViewById(R.id.textview_balance);
            }

            public void bindPlayer(PlayerWithBalanceAndConnectionState player) {
                this.player = player;

                Identifier identifier = player.memberId();
                String name = player.member().name();
                String balance = MonopolyGameActivity.formatCurrencyValue(
                        context,
                        player.balanceWithCurrency()._2(),
                        player.balanceWithCurrency()._1()
                );

                identiconId.show(identifier);
                textViewName.setText(
                        context.getString(
                                R.string.player_format_string,
                                name,
                                player.isConnected() ?
                                        context.getString(R.string.player_connected) :
                                        context.getString(R.string.player_disconnected)
                        )
                );
                textViewBalance.setText(balance);
            }

            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onPlayerClicked(player);
                }
            }

        }

        private final Context context;
        private final SortedList<PlayerWithBalanceAndConnectionState> players = new SortedList<>(
                PlayerWithBalanceAndConnectionState.class,
                new SortedListAdapterCallback<PlayerWithBalanceAndConnectionState>(this) {

                    private final Collator collator = Collator.getInstance();

                    @Override
                    public int compare(PlayerWithBalanceAndConnectionState o1,
                                       PlayerWithBalanceAndConnectionState o2) {
                        return collator.compare(o1.member().name(), o2.member().name());
                    }

                    @Override
                    public boolean areContentsTheSame(PlayerWithBalanceAndConnectionState oldItem,
                                                      PlayerWithBalanceAndConnectionState newItem) {
                        return oldItem.equals(newItem);
                    }

                    @Override
                    public boolean areItemsTheSame(PlayerWithBalanceAndConnectionState item1,
                                                   PlayerWithBalanceAndConnectionState item2) {
                        return item1.memberId().equals(item2.memberId());
                    }

                }
        );

        public PlayersAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return players.size();
        }

        @Override
        public PlayerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.relativelayout_player, parent, false);
            return new PlayerViewHolder(view);
        }

        @Override
        public void onBindViewHolder(PlayerViewHolder holder, int position) {
            PlayerWithBalanceAndConnectionState player = players.get(position);
            holder.bindPlayer(player);
        }

        public int add(PlayerWithBalanceAndConnectionState player) {
            return players.add(player);
        }

        public void beginBatchedUpdates() {
            players.beginBatchedUpdates();
        }

        public void clear() {
            players.clear();
        }

        public void endBatchedUpdates() {
            players.endBatchedUpdates();
        }

    }

    private PlayersAdapter playersAdapter;

    private TextView textViewEmpty;

    private Listener listener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        listener = (Listener) activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        playersAdapter = new PlayersAdapter(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_player_list, container, false);

        textViewEmpty = (TextView) view.findViewById(R.id.textview_empty);

        RecyclerView recyclerViewPlayers = (RecyclerView) view.findViewById(R.id.recyclerview);
        recyclerViewPlayers.setHasFixedSize(true);
        recyclerViewPlayers.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerViewPlayers.setAdapter(playersAdapter);

        return view;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public void onPlayersChanged(MemberId selectedIdentityId,
                                 scala.collection.Iterable<PlayerWithBalanceAndConnectionState>
                                         players) {
        playersAdapter.beginBatchedUpdates();
        playersAdapter.clear();
        Iterator<PlayerWithBalanceAndConnectionState> iterator = players.iterator();
        while (iterator.hasNext()) {
            PlayerWithBalanceAndConnectionState player = iterator.next();
            if (!player.memberId().equals(selectedIdentityId)) {
                playersAdapter.add(player);
            }
        }
        textViewEmpty.setVisibility(playersAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        playersAdapter.endBatchedUpdates();
    }

}
