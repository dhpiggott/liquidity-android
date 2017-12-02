package com.dhpcs.liquidity.fragment

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.util.SortedList
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.util.SortedListAdapterCallback
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.BoardGameActivity
import com.dhpcs.liquidity.boardgame.BoardGame.Player
import com.dhpcs.liquidity.boardgame.BoardGame.TransferWithCurrency
import java.text.DateFormat
import java.util.*

class TransfersFragment : Fragment() {

    companion object {

        private const val ARG_PLAYER = "player"
        private const val ARG_TRANSFERS = "transfers"

        private val timeFormat = DateFormat.getTimeInstance()
        private val dateFormat = DateFormat.getDateInstance()

        fun newInstance(player: Player?,
                        transfers: ArrayList<TransferWithCurrency>?): TransfersFragment {
            val transfersFragment = TransfersFragment()
            val args = Bundle()
            args.putSerializable(ARG_PLAYER, player)
            args.putSerializable(ARG_TRANSFERS, transfers)
            transfersFragment.arguments = args
            return transfersFragment
        }

        internal class TransferViewHolder(itemView: View,
                                          private val context: Context,
                                          private val player: Player?
        ) : RecyclerView.ViewHolder(itemView) {

            private val textViewSummary =
                    itemView.findViewById<TextView>(R.id.textview_summary)
            private val textViewCreatedTime =
                    itemView.findViewById<TextView>(R.id.textview_created_time)
            private val textViewCreatedDate =
                    itemView.findViewById<TextView>(R.id.textview_created_date)

            fun bindTransfer(transfer: TransferWithCurrency) {
                val isFromPlayer = player != null &&
                        transfer.from().right().get().member().id() == player.member().id()
                val value = BoardGameActivity.formatCurrencyValue(
                        context,
                        transfer.currency(),
                        transfer.transaction().value()
                )
                val isToPlayer = player != null &&
                        transfer.to().right().get().member().id() == player.member().id()
                val summary = if (isFromPlayer && !isToPlayer) {
                    context.getString(
                            R.string.transfer_summary_sent_to_format_string,
                            value,
                            BoardGameActivity.formatMemberOrAccount(context, transfer.to())
                    )
                } else if (!isFromPlayer && isToPlayer) {
                    context.getString(
                            R.string.transfer_summary_received_from_format_string,
                            value,
                            BoardGameActivity.formatMemberOrAccount(context, transfer.from())
                    )
                } else {
                    context.getString(
                            R.string.transfer_summary_format_string,
                            BoardGameActivity.formatMemberOrAccount(context, transfer.from()),
                            value,
                            BoardGameActivity.formatMemberOrAccount(context, transfer.to())
                    )
                }
                val createdTime = context.getString(
                        R.string.transfer_created_time_format_string,
                        timeFormat.format(transfer.transaction().created())
                )
                val createdDate = context.getString(
                        R.string.transfer_created_date_format_string,
                        dateFormat.format(transfer.transaction().created())
                )
                textViewSummary.text = summary
                textViewCreatedTime.text = createdTime
                textViewCreatedDate.text = createdDate
            }

        }

        private class TransfersAdapter(private val player: Player?) :
                RecyclerView.Adapter<TransferViewHolder>() {
            private val transfers = SortedList(
                    TransferWithCurrency::class.java,
                    object : SortedListAdapterCallback<TransferWithCurrency>(this) {

                        override fun compare(o1: TransferWithCurrency,
                                             o2: TransferWithCurrency): Int {
                            val lhsCreated = o1.transaction().created()
                            val rhsCreated = o2.transaction().created()
                            return when {
                                lhsCreated < rhsCreated -> 1
                                lhsCreated > rhsCreated -> -1
                                else -> {
                                    val lhsId = o1.transaction().id().id()
                                    val rhsId = o2.transaction().id().id()
                                    lhsId.compareTo(rhsId)
                                }
                            }
                        }

                        override fun areContentsTheSame(oldItem: TransferWithCurrency,
                                                        newItem: TransferWithCurrency): Boolean {
                            return oldItem == newItem
                        }

                        override fun areItemsTheSame(item1: TransferWithCurrency,
                                                     item2: TransferWithCurrency): Boolean {
                            return item1.transaction().id() == item2.transaction().id()
                        }

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

            internal fun replaceOrAdd(transfer: TransferWithCurrency) = transfers.add(transfer)

            internal fun endBatchedUpdates() = transfers.endBatchedUpdates()

        }

    }

    private var transfersAdapter: TransfersAdapter? = null

    private var textViewEmpty: TextView? = null
    private var recyclerViewTransfers: RecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val player = arguments!!.getSerializable(ARG_PLAYER) as Player?
        val transfers =
                arguments!!.getSerializable(ARG_TRANSFERS) as ArrayList<TransferWithCurrency>?

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

    fun onTransfersInitialized(
            transfers: scala.collection.Iterable<TransferWithCurrency>) {
        replaceOrAddTransfers(transfers)
        if (transfers.nonEmpty()) {
            textViewEmpty!!.visibility = View.GONE
            recyclerViewTransfers!!.visibility = View.VISIBLE
        }
    }

    fun onTransferAdded(addedTransfer: TransferWithCurrency) {
        replaceOrAddTransfer(
                arguments!!.getSerializable(ARG_PLAYER) as Player?,
                addedTransfer
        )
        textViewEmpty!!.visibility = View.GONE
        recyclerViewTransfers!!.visibility = View.VISIBLE
    }

    fun onTransfersChanged(changedTransfers: scala.collection.Iterable<TransferWithCurrency>) {
        replaceOrAddTransfers(changedTransfers)
    }

    private fun replaceOrAddTransfers(transfers: scala.collection.Iterable<TransferWithCurrency>) {
        val player = arguments!!.getSerializable(ARG_PLAYER) as Player?
        val iterator = transfers.iterator()
        while (iterator.hasNext()) {
            replaceOrAddTransfer(player, iterator.next())
        }
    }

    private fun replaceOrAddTransfer(player: Player?, transfer: TransferWithCurrency) {
        if (player == null ||
                transfer.from().isRight && player.member().id() ==
                        transfer.from().right().get().member().id() ||
                transfer.to().isRight && player.member().id() ==
                        transfer.to().right().get().member().id()) {
            transfersAdapter!!.replaceOrAdd(transfer)
        }
    }

}
