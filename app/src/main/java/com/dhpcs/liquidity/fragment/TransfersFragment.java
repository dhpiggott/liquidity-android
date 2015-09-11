package com.dhpcs.liquidity.fragment;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.util.SortedList;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.util.SortedListAdapterCallback;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dhpcs.liquidity.BoardGame.Player;
import com.dhpcs.liquidity.BoardGame.TransferWithCurrency;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activity.BoardGameActivity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import scala.collection.Iterator;

public class TransfersFragment extends Fragment {

    private class TransfersAdapter
            extends RecyclerView.Adapter<TransfersAdapter.TransferViewHolder> {

        public class TransferViewHolder extends RecyclerView.ViewHolder {

            private final TextView textViewSummary;
            private final TextView textViewCreatedTime;
            private final TextView textViewCreatedDate;

            public TransferViewHolder(View itemView) {
                super(itemView);
                textViewSummary = (TextView) itemView.findViewById(R.id.textview_summary);
                textViewCreatedTime = (TextView) itemView.findViewById(R.id.textview_created_time);
                textViewCreatedDate = (TextView) itemView.findViewById(R.id.textview_created_date);
            }

            public void bindTransfer(TransferWithCurrency transfer) {

                boolean isFromPlayer = player != null && transfer.from().right().get().member().id()
                        .equals(player.member().id());
                String value = BoardGameActivity.formatCurrencyValue(
                        context,
                        transfer.currency(),
                        transfer.transaction().value()
                );
                boolean isToPlayer = player != null && transfer.to().right().get().member().id()
                        .equals(player.member().id());
                String summary;
                if (isFromPlayer && !isToPlayer) {
                    summary = context.getString(
                            R.string.transfer_summary_sent_to_format_string,
                            value,
                            BoardGameActivity.formatMemberOrAccount(context, transfer.to())
                    );
                } else if (!isFromPlayer && isToPlayer) {
                    summary = context.getString(
                            R.string.transfer_summary_received_from_format_string,
                            value,
                            BoardGameActivity.formatMemberOrAccount(context, transfer.from())
                    );
                } else {
                    summary = context.getString(
                            R.string.transfer_summary_format_string,
                            BoardGameActivity.formatMemberOrAccount(context, transfer.from()),
                            value,
                            BoardGameActivity.formatMemberOrAccount(context, transfer.to())
                    );
                }
                String createdTime = timeFormat.format(transfer.transaction().created());
                String createdDate = dateFormat.format(transfer.transaction().created());

                textViewSummary.setText(summary);
                textViewCreatedTime.setText(createdTime);
                textViewCreatedDate.setText(createdDate);
            }

        }

        private final Context context;
        private final Player player;
        private final DateFormat timeFormat = DateFormat.getTimeInstance();
        private final DateFormat dateFormat = DateFormat.getDateInstance();
        private final SortedList<TransferWithCurrency> transfers = new SortedList<>(
                TransferWithCurrency.class,
                new SortedListAdapterCallback<TransferWithCurrency>(this) {

                    @Override
                    public int compare(TransferWithCurrency o1,
                                       TransferWithCurrency o2) {
                        long lhsId = o1.transaction().id().id();
                        long rhsId = o2.transaction().id().id();
                        return -1 *
                                (lhsId < rhsId ? -1 : (lhsId == rhsId ? 0 : 1));
                    }

                    @Override
                    public boolean areContentsTheSame(TransferWithCurrency oldItem,
                                                      TransferWithCurrency newItem) {
                        return oldItem.equals(newItem);
                    }

                    @Override
                    public boolean areItemsTheSame(TransferWithCurrency item1,
                                                   TransferWithCurrency item2) {
                        return item1.transaction().id().equals(item2.transaction().id());
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
                    .inflate(R.layout.linearlayout_transfer, parent, false);
            return new TransferViewHolder(view);
        }

        @Override
        public void onBindViewHolder(TransferViewHolder holder, int position) {
            TransferWithCurrency transfer = transfers.get(position);
            holder.bindTransfer(transfer);
        }

        public int addOrReplace(TransferWithCurrency transfer) {
            return transfers.add(transfer);
        }

        public void beginBatchedUpdates() {
            transfers.beginBatchedUpdates();
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
    private RecyclerView recyclerViewTransfers;

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
                replaceOrAddTransfer(player, transfer);
            }
            transfersAdapter.endBatchedUpdates();
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_transfers, container, false);

        textViewEmpty = (TextView) view.findViewById(R.id.textview_empty);
        recyclerViewTransfers = (RecyclerView) view.findViewById(R.id.recyclerview_transfers);

        recyclerViewTransfers.addItemDecoration(new RecyclerView.ItemDecoration() {

            private final Drawable divider;

            {
                TypedArray a = getActivity().obtainStyledAttributes(
                        new int[]{android.R.attr.listDivider}
                );
                divider = a.getDrawable(0);
                a.recycle();
            }

            @Override
            public void getItemOffsets(Rect outRect,
                                       View view,
                                       RecyclerView parent,
                                       RecyclerView.State state) {
                outRect.set(0, 0, 0, divider.getIntrinsicHeight());
            }

            @Override
            public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
                int left = parent.getPaddingLeft();
                int right = parent.getWidth() - parent.getPaddingRight();
                int childCount = parent.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = parent.getChildAt(i);
                    RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                            child.getLayoutParams();
                    int top = child.getBottom() + params.bottomMargin;
                    int bottom = top + divider.getIntrinsicHeight();
                    divider.setBounds(left, top, right, bottom);
                    divider.draw(c);
                }
            }

        });
        recyclerViewTransfers.setHasFixedSize(true);
        recyclerViewTransfers.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerViewTransfers.setAdapter(transfersAdapter);

        if (transfersAdapter.getItemCount() != 0) {
            textViewEmpty.setVisibility(View.GONE);
            recyclerViewTransfers.setVisibility(View.VISIBLE);
        }

        return view;
    }

    public void onTransferAdded(TransferWithCurrency addedTransfer) {
        replaceOrAddTransfer(
                (Player) getArguments().getSerializable(ARG_PLAYER),
                addedTransfer
        );
        textViewEmpty.setVisibility(View.GONE);
        recyclerViewTransfers.setVisibility(View.VISIBLE);
    }

    public void onTransfersChanged(
            scala.collection.Iterable<TransferWithCurrency> changedTransfers) {
        replaceOrAddTransfers(changedTransfers);
    }

    public void onTransfersInitialized(
            scala.collection.Iterable<TransferWithCurrency> transfers) {
        replaceOrAddTransfers(transfers);
        if (transfers.nonEmpty()) {
            textViewEmpty.setVisibility(View.GONE);
            recyclerViewTransfers.setVisibility(View.VISIBLE);
        }
    }

    private void replaceOrAddTransfer(Player player, TransferWithCurrency transfer) {
        if (player == null || (transfer.from().isRight()
                && player.member().id().equals(transfer.from().right().get().member().id()))
                || (transfer.to().isRight()
                && player.member().id().equals(transfer.to().right().get().member().id()))) {
            transfersAdapter.addOrReplace(transfer);
        }
    }

    private void replaceOrAddTransfers(scala.collection.Iterable<TransferWithCurrency> transfers) {
        Player player = (Player) getArguments().getSerializable(ARG_PLAYER);
        Iterator<TransferWithCurrency> iterator = transfers.iterator();
        while (iterator.hasNext()) {
            replaceOrAddTransfer(player, iterator.next());
        }
    }

}
