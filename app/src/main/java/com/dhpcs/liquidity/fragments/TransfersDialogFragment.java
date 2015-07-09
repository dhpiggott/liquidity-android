package com.dhpcs.liquidity.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.dhpcs.liquidity.MonopolyGame;
import com.dhpcs.liquidity.MonopolyGame.Player;
import com.dhpcs.liquidity.MonopolyGame.TransferWithCurrency;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import scala.collection.Iterator;

public class TransfersDialogFragment extends DialogFragment {

    private static class TransfersAdapter extends ArrayAdapter<TransferWithCurrency> {

        private final DateFormat dateFormat = DateFormat.getDateTimeInstance();
        private final Player player;

        public TransfersAdapter(Context context, Player player) {
            super(context,
                    // TODO
                    android.R.layout.simple_list_item_2,
                    android.R.id.text1);
            this.player = player;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            TextView textViewSummary = (TextView) view.findViewById(android.R.id.text1);
            textViewSummary.setGravity(Gravity.START);
            TextView textViewCreated = (TextView) view.findViewById(android.R.id.text2);
            textViewCreated.setGravity(Gravity.END);

            TransferWithCurrency transfer = getItem(position);

            long created = transfer.transaction().created();
            boolean isFromPlayer = player != null && transfer.from().right().get().memberId()
                    .equals(player.memberId());
            String value = MonopolyGameActivity.formatCurrency(
                    transfer.transaction().value(),
                    transfer.currency()
            );
            boolean isToPlayer = player != null && transfer.to().right().get().memberId()
                    .equals(player.memberId());
            String summary;
            if (isFromPlayer && !isToPlayer) {
                summary = getContext().getString(
                        R.string.transfer_summary_received_from_format_string,
                        value,
                        MonopolyGameActivity.formatMemberOrAccount(getContext(), transfer.to())
                );
            } else if (!isFromPlayer && isToPlayer) {
                summary = getContext().getString(
                        R.string.transfer_summary_sent_to_format_string,
                        value,
                        MonopolyGameActivity.formatMemberOrAccount(getContext(), transfer.from())
                );
            } else {
                summary = getContext().getString(
                        R.string.transfer_summary_format_string,
                        MonopolyGameActivity.formatMemberOrAccount(getContext(), transfer.from()),
                        value,
                        MonopolyGameActivity.formatMemberOrAccount(getContext(), transfer.to())
                );
            }

            textViewCreated.setText(dateFormat.format(created));
            textViewSummary.setText(summary);

            return view;
        }

    }

    private static final String ARG_PLAYER = "player";
    private static final String ARG_TRANSFERS = "transfers";

    public static TransfersDialogFragment newInstance(Player player,
                                                      ArrayList<TransferWithCurrency> transfers) {
        TransfersDialogFragment transfersDialogFragment = new TransfersDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_PLAYER, player);
        args.putSerializable(ARG_TRANSFERS, transfers);
        transfersDialogFragment.setArguments(args);
        return transfersDialogFragment;
    }

    private ArrayAdapter<MonopolyGame.TransferWithCurrency> listAdapter;

    @Override
    @SuppressWarnings("unchecked")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Player player = (Player) getArguments().getSerializable(ARG_PLAYER);
        listAdapter = new TransfersAdapter(getActivity(), player);
        List<TransferWithCurrency> transfers =
                (ArrayList<TransferWithCurrency>) getArguments().getSerializable(ARG_TRANSFERS);
        for (TransferWithCurrency transfer : transfers) {
            if (player == null || (transfer.from().isRight()
                    && player.memberId().equals(transfer.from().right().get().memberId()))
                    || (transfer.to().isRight()
                    && player.memberId().equals(transfer.to().right().get().memberId()))) {
                listAdapter.add(transfer);
            }
        }
        listAdapter.sort(MonopolyGameActivity.transferComparator);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Player player = (Player) getArguments().getSerializable(ARG_PLAYER);
        return new AlertDialog.Builder(getActivity())
                .setTitle(
                        player == null ? getString(R.string.all_transfers)
                                : getString(
                                R.string.player_transfers_format_string,
                                ((Player) getArguments().getSerializable(ARG_PLAYER))
                                        .member().name()
                        )
                )
                .setAdapter(listAdapter, null)
                .setPositiveButton(
                        R.string.done,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {
                                getDialog().dismiss();
                            }

                        }
                )
                .create();
    }

    public void onTransfersChanged(scala.collection.Iterable<TransferWithCurrency> transfers) {
        Player player = (Player) getArguments().getSerializable(ARG_PLAYER);
        listAdapter.setNotifyOnChange(false);
        listAdapter.clear();
        Iterator<TransferWithCurrency> iterator = transfers.iterator();
        while (iterator.hasNext()) {
            TransferWithCurrency transfer = iterator.next();
            if (player == null || (transfer.from().isRight()
                    && player.memberId().equals(transfer.from().right().get().memberId()))
                    || (transfer.to().isRight()
                    && player.memberId().equals(transfer.to().right().get().memberId()))) {
                listAdapter.add(transfer);
            }
        }
        listAdapter.sort(MonopolyGameActivity.transferComparator);
        listAdapter.notifyDataSetChanged();
    }

}
