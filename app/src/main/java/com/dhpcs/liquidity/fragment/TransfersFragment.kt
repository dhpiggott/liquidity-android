package com.dhpcs.liquidity.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.*
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import com.dhpcs.liquidity.activity.MainActivity.Companion.liveData
import kotlinx.android.synthetic.main.fragment_transfers.*
import java.text.DateFormat

class TransfersFragment : Fragment() {

    companion object {

        private const val ARG_PLAYER_ID = "player_id"

        private val timeFormat = DateFormat.getTimeInstance()
        private val dateFormat = DateFormat.getDateInstance()

        fun newInstance(player: BoardGame.Companion.Player?): TransfersFragment {
            val transfersFragment = TransfersFragment()
            val args = Bundle()
            args.putString(ARG_PLAYER_ID, player?.memberId)
            transfersFragment.arguments = args
            return transfersFragment
        }

        private class TransfersAdapter
        internal constructor(private val player: BoardGame.Companion.Player?) :
                ListAdapter<BoardGame.Companion.Transfer, TransferViewHolder>(
                        object : DiffUtil.ItemCallback<BoardGame.Companion.Transfer>() {

                            override fun areContentsTheSame(
                                    oldItem: BoardGame.Companion.Transfer,
                                    newItem: BoardGame.Companion.Transfer
                            ): Boolean {
                                val isFromPlayer = player != null &&
                                        oldItem.fromPlayer?.memberId == player.memberId
                                val isToPlayer = player != null &&
                                        oldItem.toPlayer?.memberId == player.memberId
                                return if (isFromPlayer && !isToPlayer) {
                                    oldItem.toPlayer?.name == newItem.toPlayer?.name
                                } else if (!isFromPlayer && isToPlayer) {
                                    oldItem.fromPlayer?.name == newItem.fromPlayer?.name
                                } else {
                                    oldItem.fromPlayer?.name == newItem.fromPlayer?.name &&
                                            oldItem.toPlayer?.name == newItem.toPlayer?.name
                                }
                            }

                            override fun areItemsTheSame(
                                    item1: BoardGame.Companion.Transfer,
                                    item2: BoardGame.Companion.Transfer
                            ): Boolean = item1.transactionId == item2.transactionId

                        }
                ) {

            private val transferComparator =
                    Comparator<BoardGame.Companion.Transfer> { o1, o2 ->
                        val lhsCreated = o1.created
                        val rhsCreated = o2.created
                        when {
                            lhsCreated < rhsCreated -> 1
                            lhsCreated > rhsCreated -> -1
                            else -> {
                                val lhsId = o1.transactionId
                                val rhsId = o2.transactionId
                                lhsId.compareTo(rhsId)
                            }
                        }
                    }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransferViewHolder {
                val view = LayoutInflater
                        .from(parent.context)
                        .inflate(R.layout.linearlayout_transfer, parent, false)
                return TransferViewHolder(view, parent.context, player)
            }

            override fun onBindViewHolder(holder: TransferViewHolder, position: Int) {
                holder.bindTransfer(getItem(position))
            }

            internal fun updateTransfers(
                    transfers: Collection<BoardGame.Companion.Transfer>) {
                submitList(transfers.sortedWith(transferComparator))
            }

        }

        private class TransferViewHolder(itemView: View,
                                         private val context: Context,
                                         private val player: BoardGame.Companion.Player?
        ) : RecyclerView.ViewHolder(itemView) {

            private val textViewSummary =
                    itemView.findViewById<TextView>(R.id.textview_summary)
            private val textViewCreatedTime =
                    itemView.findViewById<TextView>(R.id.textview_created_time)
            private val textViewCreatedDate =
                    itemView.findViewById<TextView>(R.id.textview_created_date)

            fun bindTransfer(transfer: BoardGame.Companion.Transfer) {
                val isFromPlayer = player != null &&
                        transfer.fromPlayer?.memberId == player.memberId
                val value = LiquidityApplication.formatCurrencyValue(
                        context,
                        transfer.currency,
                        transfer.value
                )
                val isToPlayer = player != null &&
                        transfer.toPlayer?.memberId == player.memberId
                val summary = if (isFromPlayer && !isToPlayer) {
                    context.getString(
                            R.string.transfer_summary_sent_to_format_string,
                            value,
                            LiquidityApplication.formatNullable(context, transfer.toPlayer?.name)
                    )
                } else if (!isFromPlayer && isToPlayer) {
                    context.getString(
                            R.string.transfer_summary_received_from_format_string,
                            value,
                            LiquidityApplication.formatNullable(context, transfer.fromPlayer?.name)
                    )
                } else {
                    context.getString(
                            R.string.transfer_summary_format_string,
                            LiquidityApplication.formatNullable(context, transfer.fromPlayer?.name),
                            value,
                            LiquidityApplication.formatNullable(context, transfer.toPlayer?.name)
                    )
                }
                val createdTime = context.getString(
                        R.string.transfer_created_time_format_string,
                        timeFormat.format(transfer.created)
                )
                val createdDate = context.getString(
                        R.string.transfer_created_date_format_string,
                        dateFormat.format(transfer.created)
                )
                textViewSummary.text = summary
                textViewCreatedTime.text = createdTime
                textViewCreatedDate.text = createdDate
            }

        }

    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_transfers, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)

        val playerId = arguments!!.getString(ARG_PLAYER_ID)

        val player = if (playerId != null) {
            model.boardGame.players[playerId]
        } else {
            null
        }

        val transfersAdapter = TransfersAdapter(player)

        recyclerview_transfers.setHasFixedSize(true)
        recyclerview_transfers.layoutManager = LinearLayoutManager(activity)
        recyclerview_transfers.adapter = transfersAdapter
        recyclerview_transfers.addItemDecoration(DividerItemDecoration(
                recyclerview_transfers.context,
                DividerItemDecoration.VERTICAL
        ))

        if (transfersAdapter.itemCount != 0) {
            textview_empty.visibility = View.GONE
            recyclerview_transfers.visibility = View.VISIBLE
        }

        model.boardGame.liveData { it.transfersObservable }.observe(this, Observer {
            val visibleTransfers = it.filter { transfer ->
                player == null ||
                        (transfer.fromPlayer != null &&
                                player.memberId == transfer.fromPlayer.memberId) ||
                        (transfer.toPlayer != null &&
                                player.memberId == transfer.toPlayer.memberId)
            }
            transfersAdapter.updateTransfers(visibleTransfers)
            if (visibleTransfers.isNotEmpty()) {
                textview_empty.visibility = View.GONE
                recyclerview_transfers.visibility = View.VISIBLE
            } else {
                textview_empty.visibility = View.VISIBLE
                recyclerview_transfers.visibility = View.GONE
            }
        })
    }

}
