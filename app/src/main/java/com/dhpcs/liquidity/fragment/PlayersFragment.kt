package com.dhpcs.liquidity.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import com.dhpcs.liquidity.activity.MainActivity.Companion.liveData
import com.dhpcs.liquidity.view.Identicon
import kotlinx.android.synthetic.main.fragment_players.*
import java.util.*

class PlayersFragment : Fragment() {

    companion object {

        private class PlayersAdapter
        internal constructor(
                private val model: MainActivity.Companion.BoardGameModel,
                private val fragmentManager: FragmentManager,
                context: Context
        ) : ListAdapter<BoardGame.Companion.Player, PlayerViewHolder>(
                object : DiffUtil.ItemCallback<BoardGame.Companion.Player>() {

                    override fun areContentsTheSame(
                            oldItem: BoardGame.Companion.Player,
                            newItem: BoardGame.Companion.Player
                    ): Boolean {
                        return oldItem.name == newItem.name &&
                                oldItem.balance == newItem.balance &&
                                oldItem.isConnected == newItem.isConnected
                    }

                    override fun areItemsTheSame(
                            item1: BoardGame.Companion.Player,
                            item2: BoardGame.Companion.Player
                    ): Boolean = item1.memberId == item2.memberId

                }
        ) {

            private val playerComparator = LiquidityApplication.playerComparator(context)

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
                val view = LayoutInflater
                        .from(parent.context)
                        .inflate(R.layout.linearlayout_player, parent, false)
                return PlayerViewHolder(model, fragmentManager, view)
            }

            override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
                holder.bindPlayer(getItem(position))
            }

            internal fun updatePlayers(
                    players: Collection<BoardGame.Companion.Player>) {
                submitList(players.sortedWith(playerComparator))
            }

        }

        private class PlayerViewHolder(
                private val model: MainActivity.Companion.BoardGameModel,
                private val fragmentManager: FragmentManager,
                itemView: View
        ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

            private val identiconId = itemView.findViewById<Identicon>(R.id.identicon_id)
            private val textViewName = itemView.findViewById<TextView>(R.id.textview_name)
            private val textViewBalance = itemView.findViewById<TextView>(R.id.textview_balance)
            private val textViewStatus = itemView.findViewById<TextView>(R.id.textview_status)

            init {
                itemView.setOnClickListener(this)
            }

            private lateinit var player: BoardGame.Companion.Player

            fun bindPlayer(player: BoardGame.Companion.Player) {
                this.player = player

                val zoneId = player.zoneId
                val memberId = player.memberId
                val name = LiquidityApplication.formatNullable(textViewName.context, player.name)
                val balance = LiquidityApplication.formatCurrencyValue(
                        textViewBalance.context,
                        player.currency,
                        player.balance
                )
                val status = if (player.isConnected) {
                    R.string.player_connected
                } else {
                    R.string.player_disconnected
                }

                identiconId.show(zoneId, memberId)
                textViewName.text = name
                textViewBalance.text = balance
                textViewStatus.setText(status)
            }

            override fun onClick(v: View) {
                val identity = model.selectedIdentity
                when (identity) {
                    is MainActivity.Companion.Optional.None -> {
                    }
                    is MainActivity.Companion.Optional.Some -> {
                        TransferToPlayerDialogFragment.newInstance(
                                ArrayList(model.boardGame.identities.values),
                                ArrayList(model.boardGame.players.values),
                                model.boardGame.currency,
                                identity.value,
                                player
                        ).show(fragmentManager, TransferToPlayerDialogFragment.TAG)
                    }
                }
            }

        }

    }


    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_players, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)

        val playersAdapter = PlayersAdapter(model, fragmentManager!!, requireContext())

        textview_empty.setOnClickListener(
                Navigation.createNavigateOnClickListener(
                        R.id.action_board_game_fragment_to_add_players_fragment,
                        null
                )
        )
        recyclerview_players.setHasFixedSize(true)
        recyclerview_players.layoutManager = LinearLayoutManager(activity)
        recyclerview_players.adapter = playersAdapter

        fun updatePlayers(
                players: Collection<BoardGame.Companion.Player>,
                selectedIdentity: MainActivity.Companion.Optional<BoardGame.Companion.Identity>) {
            val visiblePlayers = players.filter {
                when (selectedIdentity) {
                    is MainActivity.Companion.Optional.None ->
                        true
                    is MainActivity.Companion.Optional.Some -> {
                        it.memberId != selectedIdentity.value.memberId
                    }
                }
            }
            playersAdapter.updatePlayers(visiblePlayers)
            if (visiblePlayers.isNotEmpty()) {
                textview_empty.visibility = View.GONE
                recyclerview_players.visibility = View.VISIBLE
            } else {
                textview_empty.visibility = View.VISIBLE
                recyclerview_players.visibility = View.GONE
            }
        }

        model.boardGame.liveData { it.playersObservable }.observe(this, Observer {
            updatePlayers(it.values, model.selectedIdentity)
        })
        model.selectedIdentityLiveData.observe(this, Observer {
            updatePlayers(model.boardGame.players.values, it)
        })
    }

}
