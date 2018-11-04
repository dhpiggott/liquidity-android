package com.dhpcs.liquidity.fragment

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextSwitcher
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.BoardGameActivity
import org.joda.time.Instant

class LastTransferFragment : Fragment() {

    companion object {

        private const val REFRESH_INTERVAL: Long = 60000

    }

    private val refreshHandler = Handler()
    private val refreshRunnable = Runnable { showTransfer(lastTransfer!!, false) }

    private var textViewEmpty: TextView? = null
    private var textSwitcherSummary: TextSwitcher? = null
    private var textSwitcherCreated: TextSwitcher? = null

    private var lastTransfer: BoardGame.Companion.Transfer? = null

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

    fun onTransferAdded(transfer: BoardGame.Companion.Transfer) {
        if (lastTransfer == null ||
                transfer.created > lastTransfer!!.created) {
            lastTransfer = transfer
            showTransfer(lastTransfer!!, true)
        }
    }

    fun onTransfersUpdated(transfers: Map<String, BoardGame.Companion.Transfer>) {
        transfers.values.forEach {
            if (lastTransfer == null ||
                    it.created > lastTransfer!!.created) {
                lastTransfer = it
            }
        }
        if (lastTransfer != null) showTransfer(lastTransfer!!, false)
    }

    private fun showTransfer(transfer: BoardGame.Companion.Transfer,
                             animate: Boolean
    ) {
        val summary = getString(
                R.string.transfer_summary_format_string,
                BoardGameActivity.formatNullable(requireContext(), transfer.fromPlayer?.name),
                BoardGameActivity.formatCurrencyValue(
                        requireContext(),
                        transfer.currency,
                        transfer.value
                ),
                BoardGameActivity.formatNullable(requireContext(), transfer.toPlayer?.name)
        )
        val createdTimeMillis = transfer.created
        val currentTimeMillis = System.currentTimeMillis()
        val created = LiquidityApplication.getRelativeTimeSpanString(
                requireContext(),
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
