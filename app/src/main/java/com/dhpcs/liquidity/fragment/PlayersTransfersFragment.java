package com.dhpcs.liquidity.fragment;

import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activity.BoardGameActivity;
import com.dhpcs.liquidity.boardgame.BoardGame.Player;
import com.dhpcs.liquidity.boardgame.BoardGame.TransferWithCurrency;
import com.dhpcs.liquidity.model.MemberId;
import com.dhpcs.liquidity.model.TransactionId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import scala.collection.Iterator;
import scala.collection.JavaConversions;

public class PlayersTransfersFragment extends Fragment {

    private static class PlayersTransfersFragmentStatePagerAdapter
            extends FragmentStatePagerAdapter {

        private final ArrayList<Player> players = new ArrayList<>();
        private final Set<TransfersFragment> transfersFragments = new HashSet<>();
        private final Context context;

        private ArrayList<TransferWithCurrency> transfers;

        public PlayersTransfersFragmentStatePagerAdapter(FragmentManager fragmentManager,
                                                         Context context) {
            super(fragmentManager);
            this.context = context;
        }

        public void add(Player player) {
            players.add(player);
        }

        public void clear() {
            players.clear();
        }

        public Player get(int position) {
            if (position == 0) {
                return null;
            } else {
                return players.get(position - 1);
            }
        }

        public int getPosition(Player player) {
            return players.indexOf(player) + 1;
        }

        public void onTransferAdded(TransferWithCurrency addedTransfer) {
            for (TransfersFragment transfersFragment : transfersFragments) {
                transfersFragment.onTransferAdded(addedTransfer);
            }
        }

        public void onTransfersChanged(
                scala.collection.Iterable<TransferWithCurrency> changedTransfers) {
            for (TransfersFragment transfersFragment : transfersFragments) {
                transfersFragment.onTransfersChanged(changedTransfers);
            }
        }

        public void onTransfersInitialized(
                scala.collection.Iterable<TransferWithCurrency> transfers) {
            for (TransfersFragment transfersFragment : transfersFragments) {
                transfersFragment.onTransfersInitialized(transfers);
            }
        }

        public void onTransfersUpdated(
                scala.collection.immutable.Map<TransactionId, TransferWithCurrency> transfers) {
            this.transfers = new ArrayList<>(
                    JavaConversions.bufferAsJavaList(
                            transfers.values().<TransferWithCurrency>toBuffer()
                    )
            );
        }

        public void sort(Comparator<Player> comparator) {
            Collections.sort(players, comparator);
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            TransfersFragment transfersFragment = (TransfersFragment) object;
            transfersFragments.remove(transfersFragment);
            super.destroyItem(container, position, object);
        }

        @Override
        public int getCount() {
            return players.size() + 1;
        }

        @Override
        public Fragment getItem(int position) {
            return TransfersFragment.newInstance(get(position), transfers);
        }

        @Override
        public int getItemPosition(Object item) {
            return POSITION_NONE;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Player player = get(position);
            return player == null
                    ?
                    context.getString(R.string.all)
                    :
                    BoardGameActivity.formatNullable(context, player.member().name());
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            TransfersFragment transfersFragment = (TransfersFragment)
                    super.instantiateItem(container, position);
            transfersFragments.add(transfersFragment);
            return transfersFragment;
        }

    }

    private static final String STATE_SELECTED_PLAYER = "selected_player";

    private final ViewPager.OnPageChangeListener pageChangeListener =
            new ViewPager.SimpleOnPageChangeListener() {

                @Override
                public void onPageSelected(int position) {
                    selectedPlayer = playersTransfersFragmentStatePagerAdapter.get(position);
                }


            };

    private LastTransferFragment lastTransferFragment;

    private PlayersTransfersFragmentStatePagerAdapter playersTransfersFragmentStatePagerAdapter;

    private ViewPager viewPagerPlayersTransfers;

    private Player selectedPlayer;

    public void onTransferAdded(TransferWithCurrency addedTransfer) {
        lastTransferFragment.onTransferAdded(addedTransfer);
        playersTransfersFragmentStatePagerAdapter.onTransferAdded(addedTransfer);
    }

    public void onTransfersChanged(
            scala.collection.Iterable<TransferWithCurrency> changedTransfers) {
        lastTransferFragment.onTransfersChanged(changedTransfers);
        playersTransfersFragmentStatePagerAdapter.onTransfersChanged(changedTransfers);
    }

    public void onTransfersInitialized(scala.collection.Iterable<TransferWithCurrency> transfers) {
        lastTransferFragment.onTransfersInitialized(transfers);
        playersTransfersFragmentStatePagerAdapter.onTransfersInitialized(transfers);
    }

    public void onTransfersUpdated(
            scala.collection.immutable.Map<TransactionId, TransferWithCurrency> transfers) {
        playersTransfersFragmentStatePagerAdapter.onTransfersUpdated(transfers);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        playersTransfersFragmentStatePagerAdapter = new PlayersTransfersFragmentStatePagerAdapter(
                getFragmentManager(),
                getActivity()
        );

        if (savedInstanceState != null) {
            selectedPlayer = (Player) savedInstanceState.getSerializable(STATE_SELECTED_PLAYER);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_players_transfers, container, false);

        lastTransferFragment = (LastTransferFragment) getChildFragmentManager()
                .findFragmentById(R.id.fragment_last_transfer);

        TabLayout tabLayoutPlayers = (TabLayout) view.findViewById(R.id.tablayout_players);
        viewPagerPlayersTransfers = (ViewPager) view.findViewById(R.id.viewpager_players_transfers);

        viewPagerPlayersTransfers.setAdapter(playersTransfersFragmentStatePagerAdapter);
        tabLayoutPlayers.setupWithViewPager(viewPagerPlayersTransfers);
        viewPagerPlayersTransfers.addOnPageChangeListener(pageChangeListener);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewPagerPlayersTransfers.removeOnPageChangeListener(pageChangeListener);
    }

    public void onPlayersUpdated(
            scala.collection.immutable.Map<MemberId, ? extends Player> players) {
        playersTransfersFragmentStatePagerAdapter.clear();
        Iterator<? extends Player> iterator = players.valuesIterator();
        while (iterator.hasNext()) {
            playersTransfersFragmentStatePagerAdapter.add(iterator.next());
        }
        playersTransfersFragmentStatePagerAdapter.sort(
                BoardGameActivity.playerComparator(getActivity())
        );
        playersTransfersFragmentStatePagerAdapter.notifyDataSetChanged();

        if (selectedPlayer != null && players.contains(selectedPlayer.member().id())) {
            viewPagerPlayersTransfers.setCurrentItem(
                    playersTransfersFragmentStatePagerAdapter.getPosition(
                            players.apply(selectedPlayer.member().id())
                    ),
                    false
            );
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_SELECTED_PLAYER, selectedPlayer);
    }

}
