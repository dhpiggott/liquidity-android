package com.dhpcs.liquidity.fragment

import android.content.Context
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.BoardGameActivity
import com.dhpcs.liquidity.boardgame.BoardGame.Player
import com.dhpcs.liquidity.boardgame.BoardGame.TransferWithCurrency
import com.dhpcs.liquidity.model.MemberId
import com.dhpcs.liquidity.model.TransactionId
import scala.collection.JavaConversions
import java.util.*

class PlayersTransfersFragment : Fragment() {

    companion object {

        private const val STATE_SELECTED_PLAYER = "selected_player"

        private class PlayersTransfersFragmentStatePagerAdapter
        internal constructor(fragmentManager: FragmentManager,
                             private val context: Context
        ) : FragmentStatePagerAdapter(fragmentManager) {

            private val players = ArrayList<Player>()
            private val transfersFragments = HashSet<TransfersFragment>()

            private var transfers: ArrayList<TransferWithCurrency>? = null

            fun add(player: Player) = players.add(player)

            override fun getCount(): Int = players.size + 1

            override fun getPageTitle(position: Int): CharSequence? {
                val player = get(position)
                return if (player == null) {
                    context.getString(R.string.all)
                } else {
                    BoardGameActivity.formatNullable(context, player.member().name())
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

            internal operator fun get(position: Int): Player? {
                return if (position == 0) null else players[position - 1]
            }

            internal fun getPosition(player: Player): Int = players.indexOf(player) + 1

            internal fun onTransfersInitialized(
                    transfers: scala.collection.Iterable<TransferWithCurrency>) {
                for (transfersFragment in transfersFragments) {
                    transfersFragment.onTransfersInitialized(transfers)
                }
            }

            internal fun onTransferAdded(addedTransfer: TransferWithCurrency) {
                for (transfersFragment in transfersFragments) {
                    transfersFragment.onTransferAdded(addedTransfer)
                }
            }

            internal fun onTransfersChanged(
                    changedTransfers: scala.collection.Iterable<TransferWithCurrency>) {
                for (transfersFragment in transfersFragments) {
                    transfersFragment.onTransfersChanged(changedTransfers)
                }
            }

            internal fun onTransfersUpdated(
                    transfers: scala.collection.immutable.Map<TransactionId, TransferWithCurrency>
            ) {
                this.transfers = ArrayList(
                        JavaConversions.bufferAsJavaList(
                                transfers.values().toBuffer()
                        )
                )
            }

            internal fun sort(comparator: Comparator<Player>) {
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

    private var selectedPlayer: Player? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playersTransfersFragmentStatePagerAdapter = PlayersTransfersFragmentStatePagerAdapter(
                fragmentManager!!,
                activity!!
        )

        selectedPlayer = savedInstanceState?.getSerializable(STATE_SELECTED_PLAYER) as Player?
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

    fun onPlayersUpdated(players: scala.collection.immutable.Map<MemberId, out Player>) {
        playersTransfersFragmentStatePagerAdapter!!.clear()
        val iterator = players.valuesIterator()
        while (iterator.hasNext()) {
            playersTransfersFragmentStatePagerAdapter!!.add(iterator.next())
        }
        playersTransfersFragmentStatePagerAdapter!!.sort(
                BoardGameActivity.playerComparator(activity!!)
        )
        playersTransfersFragmentStatePagerAdapter!!.notifyDataSetChanged()

        if (selectedPlayer != null && players.contains(selectedPlayer!!.member().id())) {
            viewPagerPlayersTransfers!!.setCurrentItem(
                    playersTransfersFragmentStatePagerAdapter!!.getPosition(
                            players.apply(selectedPlayer!!.member().id())
                    ),
                    false
            )
        }
    }

    fun onTransfersInitialized(transfers: scala.collection.Iterable<TransferWithCurrency>) {
        lastTransferFragment!!.onTransfersInitialized(transfers)
        playersTransfersFragmentStatePagerAdapter!!.onTransfersInitialized(transfers)
    }

    fun onTransferAdded(addedTransfer: TransferWithCurrency) {
        lastTransferFragment!!.onTransferAdded(addedTransfer)
        playersTransfersFragmentStatePagerAdapter!!.onTransferAdded(addedTransfer)
    }

    fun onTransfersChanged(
            changedTransfers: scala.collection.Iterable<TransferWithCurrency>) {
        lastTransferFragment!!.onTransfersChanged(changedTransfers)
        playersTransfersFragmentStatePagerAdapter!!.onTransfersChanged(changedTransfers)
    }

    fun onTransfersUpdated(
            transfers: scala.collection.immutable.Map<TransactionId, TransferWithCurrency>) {
        playersTransfersFragmentStatePagerAdapter!!.onTransfersUpdated(transfers)
    }

}
