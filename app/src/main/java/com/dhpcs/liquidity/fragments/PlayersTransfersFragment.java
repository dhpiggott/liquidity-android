package com.dhpcs.liquidity.fragments;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dhpcs.liquidity.MonopolyGame.Player;
import com.dhpcs.liquidity.MonopolyGame.TransferWithCurrency;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.models.TransactionId;

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

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            TransfersFragment transfersFragment = (TransfersFragment) object;
            transfersFragments.remove(transfersFragment);
            super.destroyItem(container, position, object);
        }

        public Player get(int position) {
            if (position == 0) {
                return null;
            } else {
                return players.get(position - 1);
            }
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
            return player == null ? context.getString(R.string.all) : player.member().name();
        }

        public int getPosition(Player player) {
            return players.indexOf(player) + 1;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            TransfersFragment transfersFragment = (TransfersFragment)
                    super.instantiateItem(container, position);
            transfersFragments.add(transfersFragment);
            return transfersFragment;
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

    }

    private static final String STATE_SELECTED_PLAYER = "selected_player";

    private final ViewPager.OnPageChangeListener pageChangeListener =
            new ViewPager.SimpleOnPageChangeListener() {

                @Override
                public void onPageSelected(int position) {
                    selectedPlayer = playersTransfersFragmentStatePagerAdapter.get(position);
                }


            };

    private PlayersTransfersFragmentStatePagerAdapter playersTransfersFragmentStatePagerAdapter;

    private TabLayout tabLayoutPlayers;
    private ViewPager viewPagerPlayersTransfers;

    private Player selectedPlayer;

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

        tabLayoutPlayers = (TabLayout) view.findViewById(R.id.tablayout_players);
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
        playersTransfersFragmentStatePagerAdapter.sort(MonopolyGameActivity.playerComparator);
        playersTransfersFragmentStatePagerAdapter.notifyDataSetChanged();

        /*
         * The call to setTabsFromPagerAdapter first removes all existing tabs. The first tab
         * re-added is consequently also automatically selected for us. That's not so helpful here
         * as it would cause selectedPlayer to be set to null (the value corresponding to the
         * always-present, always-first 'ALL' tab). We could just store the fields value in a local
         * here, but this somehow seems to read better.
         */
        viewPagerPlayersTransfers.removeOnPageChangeListener(pageChangeListener);
        tabLayoutPlayers.setTabsFromPagerAdapter(playersTransfersFragmentStatePagerAdapter);
        viewPagerPlayersTransfers.addOnPageChangeListener(pageChangeListener);
        if (selectedPlayer != null && players.contains(selectedPlayer.memberId())) {
            viewPagerPlayersTransfers.setCurrentItem(
                    playersTransfersFragmentStatePagerAdapter.getPosition(
                            players.apply(selectedPlayer.memberId())
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

    public void onTransferAdded(TransferWithCurrency addedTransfer) {
        playersTransfersFragmentStatePagerAdapter.onTransferAdded(addedTransfer);
    }

    public void onTransfersChanged(
            scala.collection.Iterable<TransferWithCurrency> changedTransfers) {
        playersTransfersFragmentStatePagerAdapter.onTransfersChanged(changedTransfers);
    }

    public void onTransfersInitialized(scala.collection.Iterable<TransferWithCurrency> transfers) {
        playersTransfersFragmentStatePagerAdapter.onTransfersInitialized(transfers);
    }

    public void onTransfersUpdated(
            scala.collection.immutable.Map<TransactionId, TransferWithCurrency> transfers) {
        playersTransfersFragmentStatePagerAdapter.onTransfersUpdated(transfers);
    }

}
