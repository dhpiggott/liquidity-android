package com.dhpcs.liquidity.fragments;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dhpcs.liquidity.MonopolyGame.TransferWithCurrency;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;

import java.text.DateFormat;

import scala.collection.Iterator;

public class LastTransferFragment extends Fragment {

    private final DateFormat dateFormat = DateFormat.getDateTimeInstance();

    private TextView textViewSummary;
    private TextView textViewCreated;

    private TransferWithCurrency lastTransfer;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_last_transfer, container, false);

        textViewSummary = (TextView) view.findViewById(R.id.textview_summary);
        textViewCreated = (TextView) view.findViewById(R.id.textview_created);

        return view;
    }

    public void onTransferAdded(TransferWithCurrency addedTransfer) {
        if (lastTransfer == null ||
                addedTransfer.transaction().created() > lastTransfer.transaction().created()) {
            lastTransfer = addedTransfer;
            showTransfer(lastTransfer);
        }
    }

    public void onTransfersChanged(
            scala.collection.Iterable<TransferWithCurrency> changedTransfers) {
        Iterator<TransferWithCurrency> iterator = changedTransfers.iterator();
        while (iterator.hasNext()) {
            TransferWithCurrency transfer = iterator.next();
            if (transfer.transactionId().equals(lastTransfer.transactionId())) {
                lastTransfer = transfer;
                showTransfer(lastTransfer);
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
            showTransfer(lastTransfer);
        }
    }

    private void showTransfer(TransferWithCurrency transfer) {
        long created = transfer.transaction().created();
        String value = MonopolyGameActivity.formatCurrencyValue(
                getActivity(),
                transfer.currency(),
                transfer.transaction().value()
        );
        String summary = getString(
                R.string.transfer_summary_format_string,
                MonopolyGameActivity.formatMemberOrAccount(getActivity(), lastTransfer.from()),
                value,
                MonopolyGameActivity.formatMemberOrAccount(getActivity(), lastTransfer.to())
        );

        textViewCreated.setText(dateFormat.format(created));
        textViewSummary.setText(summary);
    }

}
