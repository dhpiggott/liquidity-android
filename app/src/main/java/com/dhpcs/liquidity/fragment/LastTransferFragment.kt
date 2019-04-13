package com.dhpcs.liquidity.fragment

import android.media.MediaPlayer
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import com.dhpcs.liquidity.activity.MainActivity.Companion.liveData
import io.reactivex.Observable
import kotlinx.android.synthetic.main.fragment_last_transfer.*
import org.joda.time.Instant
import java.util.concurrent.TimeUnit

class LastTransferFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_last_transfer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)

        val transferReceiptMediaPlayer = MediaPlayer.create(
                requireContext(),
                R.raw.antique_cash_register_punching_single_key
        )
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                transferReceiptMediaPlayer.release()
            }
        })
        model.boardGame.liveData {
            Observable.merge(
                    it.transfersObservable.filter { transfers ->
                        transfers.isNotEmpty()
                    }.map { transfers ->
                        Pair(transfers.last(), false)
                    },
                    it.addedTransfersObservable.map { transfer ->
                        Pair(transfer, true)
                    }
            ).switchMap { (transfer, justAdded) ->
                Observable.concat(
                        Observable.just(Pair(transfer, justAdded)),
                        Observable.interval(60, TimeUnit.SECONDS)
                                .map { Pair(transfer, false) }
                )
            }
        }.observe(this, Observer { (transfer, justAdded) ->
            textview_empty.visibility = View.GONE
            textswitcher_summary.visibility = View.VISIBLE
            textswitcher_created.visibility = View.VISIBLE
            val summary = getString(
                    R.string.transfer_summary_format_string,
                    LiquidityApplication.formatNullable(
                            requireContext(),
                            transfer.fromPlayer?.name
                    ),
                    LiquidityApplication.formatCurrencyValue(
                            requireContext(),
                            transfer.currency,
                            transfer.value
                    ),
                    LiquidityApplication.formatNullable(requireContext(), transfer.toPlayer?.name)
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
                    TimeUnit.MINUTES.toMillis(1)
            )
            if (justAdded) {
                view.handler?.removeCallbacksAndMessages(null)
                textswitcher_summary.setText(summary)
                textswitcher_created.setText(created)
                val playTransferReceiptSounds =
                        PreferenceManager
                                .getDefaultSharedPreferences(requireContext())
                                .getBoolean("play_transfer_receipt_sounds", true)
                if (transfer.toPlayer != null && playTransferReceiptSounds &&
                        transfer.toPlayer.ownerPublicKey == model.serverConnection.clientKey
                ) {
                    if (transferReceiptMediaPlayer.isPlaying) {
                        transferReceiptMediaPlayer.seekTo(0)
                    } else {
                        transferReceiptMediaPlayer.start()
                    }
                }
            } else {
                textswitcher_summary.setCurrentText(summary)
                textswitcher_created.setCurrentText(created)
            }
        })
    }

}
