package com.dhpcs.liquidity.fragment;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.util.SortedList;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.util.SortedListAdapterCallback;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activity.BoardGameActivity;
import com.dhpcs.liquidity.boardgame.BoardGame.Identity;
import com.dhpcs.liquidity.boardgame.BoardGame.Player;
import com.dhpcs.liquidity.boardgame.BoardGame.PlayerWithBalanceAndConnectionState;
import com.dhpcs.liquidity.model.MemberId;
import com.dhpcs.liquidity.model.ZoneId;
import com.dhpcs.liquidity.view.Identicon;

import java.util.Comparator;

import scala.Option;
import scala.collection.Iterator;

public class PlayersFragment extends Fragment {

    public interface Listener {

        void onNoPlayersTextClicked();

        void onPlayerClicked(Player player);

    }

    private class PlayersAdapter extends RecyclerView.Adapter<PlayersAdapter.PlayerViewHolder> {

        class PlayerViewHolder extends RecyclerView.ViewHolder
                implements View.OnClickListener {

            private final Identicon identiconId;
            private final TextView textViewName;
            private final TextView textViewBalance;
            private final TextView textViewStatus;

            private Player player;

            PlayerViewHolder(View itemView) {
                super(itemView);
                itemView.setOnClickListener(this);
                identiconId = itemView.findViewById(R.id.identicon_id);
                textViewName = itemView.findViewById(R.id.textview_name);
                textViewBalance = itemView.findViewById(R.id.textview_balance);
                textViewStatus = itemView.findViewById(R.id.textview_status);
            }

            void bindPlayer(PlayerWithBalanceAndConnectionState player) {
                this.player = player;

                ZoneId zoneId = player.zoneId();
                MemberId memberId = player.member().id();
                String name = BoardGameActivity.formatNullable(context, player.member().name());
                String balance = BoardGameActivity.formatCurrencyValue(
                        context,
                        player.balanceWithCurrency()._2(),
                        player.balanceWithCurrency()._1()
                );
                int status = player.isConnected()
                        ?
                        R.string.player_connected
                        :
                        R.string.player_disconnected;


                identiconId.show(zoneId, memberId);
                textViewName.setText(name);
                textViewBalance.setText(balance);
                textViewStatus.setText(status);
            }

            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onPlayerClicked(player);
                }
            }

        }

        private final Context context;
        private final SortedList<PlayerWithBalanceAndConnectionState> players;

        PlayersAdapter(final Context context) {
            this.context = context;
            this.players = new SortedList<>(
                    PlayerWithBalanceAndConnectionState.class,
                    new SortedListAdapterCallback<PlayerWithBalanceAndConnectionState>(this) {

                        private final Comparator<Player> playerComparator =
                                BoardGameActivity.playerComparator(context);

                        @Override
                        public int compare(PlayerWithBalanceAndConnectionState o1,
                                           PlayerWithBalanceAndConnectionState o2) {
                            return playerComparator.compare(o1, o2);
                        }

                        @Override
                        public boolean areContentsTheSame(PlayerWithBalanceAndConnectionState oldItem,
                                                          PlayerWithBalanceAndConnectionState newItem) {
                            return oldItem.equals(newItem);
                        }

                        @Override
                        public boolean areItemsTheSame(PlayerWithBalanceAndConnectionState item1,
                                                       PlayerWithBalanceAndConnectionState item2) {
                            return item1.member().id().equals(item2.member().id());
                        }

                    }
            );
        }

        void remove(PlayerWithBalanceAndConnectionState player) {
            players.remove(player);
        }

        void replace(PlayerWithBalanceAndConnectionState oldPlayer,
                     PlayerWithBalanceAndConnectionState newPlayer) {
            players.updateItemAt(players.indexOf(oldPlayer), newPlayer);
        }

        /**
         * @param player Must have same position according to the lists order as any item it
         *               replaces. If properties of the player (i.e. its name) have changed
         *               relative to any previous item, the replace method must instead be called.
         */
        void replaceOrAdd(PlayerWithBalanceAndConnectionState player) {
            players.add(player);
        }

        @Override
        public int getItemCount() {
            return players.size();
        }

        @Override
        public PlayerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.linearlayout_player, parent, false);
            return new PlayerViewHolder(view);
        }

        @Override
        public void onBindViewHolder(PlayerViewHolder holder, int position) {
            PlayerWithBalanceAndConnectionState player = players.get(position);
            holder.bindPlayer(player);
        }

    }

    private PlayersAdapter playersAdapter;

    private TextView textViewEmpty;
    private RecyclerView recyclerViewPlayers;

    private scala.collection.immutable.Map<MemberId, PlayerWithBalanceAndConnectionState> players;
    private Identity selectedIdentity;

    private Listener listener;

    public void onPlayerAdded(PlayerWithBalanceAndConnectionState addedPlayer) {
        replaceOrAddPlayer(addedPlayer);
        if (playersAdapter.getItemCount() != 0) {
            textViewEmpty.setVisibility(View.GONE);
            recyclerViewPlayers.setVisibility(View.VISIBLE);
        }
    }

    public void onPlayerChanged(PlayerWithBalanceAndConnectionState changedPlayer) {
        if (selectedIdentity == null
                || !changedPlayer.member().id().equals(selectedIdentity.member().id())) {
            playersAdapter.replace(players.apply(changedPlayer.member().id()), changedPlayer);
        }
    }

    public void onPlayersInitialized(
            scala.collection.Iterable<PlayerWithBalanceAndConnectionState> players) {
        Iterator<PlayerWithBalanceAndConnectionState> iterator = players.iterator();
        while (iterator.hasNext()) {
            replaceOrAddPlayer(iterator.next());
        }
        if (playersAdapter.getItemCount() != 0) {
            textViewEmpty.setVisibility(View.GONE);
            recyclerViewPlayers.setVisibility(View.VISIBLE);
        }
    }

    public void onPlayerRemoved(PlayerWithBalanceAndConnectionState removedPlayer) {
        if (selectedIdentity == null
                || !removedPlayer.member().id().equals(selectedIdentity.member().id())) {
            playersAdapter.remove(removedPlayer);
        }
        if (playersAdapter.getItemCount() == 0) {
            textViewEmpty.setVisibility(View.VISIBLE);
            recyclerViewPlayers.setVisibility(View.GONE);
        }
    }

    public void onPlayersUpdated(
            scala.collection.immutable.Map<MemberId, PlayerWithBalanceAndConnectionState> players) {
        this.players = players;
    }

    public void onSelectedIdentityChanged(Identity selectedIdentity) {
        if (this.selectedIdentity != null && players != null) {
            Option<PlayerWithBalanceAndConnectionState> player =
                    players.get(this.selectedIdentity.member().id());
            if (player.isDefined()) {
                playersAdapter.replaceOrAdd(player.get());
            }
        }
        this.selectedIdentity = selectedIdentity;
        if (this.selectedIdentity != null && players != null) {
            Option<PlayerWithBalanceAndConnectionState> player =
                    players.get(this.selectedIdentity.member().id());
            if (player.isDefined()) {
                playersAdapter.remove(player.get());
            }
        }
        textViewEmpty.setVisibility(
                playersAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE
        );
        recyclerViewPlayers.setVisibility(
                playersAdapter.getItemCount() == 0 ? View.GONE : View.VISIBLE
        );
    }

    private void replaceOrAddPlayer(PlayerWithBalanceAndConnectionState player) {
        if (selectedIdentity == null
                || !player.member().id().equals(selectedIdentity.member().id())) {
            playersAdapter.replaceOrAdd(player);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        listener = (Listener) context;
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
        View view = inflater.inflate(R.layout.fragment_players, container, false);

        textViewEmpty = view.findViewById(R.id.textview_empty);
        recyclerViewPlayers = view.findViewById(R.id.recyclerview_players);

        textViewEmpty.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNoPlayersTextClicked();
            }
        });
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

}
