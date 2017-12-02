package com.dhpcs.liquidity.fragment

import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextSwitcher
import android.widget.TextView
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.BoardGameActivity
import com.dhpcs.liquidity.boardgame.BoardGame.TransferWithCurrency
import org.joda.time.Instant

class LastTransferFragment : Fragment() {

    companion object {

        private const val REFRESH_INTERVAL: Long = 60000

    }

    private val refreshHandler = Handler()
    private val refreshRunnable = Runnable { showTransfer(lastTransfer, false) }

    private var textViewEmpty: TextView? = null
    private var textSwitcherSummary: TextSwitcher? = null
    private var textSwitcherCreated: TextSwitcher? = null

    private var lastTransfer: TransferWithCurrency? = null

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_last_transfer, container, false)

        textViewEmpty = view.findViewById(R.id.textview_empty)
        textSwitcherSummary = view.findViewById(R.id.textswitcher_summary)
        textSwitcherCreated = view.findViewById(R.id.textswitcher_created)

        return view
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onDestroy()
    }

    fun onTransfersInitialized(transfers: scala.collection.Iterable<TransferWithCurrency>) {
        val iterator = transfers.iterator()
        while (iterator.hasNext()) {
            val transfer = iterator.next()
            if (lastTransfer == null ||
                    transfer.transaction().created() > lastTransfer!!.transaction().created()) {
                lastTransfer = transfer
            }
        }
        if (lastTransfer != null) showTransfer(lastTransfer, false)
    }

    fun onTransferAdded(addedTransfer: TransferWithCurrency) {
        if (lastTransfer == null ||
                addedTransfer.transaction().created() > lastTransfer!!.transaction().created()) {
            lastTransfer = addedTransfer
            showTransfer(lastTransfer, true)
        }
    }

    fun onTransfersChanged(changedTransfers: scala.collection.Iterable<TransferWithCurrency>) {
        val iterator = changedTransfers.iterator()
        while (iterator.hasNext()) {
            val transfer = iterator.next()
            if (transfer.transaction().id() == lastTransfer!!.transaction().id()) {
                lastTransfer = transfer
                showTransfer(lastTransfer, false)
                break
            }
        }
    }

    private fun showTransfer(transfer: TransferWithCurrency?, animate: Boolean) {
        val summary = getString(
                R.string.transfer_summary_format_string,
                BoardGameActivity.formatMemberOrAccount(activity!!, lastTransfer!!.from()),
                BoardGameActivity.formatCurrencyValue(
                        activity!!,
                        transfer!!.currency(),
                        transfer.transaction().value()
                ),
                BoardGameActivity.formatMemberOrAccount(activity!!, lastTransfer!!.to())
        )
        val createdTimeMillis = transfer.transaction().created()
        val currentTimeMillis = System.currentTimeMillis()
        val created = LiquidityApplication.getRelativeTimeSpanString(
                activity!!,
                Instant(createdTimeMillis),
                Instant(
                        if (currentTimeMillis < createdTimeMillis)
                            createdTimeMillis
                        else
                            currentTimeMillis
                ),
                REFRESH_INTERVAL
        )

        if (animate) {
            textSwitcherSummary!!.setText(summary)
            textSwitcherCreated!!.setText(created)
        } else {
            textSwitcherSummary!!.setCurrentText(summary)
            textSwitcherCreated!!.setCurrentText(created)
        }
        textViewEmpty!!.visibility = View.GONE
        textSwitcherSummary!!.visibility = View.VISIBLE
        textSwitcherCreated!!.visibility = View.VISIBLE
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL)
    }

}
