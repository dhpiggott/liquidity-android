package com.dhpcs.liquidity.fragment

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.util.SortedList
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.util.SortedListAdapterCallback
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.BoardGameActivity
import com.dhpcs.liquidity.boardgame.BoardGame.*
import com.dhpcs.liquidity.model.MemberId
import com.dhpcs.liquidity.view.Identicon

class PlayersFragment : Fragment() {

    companion object {

        interface Listener {

            fun onNoPlayersTextClicked()

            fun onPlayerClicked(player: Player)

        }

        private class PlayersAdapter
        internal constructor(private val playersFragment: PlayersFragment
        ) : RecyclerView.Adapter<PlayerViewHolder>() {

            private val players: SortedList<PlayerWithBalanceAndConnectionState> = SortedList(
                    PlayerWithBalanceAndConnectionState::class.java,
                    object : SortedListAdapterCallback<PlayerWithBalanceAndConnectionState>(this) {

                        private val playerComparator =
                                BoardGameActivity.playerComparator(playersFragment.context)

                        override fun compare(o1: PlayerWithBalanceAndConnectionState,
                                             o2: PlayerWithBalanceAndConnectionState
                        ): Int = playerComparator.compare(o1, o2)

                        override fun
                                areContentsTheSame(oldItem: PlayerWithBalanceAndConnectionState,
                                                   newItem: PlayerWithBalanceAndConnectionState
                        ): Boolean = oldItem == newItem

                        override fun areItemsTheSame(item1: PlayerWithBalanceAndConnectionState,
                                                     item2: PlayerWithBalanceAndConnectionState
                        ): Boolean = item1.member().id() == item2.member().id()

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
            internal fun replaceOrAdd(player: PlayerWithBalanceAndConnectionState) {
                players.add(player)
            }

            internal fun replace(oldPlayer: PlayerWithBalanceAndConnectionState,
                                 newPlayer: PlayerWithBalanceAndConnectionState) {
                players.updateItemAt(players.indexOf(oldPlayer), newPlayer)
            }

            internal fun remove(player: PlayerWithBalanceAndConnectionState) {
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

            private var player: Player? = null

            fun bindPlayer(player: PlayerWithBalanceAndConnectionState) {
                this.player = player

                val zoneId = player.zoneId()
                val memberId = player.member().id()
                val name = BoardGameActivity.formatNullable(
                        playersFragment.context,
                        player.member().name()
                )
                val balance = BoardGameActivity.formatCurrencyValue(
                        playersFragment.context,
                        player.balanceWithCurrency()._2(),
                        player.balanceWithCurrency()._1()
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

    private var players: scala.collection.immutable.Map<MemberId,
            PlayerWithBalanceAndConnectionState>? = null
    private var selectedIdentity: Identity? = null

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
            players: scala.collection.Iterable<PlayerWithBalanceAndConnectionState>) {
        val iterator = players.iterator()
        while (iterator.hasNext()) {
            replaceOrAddPlayer(iterator.next())
        }
        if (playersAdapter!!.itemCount != 0) {
            textViewEmpty!!.visibility = View.GONE
            recyclerViewPlayers!!.visibility = View.VISIBLE
        }
    }

    fun onPlayerAdded(addedPlayer: PlayerWithBalanceAndConnectionState) {
        replaceOrAddPlayer(addedPlayer)
        if (playersAdapter!!.itemCount != 0) {
            textViewEmpty!!.visibility = View.GONE
            recyclerViewPlayers!!.visibility = View.VISIBLE
        }
    }

    fun onPlayerChanged(changedPlayer: PlayerWithBalanceAndConnectionState) {
        if (selectedIdentity == null ||
                changedPlayer.member().id() != selectedIdentity!!.member().id()) {
            playersAdapter!!.replace(players!!.apply(changedPlayer.member().id()), changedPlayer)
        }
    }

    fun onPlayersUpdated(players: scala.collection.immutable.Map<MemberId,
            PlayerWithBalanceAndConnectionState>) {
        this.players = players
    }

    fun onPlayerRemoved(removedPlayer: PlayerWithBalanceAndConnectionState) {
        if (selectedIdentity == null ||
                removedPlayer.member().id() != selectedIdentity!!.member().id()) {
            playersAdapter!!.remove(removedPlayer)
        }
        if (playersAdapter!!.itemCount == 0) {
            textViewEmpty!!.visibility = View.VISIBLE
            recyclerViewPlayers!!.visibility = View.GONE
        }
    }

    fun onSelectedIdentityChanged(selectedIdentity: Identity) {
        if (this.selectedIdentity != null && players != null) {
            val player = players!!.get(this.selectedIdentity!!.member().id())
            if (player.isDefined) playersAdapter!!.replaceOrAdd(player.get())
        }
        this.selectedIdentity = selectedIdentity
        if (this.selectedIdentity != null && players != null) {
            val player = players!!.get(this.selectedIdentity!!.member().id())
            if (player.isDefined) playersAdapter!!.remove(player.get())
        }
        textViewEmpty!!.visibility =
                if (playersAdapter!!.itemCount == 0) View.VISIBLE else View.GONE
        recyclerViewPlayers!!.visibility =
                if (playersAdapter!!.itemCount == 0) View.GONE else View.VISIBLE
    }

    private fun replaceOrAddPlayer(player: PlayerWithBalanceAndConnectionState) {
        if (selectedIdentity == null || player.member().id() != selectedIdentity!!.member().id()) {
            playersAdapter!!.replaceOrAdd(player)
        }
    }

}
