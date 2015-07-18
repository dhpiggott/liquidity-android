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
import java.util.ArrayList;
import java.util.Collections;

import scala.collection.Iterator;

public class LastTransferFragment extends Fragment {

    private final DateFormat dateFormat = DateFormat.getDateTimeInstance();

    private TextView textViewSummary;
    private TextView textViewCreated;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_last_transfer, container, false);

        textViewSummary = (TextView) view.findViewById(R.id.textview_summary);
        textViewCreated = (TextView) view.findViewById(R.id.textview_created);

        return view;
    }

    public void onTransfersChanged(scala.collection.Iterable<TransferWithCurrency> transfers) {
        ArrayList<TransferWithCurrency> sortedTransfers = new ArrayList<>(transfers.size());
        Iterator<TransferWithCurrency> iterator = transfers.iterator();
        while (iterator.hasNext()) {
            sortedTransfers.add(iterator.next());
        }
        Collections.sort(sortedTransfers, MonopolyGameActivity.transferComparator);

        TransferWithCurrency lastTransfer = sortedTransfers.size() == 0 ? null
                : sortedTransfers.get(0);

        if (lastTransfer != null) {
            long created = lastTransfer.transaction().created();
            String value = MonopolyGameActivity.formatCurrency(
                    getActivity(),
                    lastTransfer.transaction().value(),
                    lastTransfer.currency()
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

}
