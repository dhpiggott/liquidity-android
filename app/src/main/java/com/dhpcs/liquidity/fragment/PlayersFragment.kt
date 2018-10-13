package com.dhpcs.liquidity.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import androidx.recyclerview.widget.SortedListAdapterCallback
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
        ) : RecyclerView.Adapter<PlayerViewHolder>() {

            private val players = SortedList(
                    BoardGame.Companion.PlayerWithBalanceAndConnectionState::class.java,
                    object : SortedListAdapterCallback<
                            BoardGame.Companion.PlayerWithBalanceAndConnectionState
                            >(this) {

                        private val playerComparator = BoardGameActivity
                                .playerComparator(playersFragment.context!!)

                        override fun compare(
                                o1: BoardGame.Companion.PlayerWithBalanceAndConnectionState,
                                o2: BoardGame.Companion.PlayerWithBalanceAndConnectionState
                        ): Int = playerComparator.compare(o1, o2)

                        override fun areContentsTheSame(
                                oldItem: BoardGame.Companion.PlayerWithBalanceAndConnectionState,
                                newItem: BoardGame.Companion.PlayerWithBalanceAndConnectionState
                        ): Boolean = oldItem == newItem

                        override fun areItemsTheSame(
                                item1: BoardGame.Companion.PlayerWithBalanceAndConnectionState,
                                item2: BoardGame.Companion.PlayerWithBalanceAndConnectionState
                        ): Boolean = item1.memberId == item2.memberId

                    }
            )

            override fun getItemCount(): Int = players.size()

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
                val view = LayoutInflater
                        .from(parent.context)
                        .inflate(R.layout.linearlayout_player, parent, false)
                return PlayerViewHolder(playersFragment, view)
            }

            override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
                val player = players.get(position)
                holder.bindPlayer(player)
            }

            /**
             * @param player Must have same position according to the lists order as any item it
             * replaces. If properties of the player (i.e. its name) have changed
             * relative to any previous item, the replace method must instead be called.
             */
            internal fun replaceOrAdd(
                    player: BoardGame.Companion.PlayerWithBalanceAndConnectionState
            ) {
                players.add(player)
            }

            internal fun replace(oldPlayer:
                                 BoardGame.Companion.PlayerWithBalanceAndConnectionState,
                                 newPlayer:
                                 BoardGame.Companion.PlayerWithBalanceAndConnectionState) {
                players.updateItemAt(players.indexOf(oldPlayer), newPlayer)
            }

            internal fun remove(player: BoardGame.Companion.PlayerWithBalanceAndConnectionState) {
                players.remove(player)
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

    private var players: Map<String, BoardGame.Companion.PlayerWithBalanceAndConnectionState>? =
            null
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

    fun onPlayersInitialized(
            players: Collection<BoardGame.Companion.PlayerWithBalanceAndConnectionState>
    ) {
        players.forEach {
            replaceOrAddPlayer(it)
        }
        if (playersAdapter!!.itemCount != 0) {
            textViewEmpty!!.visibility = View.GONE
            recyclerViewPlayers!!.visibility = View.VISIBLE
        }
    }

    fun onPlayerAdded(addedPlayer: BoardGame.Companion.PlayerWithBalanceAndConnectionState) {
        replaceOrAddPlayer(addedPlayer)
        if (playersAdapter!!.itemCount != 0) {
            textViewEmpty!!.visibility = View.GONE
            recyclerViewPlayers!!.visibility = View.VISIBLE
        }
    }

    fun onPlayerChanged(changedPlayer: BoardGame.Companion.PlayerWithBalanceAndConnectionState) {
        if (selectedIdentity == null ||
                changedPlayer.memberId != selectedIdentity!!.memberId) {
            playersAdapter!!.replace(players!![changedPlayer.memberId]!!, changedPlayer)
        }
    }

    fun onPlayersUpdated(players: Map<String,
            BoardGame.Companion.PlayerWithBalanceAndConnectionState>) {
        this.players = players
    }

    fun onPlayerRemoved(removedPlayer: BoardGame.Companion.PlayerWithBalanceAndConnectionState) {
        if (selectedIdentity == null ||
                removedPlayer.memberId != selectedIdentity!!.memberId) {
            playersAdapter!!.remove(removedPlayer)
        }
        if (playersAdapter!!.itemCount == 0) {
            textViewEmpty!!.visibility = View.VISIBLE
            recyclerViewPlayers!!.visibility = View.GONE
        }
    }

    fun onSelectedIdentityChanged(selectedIdentity: BoardGame.Companion.Identity?) {
        if (this.selectedIdentity != null && players != null) {
            val player = players!![this.selectedIdentity!!.memberId]
            if (player != null) playersAdapter!!.replaceOrAdd(player)
        }
        this.selectedIdentity = selectedIdentity
        if (this.selectedIdentity != null && players != null) {
            val player = players!![this.selectedIdentity!!.memberId]
            if (player != null) playersAdapter!!.remove(player)
        }
        textViewEmpty!!.visibility =
                if (playersAdapter!!.itemCount == 0) View.VISIBLE else View.GONE
        recyclerViewPlayers!!.visibility =
                if (playersAdapter!!.itemCount == 0) View.GONE else View.VISIBLE
    }

    private fun replaceOrAddPlayer(
            player: BoardGame.Companion.PlayerWithBalanceAndConnectionState
    ) {
        if (selectedIdentity == null || player.memberId != selectedIdentity!!.memberId) {
            playersAdapter!!.replaceOrAdd(player)
        }
    }

}
