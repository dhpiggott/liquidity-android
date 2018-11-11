package com.dhpcs.liquidity.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import com.dhpcs.liquidity.activity.MainActivity.Companion.liveData
import kotlinx.android.synthetic.main.fragment_players_transfers.*

class PlayersTransfersFragment : Fragment() {

    companion object {

        private class PlayersTransfersFragmentStatePagerAdapter
        internal constructor(fragmentManager: FragmentManager,
                             private val context: Context
        ) : FragmentStatePagerAdapter(fragmentManager) {

            internal val players = arrayListOf<BoardGame.Companion.Player>()

            override fun getCount(): Int = players.size + 1

            override fun getPageTitle(position: Int): CharSequence? {
                return if (position == 0) {
                    context.getString(R.string.all)
                } else {
                    LiquidityApplication.formatNullable(context, players[position - 1].name)
                }
            }

            override fun getItemPosition(item: Any): Int = POSITION_NONE

            override fun getItem(position: Int): Fragment {
                return if (position == 0) {
                    TransfersFragment.newInstance(null)
                } else {
                    TransfersFragment.newInstance(players[position - 1])
                }
            }

        }

    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_players_transfers, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)

        val playersTransfersFragmentStatePagerAdapter = PlayersTransfersFragmentStatePagerAdapter(
                fragmentManager!!,
                requireContext()
        )

        viewpager_players_transfers.adapter = playersTransfersFragmentStatePagerAdapter
        tablayout_players.setupWithViewPager(viewpager_players_transfers)

        model.boardGame.liveData { it.playersObservable }.observe(this, Observer {
            playersTransfersFragmentStatePagerAdapter.players.clear()
            playersTransfersFragmentStatePagerAdapter.players.addAll(
                    it.values.sortedWith(LiquidityApplication.playerComparator(requireContext()))
            )
            playersTransfersFragmentStatePagerAdapter.notifyDataSetChanged()
        })
    }

}
