package com.dhpcs.liquidity.fragments;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.dhpcs.liquidity.MonopolyGame.Player;
import com.dhpcs.liquidity.MonopolyGame.TransferWithCurrency;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import scala.collection.Iterator;

// TODO: Extend ListFragment? Or switch to RecyclerView?
public class TransfersFragment extends Fragment {

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
            String value = MonopolyGameActivity.formatCurrencyValue(
                    getContext(),
                    transfer.currency(),
                    transfer.transaction().value()
            );
            boolean isToPlayer = player != null && transfer.to().right().get().memberId()
                    .equals(player.memberId());
            String summary;
            if (isFromPlayer && !isToPlayer) {
                summary = getContext().getString(
                        R.string.transfer_summary_sent_to_format_string,
                        value,
                        MonopolyGameActivity.formatMemberOrAccount(getContext(), transfer.to())
                );
            } else if (!isFromPlayer && isToPlayer) {
                summary = getContext().getString(
                        R.string.transfer_summary_received_from_format_string,
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

    public static TransfersFragment newInstance(Player player,
                                                ArrayList<TransferWithCurrency> transfers) {
        TransfersFragment transfersFragment = new TransfersFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_PLAYER, player);
        args.putSerializable(ARG_TRANSFERS, transfers);
        transfersFragment.setArguments(args);
        return transfersFragment;
    }

    private ArrayAdapter<TransferWithCurrency> listAdapter;

    @Override
    @SuppressWarnings("unchecked")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Player player = (Player) getArguments().getSerializable(ARG_PLAYER);
        listAdapter = new TransfersAdapter(getActivity(), player);
        List<TransferWithCurrency> transfers =
                (ArrayList<TransferWithCurrency>) getArguments().getSerializable(ARG_TRANSFERS);
        if (transfers != null) {
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
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_transfers_list, container, false);

        AbsListView absListViewPlayers = (AbsListView) view.findViewById(android.R.id.list);
        absListViewPlayers.setAdapter(listAdapter);
        absListViewPlayers.setEmptyView(view.findViewById(android.R.id.empty));

        return view;
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
