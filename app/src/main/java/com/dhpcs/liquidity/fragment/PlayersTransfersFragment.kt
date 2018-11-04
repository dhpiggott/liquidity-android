package com.dhpcs.liquidity.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.BoardGameActivity
import com.google.android.material.tabs.TabLayout
import java.util.*
import kotlin.collections.ArrayList

class PlayersTransfersFragment : Fragment() {

    companion object {

        private const val STATE_SELECTED_PLAYER = "selected_player"

        private class PlayersTransfersFragmentStatePagerAdapter
        internal constructor(fragmentManager: FragmentManager,
                             private val context: Context
        ) : FragmentStatePagerAdapter(fragmentManager) {

            private val players = ArrayList<BoardGame.Companion.Player>()
            private val transfersFragments = HashSet<TransfersFragment>()

            private var transfers: ArrayList<BoardGame.Companion.TransferWithCurrency> = ArrayList()

            fun add(player: BoardGame.Companion.Player) = players.add(player)

            override fun getCount(): Int = players.size + 1

            override fun getPageTitle(position: Int): CharSequence? {
                val player = get(position)
                return if (player == null) {
                    context.getString(R.string.all)
                } else {
                    BoardGameActivity.formatNullable(context, player.name)
                }
            }

            override fun getItem(position: Int): Fragment {
                return TransfersFragment.newInstance(get(position), transfers)
            }

            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val transfersFragment =
                        super.instantiateItem(container, position) as TransfersFragment
                transfersFragments.add(transfersFragment)
                return transfersFragment
            }

            override fun getItemPosition(item: Any): Int = PagerAdapter.POSITION_NONE

            override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
                val transfersFragment = `object` as TransfersFragment
                transfersFragments.remove(transfersFragment)
                super.destroyItem(container, position, `object`)
            }

            internal fun clear() = players.clear()

            internal operator fun get(position: Int): BoardGame.Companion.Player? {
                return if (position == 0) null else players[position - 1]
            }

            internal fun getPosition(player: BoardGame.Companion.Player): Int {
                return players.indexOf(player) + 1
            }

            internal fun onTransfersUpdated(
                    transfers: Map<String, BoardGame.Companion.TransferWithCurrency>
            ) {
                this.transfers = ArrayList(transfers.values)
                for (transfersFragment in transfersFragments) {
                    transfersFragment.onTransfersUpdated(transfers)
                }
            }

            internal fun sort(comparator: Comparator<BoardGame.Companion.Player>) {
                Collections.sort(players, comparator)
            }

        }

    }

    private val pageChangeListener = object : ViewPager.SimpleOnPageChangeListener() {

        override fun onPageSelected(position: Int) {
            selectedPlayer = playersTransfersFragmentStatePagerAdapter!![position]
        }

    }

    private var playersTransfersFragmentStatePagerAdapter:
            PlayersTransfersFragmentStatePagerAdapter? = null

    private var lastTransferFragment: LastTransferFragment? = null
    private var viewPagerPlayersTransfers: ViewPager? = null

    private var selectedPlayer: BoardGame.Companion.Player? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playersTransfersFragmentStatePagerAdapter = PlayersTransfersFragmentStatePagerAdapter(
                fragmentManager!!,
                requireActivity()
        )

        selectedPlayer = savedInstanceState?.getSerializable(STATE_SELECTED_PLAYER) as
                BoardGame.Companion.Player?
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater
                .inflate(R.layout.fragment_players_transfers, container, false)

        lastTransferFragment = childFragmentManager
                .findFragmentById(R.id.fragment_last_transfer) as LastTransferFragment

        val tabLayoutPlayers = view.findViewById<TabLayout>(R.id.tablayout_players)
        viewPagerPlayersTransfers = view.findViewById(R.id.viewpager_players_transfers)

        viewPagerPlayersTransfers!!.adapter = playersTransfersFragmentStatePagerAdapter
        tabLayoutPlayers.setupWithViewPager(viewPagerPlayersTransfers)
        viewPagerPlayersTransfers!!.addOnPageChangeListener(pageChangeListener)

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewPagerPlayersTransfers!!.removeOnPageChangeListener(pageChangeListener)
    }

    fun onPlayersUpdated(players: Map<String, BoardGame.Companion.Player>) {
        playersTransfersFragmentStatePagerAdapter!!.clear()
        players.values.forEach {
            playersTransfersFragmentStatePagerAdapter!!.add(it)
        }
        playersTransfersFragmentStatePagerAdapter!!.sort(
                BoardGameActivity.playerComparator(requireActivity())
        )
        playersTransfersFragmentStatePagerAdapter!!.notifyDataSetChanged()

        if (selectedPlayer != null && players.contains(selectedPlayer!!.memberId)) {
            viewPagerPlayersTransfers!!.setCurrentItem(
                    playersTransfersFragmentStatePagerAdapter!!.getPosition(
                            players[selectedPlayer!!.memberId]!!
                    ),
                    false
            )
        }
    }

    fun onTransferAdded(transfer: BoardGame.Companion.TransferWithCurrency) {
        lastTransferFragment!!.onTransferAdded(transfer)
    }

    fun onTransfersUpdated(
            transfers: Map<String, BoardGame.Companion.TransferWithCurrency>) {
        lastTransferFragment!!.onTransfersUpdated(transfers)
        playersTransfersFragmentStatePagerAdapter!!.onTransfersUpdated(transfers)
    }

}
