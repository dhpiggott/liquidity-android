package com.dhpcs.liquidity.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextSwitcher;
import android.widget.TextView;

import com.dhpcs.liquidity.BoardGame.TransferWithCurrency;
import com.dhpcs.liquidity.LiquidityApplication;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activity.BoardGameActivity;

import org.joda.time.Instant;

import scala.collection.Iterator;

public class LastTransferFragment extends Fragment {

    private static final long REFRESH_INTERVAL = 60_000;

    private final Handler refreshHandler = new Handler();
    private final Runnable refreshRunnable = new Runnable() {

        @Override
        public void run() {
            showTransfer(lastTransfer, false);
        }

    };

    private TextView textViewEmpty;
    private TextSwitcher textSwitcherSummary;
    private TextSwitcher textSwitcherCreated;

    private TransferWithCurrency lastTransfer;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_last_transfer, container, false);

        textViewEmpty = (TextView) view.findViewById(R.id.textview_empty);
        textSwitcherSummary = (TextSwitcher) view.findViewById(R.id.textswitcher_summary);
        textSwitcherCreated = (TextSwitcher) view.findViewById(R.id.textswitcher_created);

        return view;
    }

    @Override
    public void onDestroy() {
        refreshHandler.removeCallbacks(refreshRunnable);
        super.onDestroy();
    }

    public void onTransferAdded(TransferWithCurrency addedTransfer) {
        if (lastTransfer == null ||
                addedTransfer.transaction().created() > lastTransfer.transaction().created()) {
            lastTransfer = addedTransfer;
            showTransfer(lastTransfer, true);
        }
    }

    public void onTransfersChanged(
            scala.collection.Iterable<TransferWithCurrency> changedTransfers) {
        Iterator<TransferWithCurrency> iterator = changedTransfers.iterator();
        while (iterator.hasNext()) {
            TransferWithCurrency transfer = iterator.next();
            if (transfer.transaction().id().equals(lastTransfer.transaction().id())) {
                lastTransfer = transfer;
                showTransfer(lastTransfer, false);
                break;
            }
        }
    }

    public void onTransfersInitialized(scala.collection.Iterable<TransferWithCurrency> transfers) {
        Iterator<TransferWithCurrency> iterator = transfers.iterator();
        while (iterator.hasNext()) {
            TransferWithCurrency transfer = iterator.next();
            if (lastTransfer == null ||
                    transfer.transaction().created() > lastTransfer.transaction().created()) {
                lastTransfer = transfer;
            }
        }
        if (lastTransfer != null) {
            showTransfer(lastTransfer, false);
        }
    }

    private void showTransfer(TransferWithCurrency transfer, boolean animate) {
        String summary = getString(
                R.string.transfer_summary_format_string,
                BoardGameActivity.formatMemberOrAccount(getActivity(), lastTransfer.from()),
                BoardGameActivity.formatCurrencyValue(
                        getActivity(),
                        transfer.currency(),
                        transfer.transaction().value()
                ),
                BoardGameActivity.formatMemberOrAccount(getActivity(), lastTransfer.to())
        );
        String created = LiquidityApplication.getRelativeTimeSpanString(
                getActivity(),
                new Instant(transfer.transaction().created()),
                new Instant(System.currentTimeMillis()),
                REFRESH_INTERVAL
        );

        if (animate) {
            textSwitcherSummary.setText(summary);
            textSwitcherCreated.setText(created);
        } else {
            textSwitcherSummary.setCurrentText(summary);
            textSwitcherCreated.setCurrentText(created);
        }
        textViewEmpty.setVisibility(View.GONE);
        textSwitcherSummary.setVisibility(View.VISIBLE);
        textSwitcherCreated.setVisibility(View.VISIBLE);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

}
