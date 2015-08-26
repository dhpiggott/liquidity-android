package com.dhpcs.liquidity.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.util.SortedList;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.util.SortedListAdapterCallback;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dhpcs.liquidity.BoardGame.Identity;
import com.dhpcs.liquidity.BoardGame.Player;
import com.dhpcs.liquidity.BoardGame.PlayerWithBalanceAndConnectionState;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activity.BoardGameActivity;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.models.ZoneId;
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

        public class PlayerViewHolder extends RecyclerView.ViewHolder
                implements View.OnClickListener {

            private final Identicon identiconId;
            private final TextView textViewName;
            private final TextView textViewBalance;
            private final TextView textViewStatus;

            private Player player;

            public PlayerViewHolder(View itemView) {
                super(itemView);
                itemView.setOnClickListener(this);
                identiconId = (Identicon) itemView.findViewById(R.id.identicon_id);
                textViewName = (TextView) itemView.findViewById(R.id.textview_name);
                textViewBalance = (TextView) itemView.findViewById(R.id.textview_balance);
                textViewStatus = (TextView) itemView.findViewById(R.id.textview_status);
            }

            public void bindPlayer(PlayerWithBalanceAndConnectionState player) {
                this.player = player;

                ZoneId zoneId = player.zoneId();
                MemberId memberId = player.member().id();
                String name = BoardGameActivity.formatNullable(context, player.member().name());
                String balance = BoardGameActivity.formatCurrencyValue(
                        context,
                        player.balanceWithCurrency()._2(),
                        player.balanceWithCurrency()._1()
                );
                String status = player.isConnected() ?
                        null : context.getString(R.string.player_disconnected);

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

        public PlayersAdapter(final Context context) {
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

        /**
         * @param player Must have same position according to the lists order as any item it
         *               replaces. If properties of the player (i.e. its name) have changed
         *               relative to any previous item, the replace method must instead be called.
         * @return The index of the (newly) added player .
         */
        public int addOrReplace(PlayerWithBalanceAndConnectionState player) {
            return players.add(player);
        }

        public boolean remove(PlayerWithBalanceAndConnectionState player) {
            return players.remove(player);
        }

        public void replace(PlayerWithBalanceAndConnectionState oldPlayer,
                            PlayerWithBalanceAndConnectionState newPlayer) {
            players.updateItemAt(players.indexOf(oldPlayer), newPlayer);
        }

    }

    private PlayersAdapter playersAdapter;

    private TextView textViewEmpty;
    private RecyclerView recyclerViewPlayers;

    private scala.collection.immutable.Map<MemberId, PlayerWithBalanceAndConnectionState> players;
    private Identity selectedIdentity;

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
        View view = inflater.inflate(R.layout.fragment_players, container, false);

        textViewEmpty = (TextView) view.findViewById(R.id.textview_empty);
        recyclerViewPlayers = (RecyclerView) view.findViewById(R.id.recyclerview_players);

        textViewEmpty.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onNoPlayersTextClicked();
                }
            }

        });
        recyclerViewPlayers.addItemDecoration(new RecyclerView.ItemDecoration() {

            private final Drawable divider;

            {
                TypedArray a = getActivity().obtainStyledAttributes(
                        new int[]{android.R.attr.listDivider}
                );
                divider = a.getDrawable(0);
                a.recycle();
            }

            @Override
            public void getItemOffsets(Rect outRect,
                                       View view,
                                       RecyclerView parent,
                                       RecyclerView.State state) {
                outRect.set(0, 0, 0, divider.getIntrinsicHeight());
            }

            @Override
            public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
                int left = parent.getPaddingLeft();
                int right = parent.getWidth() - parent.getPaddingRight();
                int childCount = parent.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = parent.getChildAt(i);
                    RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                            child.getLayoutParams();
                    int top = child.getBottom() + params.bottomMargin;
                    int bottom = top + divider.getIntrinsicHeight();
                    divider.setBounds(left, top, right, bottom);
                    divider.draw(c);
                }
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
                playersAdapter.addOrReplace(player.get());
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
            playersAdapter.addOrReplace(player);
        }
    }

}
