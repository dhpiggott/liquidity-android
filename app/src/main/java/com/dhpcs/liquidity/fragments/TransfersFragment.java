package com.dhpcs.liquidity.fragments;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.util.SortedList;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.util.SortedListAdapterCallback;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dhpcs.liquidity.MonopolyGame.Player;
import com.dhpcs.liquidity.MonopolyGame.TransferWithCurrency;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import scala.collection.Iterator;

public class TransfersFragment extends Fragment {

    private class TransfersAdapter
            extends RecyclerView.Adapter<TransfersAdapter.TransferViewHolder> {

        public class TransferViewHolder extends RecyclerView.ViewHolder {

            private final TextView textViewSummary;
            private final TextView textViewCreated;

            public TransferViewHolder(View itemView) {
                super(itemView);
                textViewSummary = (TextView) itemView.findViewById(android.R.id.text1);
                textViewSummary.setGravity(Gravity.START);
                textViewCreated = (TextView) itemView.findViewById(android.R.id.text2);
                textViewCreated.setGravity(Gravity.END);
            }

            public void bindTransfer(TransferWithCurrency transfer) {

                long created = transfer.transaction().created();
                boolean isFromPlayer = player != null && transfer.from().right().get().memberId()
                        .equals(player.memberId());
                String value = MonopolyGameActivity.formatCurrencyValue(
                        context,
                        transfer.currency(),
                        transfer.transaction().value()
                );
                boolean isToPlayer = player != null && transfer.to().right().get().memberId()
                        .equals(player.memberId());
                String summary;
                if (isFromPlayer && !isToPlayer) {
                    summary = context.getString(
                            R.string.transfer_summary_sent_to_format_string,
                            value,
                            MonopolyGameActivity.formatMemberOrAccount(context, transfer.to())
                    );
                } else if (!isFromPlayer && isToPlayer) {
                    summary = context.getString(
                            R.string.transfer_summary_received_from_format_string,
                            value,
                            MonopolyGameActivity.formatMemberOrAccount(context, transfer.from())
                    );
                } else {
                    summary = context.getString(
                            R.string.transfer_summary_format_string,
                            MonopolyGameActivity.formatMemberOrAccount(context, transfer.from()),
                            value,
                            MonopolyGameActivity.formatMemberOrAccount(context, transfer.to())
                    );
                }

                textViewCreated.setText(dateFormat.format(created));
                textViewSummary.setText(summary);
            }

        }

        private final Context context;
        private final Player player;
        private final DateFormat dateFormat = DateFormat.getDateTimeInstance();
        private final SortedList<TransferWithCurrency> transfers = new SortedList<>(
                TransferWithCurrency.class,
                new SortedListAdapterCallback<TransferWithCurrency>(this) {

                    @Override
                    public int compare(TransferWithCurrency o1,
                                       TransferWithCurrency o2) {
                        long lhsCreated = o1.transaction().created();
                        long rhsCreated = o2.transaction().created();
                        return -1 * (lhsCreated < rhsCreated ? -1 : (lhsCreated == rhsCreated ? 0 : 1));
                    }

                    @Override
                    public boolean areContentsTheSame(TransferWithCurrency oldItem,
                                                      TransferWithCurrency newItem) {
                        return oldItem.equals(newItem);
                    }

                    @Override
                    public boolean areItemsTheSame(TransferWithCurrency item1,
                                                   TransferWithCurrency item2) {
                        return item1.transactionId().equals(item2.transactionId());
                    }

                }
        );

        public TransfersAdapter(Context context, Player player) {
            this.context = context;
            this.player = player;
        }


        @Override
        public int getItemCount() {
            return transfers.size();
        }

        @Override
        public TransferViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater
                    .from(parent.getContext())
                            // TODO
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new TransferViewHolder(view);
        }

        @Override
        public void onBindViewHolder(TransferViewHolder holder, int position) {
            TransferWithCurrency transfer = transfers.get(position);
            holder.bindTransfer(transfer);
        }

        public int add(TransferWithCurrency transfer) {
            return transfers.add(transfer);
        }

        public void beginBatchedUpdates() {
            transfers.beginBatchedUpdates();
        }

        public void clear() {
            transfers.clear();
        }

        public void endBatchedUpdates() {
            transfers.endBatchedUpdates();
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

    private TransfersAdapter transfersAdapter;

    private TextView textViewEmpty;

    @Override
    @SuppressWarnings("unchecked")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Player player = (Player) getArguments().getSerializable(ARG_PLAYER);
        List<TransferWithCurrency> transfers =
                (ArrayList<TransferWithCurrency>) getArguments().getSerializable(ARG_TRANSFERS);

        transfersAdapter = new TransfersAdapter(getActivity(), player);

        if (transfers != null) {
            transfersAdapter.beginBatchedUpdates();
            for (TransferWithCurrency transfer : transfers) {
                if (player == null || (transfer.from().isRight()
                        && player.memberId().equals(transfer.from().right().get().memberId()))
                        || (transfer.to().isRight()
                        && player.memberId().equals(transfer.to().right().get().memberId()))) {
                    transfersAdapter.add(transfer);
                }
            }
            transfersAdapter.endBatchedUpdates();
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_transfers_list, container, false);

        textViewEmpty = (TextView) view.findViewById(R.id.textview_empty);
        textViewEmpty.setVisibility(
                transfersAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE
        );

        RecyclerView recyclerViewTransfers = (RecyclerView) view.findViewById(R.id.recyclerview);
        recyclerViewTransfers.setHasFixedSize(true);
        recyclerViewTransfers.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerViewTransfers.setAdapter(transfersAdapter);

        return view;
    }

    public void onTransfersChanged(scala.collection.Iterable<TransferWithCurrency> transfers) {
        Player player = (Player) getArguments().getSerializable(ARG_PLAYER);
        transfersAdapter.beginBatchedUpdates();
        transfersAdapter.clear();
        Iterator<TransferWithCurrency> iterator = transfers.iterator();
        while (iterator.hasNext()) {
            TransferWithCurrency transfer = iterator.next();
            if (player == null || (transfer.from().isRight()
                    && player.memberId().equals(transfer.from().right().get().memberId()))
                    || (transfer.to().isRight()
                    && player.memberId().equals(transfer.to().right().get().memberId()))) {
                transfersAdapter.add(transfer);
            }
        }
        textViewEmpty.setVisibility(
                transfersAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE
        );
        transfersAdapter.endBatchedUpdates();
    }

}
