package com.dhpcs.liquidity.fragments;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextSwitcher;
import android.widget.TextView;

import com.dhpcs.liquidity.MonopolyGame.TransferWithCurrency;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;

import java.text.DateFormat;

import scala.collection.Iterator;

public class LastTransferFragment extends Fragment {

    private final DateFormat timeFormat = DateFormat.getTimeInstance();

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
            if (transfer.transactionId().equals(lastTransfer.transactionId())) {
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
                MonopolyGameActivity.formatMemberOrAccount(getActivity(), lastTransfer.from()),
                MonopolyGameActivity.formatCurrencyValue(
                        getActivity(),
                        transfer.currency(),
                        transfer.transaction().value()
                ),
                MonopolyGameActivity.formatMemberOrAccount(getActivity(), lastTransfer.to())
        );
        String created = timeFormat.format(transfer.transaction().created());

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
    }

}
