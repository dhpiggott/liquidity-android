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
import com.dhpcs.liquidity.MonopolyGame.PlayerWithBalanceAndConnectionState;
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

        private final ArrayList<PlayerWithBalanceAndConnectionState> players = new ArrayList<>();
        private final Set<TransfersFragment> transfersFragments = new HashSet<>();
        private final Context context;

        private ArrayList<TransferWithCurrency> transfers;

        public PlayersTransfersFragmentStatePagerAdapter(FragmentManager fragmentManager,
                                                         Context context) {
            super(fragmentManager);
            this.context = context;
        }

        public void add(PlayerWithBalanceAndConnectionState player) {
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

        public PlayerWithBalanceAndConnectionState get(int position) {
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
            return TransfersFragment.newInstance(
                    get(position),
                    transfers
            );
        }

        @Override
        public int getItemPosition(Object item) {
            return POSITION_NONE;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            PlayerWithBalanceAndConnectionState player = get(position);
            return player == null ? context.getString(R.string.all_transfers)
                    : context.getString(
                    R.string.player_transfers_format_string,
                    player.member().name()
            );
        }

        public int getPosition(PlayerWithBalanceAndConnectionState player) {
            return players.indexOf(player) + 1;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            TransfersFragment transfersFragment = (TransfersFragment)
                    super.instantiateItem(container, position);
            transfersFragments.add(transfersFragment);
            return transfersFragment;
        }

        public void onTransfersAdded(
                scala.collection.Iterable<TransferWithCurrency> addedTransfers) {
            for (TransfersFragment transfersFragment : transfersFragments) {
                transfersFragment.onTransfersAdded(addedTransfers);
            }
        }

        public void onTransfersChanged(
                scala.collection.Iterable<TransferWithCurrency> changedTransfers) {
            for (TransfersFragment transfersFragment : transfersFragments) {
                transfersFragment.onTransfersChanged(changedTransfers);
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

    private PlayersTransfersFragmentStatePagerAdapter playersTransfersFragmentStatePagerAdapter;
    private ViewPager viewPagerPlayersTransfers;
    private TabLayout tabLayoutPlayers;

    public PlayerWithBalanceAndConnectionState getSelectedPlayer() {
        if (playersTransfersFragmentStatePagerAdapter.getCount() == 0) {
            return null;
        } else {
            return playersTransfersFragmentStatePagerAdapter.get(
                    viewPagerPlayersTransfers.getCurrentItem()
            );
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        playersTransfersFragmentStatePagerAdapter = new PlayersTransfersFragmentStatePagerAdapter(
                getFragmentManager(),
                getActivity()
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_players_transfers, container, false);

        viewPagerPlayersTransfers = (ViewPager) view.findViewById(R.id.viewpager_players_transfers);
        tabLayoutPlayers = (TabLayout) view.findViewById(R.id.tablayout_players);

        viewPagerPlayersTransfers.setAdapter(playersTransfersFragmentStatePagerAdapter);
        tabLayoutPlayers.setupWithViewPager(viewPagerPlayersTransfers);

        return view;
    }

    public void onPlayersUpdated(
            scala.collection.immutable.Map<MemberId, PlayerWithBalanceAndConnectionState> players) {
        PlayerWithBalanceAndConnectionState selectedPlayer = getSelectedPlayer();
        playersTransfersFragmentStatePagerAdapter.clear();
        Iterator<PlayerWithBalanceAndConnectionState> iterator = players.valuesIterator();
        while (iterator.hasNext()) {
            playersTransfersFragmentStatePagerAdapter.add(iterator.next());
        }
        playersTransfersFragmentStatePagerAdapter.sort(MonopolyGameActivity.playerComparator);
        playersTransfersFragmentStatePagerAdapter.notifyDataSetChanged();
        tabLayoutPlayers.setupWithViewPager(viewPagerPlayersTransfers);
        if (selectedPlayer != null && players.contains(selectedPlayer.memberId())) {
            viewPagerPlayersTransfers.setCurrentItem(
                    playersTransfersFragmentStatePagerAdapter.getPosition(
                            players.apply(selectedPlayer.memberId())
                    ),
                    false
            );
        }
    }

    public void onTransfersAdded(scala.collection.Iterable<TransferWithCurrency> addedTransfers) {
        playersTransfersFragmentStatePagerAdapter.onTransfersAdded(addedTransfers);
    }

    public void onTransfersChanged(
            scala.collection.Iterable<TransferWithCurrency> changedTransfers) {
        playersTransfersFragmentStatePagerAdapter.onTransfersChanged(changedTransfers);
    }

    public void onTransfersUpdated(
            scala.collection.immutable.Map<TransactionId, TransferWithCurrency> transfers) {
        playersTransfersFragmentStatePagerAdapter.onTransfersUpdated(transfers);
    }

}
