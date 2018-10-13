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
import java.text.DateFormat
import java.util.*

class TransfersFragment : Fragment() {

    companion object {

        private const val ARG_PLAYER = "player"
        private const val ARG_TRANSFERS = "transfers"

        private val timeFormat = DateFormat.getTimeInstance()
        private val dateFormat = DateFormat.getDateInstance()

        fun newInstance(player: BoardGame.Companion.Player?,
                        transfers: ArrayList<BoardGame.Companion.TransferWithCurrency>?
        ): TransfersFragment {
            val transfersFragment = TransfersFragment()
            val args = Bundle()
            args.putSerializable(ARG_PLAYER, player)
            args.putSerializable(ARG_TRANSFERS, transfers)
            transfersFragment.arguments = args
            return transfersFragment
        }

        private class TransfersAdapter
        internal constructor(private val player: BoardGame.Companion.Player?) :
                RecyclerView.Adapter<TransferViewHolder>() {

            private val transfers = SortedList(
                    BoardGame.Companion.TransferWithCurrency::class.java,
                    object : SortedListAdapterCallback<
                            BoardGame.Companion.TransferWithCurrency
                            >(this) {

                        override fun compare(
                                o1: BoardGame.Companion.TransferWithCurrency,
                                o2: BoardGame.Companion.TransferWithCurrency
                        ): Int {
                            val lhsCreated = o1.created
                            val rhsCreated = o2.created
                            return when {
                                lhsCreated < rhsCreated -> 1
                                lhsCreated > rhsCreated -> -1
                                else -> {
                                    val lhsId = o1.transactionId
                                    val rhsId = o2.transactionId
                                    lhsId.compareTo(rhsId)
                                }
                            }
                        }

                        override fun areContentsTheSame(
                                oldItem: BoardGame.Companion.TransferWithCurrency,
                                newItem: BoardGame.Companion.TransferWithCurrency
                        ): Boolean = oldItem == newItem

                        override fun areItemsTheSame(
                                item1: BoardGame.Companion.TransferWithCurrency,
                                item2: BoardGame.Companion.TransferWithCurrency
                        ): Boolean = item1.transactionId == item2.transactionId

                    }
            )

            override fun getItemCount(): Int {
                return transfers.size()
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransferViewHolder {
                val view = LayoutInflater
                        .from(parent.context)
                        .inflate(R.layout.linearlayout_transfer, parent, false)
                return TransferViewHolder(view, parent.context, player)
            }

            override fun onBindViewHolder(holder: TransferViewHolder, position: Int) {
                val transfer = transfers.get(position)
                holder.bindTransfer(transfer)
            }

            internal fun beginBatchedUpdates() = transfers.beginBatchedUpdates()

            internal fun replaceOrAdd(transfer: BoardGame.Companion.TransferWithCurrency) {
                transfers.add(transfer)
            }

            internal fun endBatchedUpdates() = transfers.endBatchedUpdates()

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

            fun bindTransfer(transfer: BoardGame.Companion.TransferWithCurrency) {
                val isFromPlayer = player != null &&
                        transfer.fromPlayer?.memberId == player.memberId
                val value = BoardGameActivity.formatCurrencyValue(
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
                            BoardGameActivity.formatAccountOrPlayer(
                                    context,
                                    transfer.toAccountId,
                                    transfer.toAccountName,
                                    transfer.toPlayer
                            )
                    )
                } else if (!isFromPlayer && isToPlayer) {
                    context.getString(
                            R.string.transfer_summary_received_from_format_string,
                            value,
                            BoardGameActivity.formatAccountOrPlayer(
                                    context,
                                    transfer.fromAccountId,
                                    transfer.fromAccountName,
                                    transfer.fromPlayer
                            )
                    )
                } else {
                    context.getString(
                            R.string.transfer_summary_format_string,
                            BoardGameActivity.formatAccountOrPlayer(
                                    context,
                                    transfer.fromAccountId,
                                    transfer.fromAccountName,
                                    transfer.fromPlayer
                            ),
                            value,
                            BoardGameActivity.formatAccountOrPlayer(
                                    context,
                                    transfer.toAccountId,
                                    transfer.toAccountName,
                                    transfer.toPlayer
                            )
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

    private var transfersAdapter: TransfersAdapter? = null

    private var textViewEmpty: TextView? = null
    private var recyclerViewTransfers: RecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val player = arguments!!.getSerializable(ARG_PLAYER) as BoardGame.Companion.Player?
        val transfers = arguments!!.getSerializable(ARG_TRANSFERS) as
                List<BoardGame.Companion.TransferWithCurrency>?

        transfersAdapter = TransfersAdapter(player)

        if (transfers != null) {
            transfersAdapter!!.beginBatchedUpdates()
            for (transfer in transfers) {
                replaceOrAddTransfer(player, transfer)
            }
            transfersAdapter!!.endBatchedUpdates()
        }
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_transfers, container, false)

        textViewEmpty = view.findViewById(R.id.textview_empty)
        recyclerViewTransfers = view.findViewById(R.id.recyclerview_transfers)

        recyclerViewTransfers!!.setHasFixedSize(true)
        recyclerViewTransfers!!.layoutManager = LinearLayoutManager(activity)
        recyclerViewTransfers!!.adapter = transfersAdapter
        recyclerViewTransfers!!.addItemDecoration(DividerItemDecoration(
                recyclerViewTransfers!!.context,
                DividerItemDecoration.VERTICAL
        ))

        if (transfersAdapter!!.itemCount != 0) {
            textViewEmpty!!.visibility = View.GONE
            recyclerViewTransfers!!.visibility = View.VISIBLE
        }

        return view
    }

    fun onTransfersInitialized(transfers: Collection<BoardGame.Companion.TransferWithCurrency>) {
        replaceOrAddTransfers(transfers)
        if (transfers.isNotEmpty()) {
            textViewEmpty!!.visibility = View.GONE
            recyclerViewTransfers!!.visibility = View.VISIBLE
        }
    }

    fun onTransferAdded(addedTransfer: BoardGame.Companion.TransferWithCurrency) {
        replaceOrAddTransfer(
                arguments!!.getSerializable(ARG_PLAYER) as BoardGame.Companion.Player?,
                addedTransfer
        )
        textViewEmpty!!.visibility = View.GONE
        recyclerViewTransfers!!.visibility = View.VISIBLE
    }

    fun onTransfersChanged(changedTransfers: Collection<BoardGame.Companion.TransferWithCurrency>) {
        replaceOrAddTransfers(changedTransfers)
    }

    private fun replaceOrAddTransfers(
            transfers: Collection<BoardGame.Companion.TransferWithCurrency>
    ) {
        val player = arguments!!.getSerializable(ARG_PLAYER) as BoardGame.Companion.Player?
        transfers.forEach {
            replaceOrAddTransfer(player, it)
        }
    }

    private fun replaceOrAddTransfer(player: BoardGame.Companion.Player?,
                                     transfer: BoardGame.Companion.TransferWithCurrency
    ) {
        if (player == null ||
                transfer.fromPlayer != null && player.memberId == transfer.fromPlayer.memberId ||
                transfer.toPlayer != null && player.memberId == transfer.toPlayer.memberId) {
            transfersAdapter!!.replaceOrAdd(transfer)
        }
    }

}
