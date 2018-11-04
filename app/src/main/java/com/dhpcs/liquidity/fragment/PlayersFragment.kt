package com.dhpcs.liquidity.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.*
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.BoardGameActivity
import com.dhpcs.liquidity.view.Identicon

class PlayersFragment : Fragment() {

    companion object {

        interface Listener {

            fun onNoPlayersTextClicked()

            fun onPlayerClicked(player: BoardGame.Companion.Player)

        }

        private class PlayersAdapter
        internal constructor(private val playersFragment: PlayersFragment
        ) : ListAdapter<BoardGame.Companion.PlayerWithBalanceAndConnectionState, PlayerViewHolder>(
                object : DiffUtil.ItemCallback<BoardGame.Companion.PlayerWithBalanceAndConnectionState>() {

                    override fun areContentsTheSame(
                            oldItem: BoardGame.Companion.PlayerWithBalanceAndConnectionState,
                            newItem: BoardGame.Companion.PlayerWithBalanceAndConnectionState
                    ): Boolean {
                        return oldItem.name == newItem.name &&
                                oldItem.balance == newItem.balance &&
                                oldItem.isConnected == newItem.isConnected
                    }

                    override fun areItemsTheSame(
                            item1: BoardGame.Companion.PlayerWithBalanceAndConnectionState,
                            item2: BoardGame.Companion.PlayerWithBalanceAndConnectionState
                    ): Boolean = item1.memberId == item2.memberId

                }
        ) {

            private val playerComparator = BoardGameActivity
                    .playerComparator(playersFragment.context!!)

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
                val view = LayoutInflater
                        .from(parent.context)
                        .inflate(R.layout.linearlayout_player, parent, false)
                return PlayerViewHolder(playersFragment, view)
            }

            override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
                holder.bindPlayer(getItem(position))
            }

            internal fun updatePlayers(
                    players: Collection<BoardGame.Companion.PlayerWithBalanceAndConnectionState>) {
                submitList(players.sortedWith(playerComparator))
            }

        }

        private class PlayerViewHolder(private val playersFragment: PlayersFragment,
                                       itemView: View
        ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

            private val identiconId = itemView.findViewById<Identicon>(R.id.identicon_id)
            private val textViewName = itemView.findViewById<TextView>(R.id.textview_name)
            private val textViewBalance = itemView.findViewById<TextView>(R.id.textview_balance)
            private val textViewStatus = itemView.findViewById<TextView>(R.id.textview_status)

            init {
                itemView.setOnClickListener(this)
            }

            private var player: BoardGame.Companion.Player? = null

            fun bindPlayer(player: BoardGame.Companion.PlayerWithBalanceAndConnectionState) {
                this.player = player

                val zoneId = player.zoneId
                val memberId = player.memberId
                val name = BoardGameActivity.formatNullable(playersFragment.context!!, player.name)
                val balance = BoardGameActivity.formatCurrencyValue(
                        playersFragment.context!!,
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
                playersFragment.listener?.onPlayerClicked(player!!)
            }

        }

    }

    private var playersAdapter: PlayersAdapter? = null

    private var textViewEmpty: TextView? = null
    private var recyclerViewPlayers: RecyclerView? = null

    private var players: Collection<BoardGame.Companion.PlayerWithBalanceAndConnectionState> =
            emptyList()
    private var selectedIdentity: BoardGame.Companion.Identity? = null

    internal var listener: Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playersAdapter = PlayersAdapter(this)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_players, container, false)

        textViewEmpty = view.findViewById(R.id.textview_empty)
        recyclerViewPlayers = view.findViewById(R.id.recyclerview_players)

        textViewEmpty!!.setOnClickListener {
            listener?.onNoPlayersTextClicked()
        }
        recyclerViewPlayers!!.setHasFixedSize(true)
        recyclerViewPlayers!!.layoutManager = LinearLayoutManager(activity)
        recyclerViewPlayers!!.adapter = playersAdapter

        return view
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        listener = context as Listener?
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    fun onPlayersUpdated(players: Map<String,
            BoardGame.Companion.PlayerWithBalanceAndConnectionState>) {
        this.players = players.values
        updatePlayers(this.players, selectedIdentity)
    }

    fun onSelectedIdentityChanged(selectedIdentity: BoardGame.Companion.Identity?) {
        this.selectedIdentity = selectedIdentity
        updatePlayers(players, selectedIdentity)
    }

    private fun updatePlayers(
            players: Collection<BoardGame.Companion.PlayerWithBalanceAndConnectionState>,
            selectedIdentity: BoardGame.Companion.Identity?) {
        val visiblePlayers = players.filter {
            selectedIdentity == null ||
                    it.memberId != selectedIdentity.memberId
        }
        playersAdapter!!.updatePlayers(visiblePlayers)
        if (visiblePlayers.isNotEmpty()) {
            textViewEmpty!!.visibility = View.GONE
            recyclerViewPlayers!!.visibility = View.VISIBLE
        } else {
            textViewEmpty!!.visibility = View.VISIBLE
            recyclerViewPlayers!!.visibility = View.GONE
        }
    }

}
