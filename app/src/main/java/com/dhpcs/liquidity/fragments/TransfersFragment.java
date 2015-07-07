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
import com.dhpcs.liquidity.models.Account;
import com.dhpcs.liquidity.models.AccountId;

import java.text.DateFormat;
import java.util.Comparator;

import scala.Tuple2;
import scala.collection.Iterator;
import scala.util.Either;

// TODO: Extend ListFragment? http://developer.android.com/reference/android/app/ListFragment.html
public class TransfersFragment extends Fragment {

    private static class TransfersAdapter extends ArrayAdapter<TransferWithCurrency> {

        private final DateFormat dateFormat = DateFormat.getDateTimeInstance();

        public TransfersAdapter(Context context) {
            super(context,
                    // TODO
                    android.R.layout.simple_list_item_2,
                    android.R.id.text1);
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
            String from = memberNameOrAccountString(transfer.from());
            String to = memberNameOrAccountString(transfer.to());
            String value = MonopolyGameActivity.formatCurrency(
                    transfer.transaction().value(),
                    transfer.currency()
            );

            textViewCreated.setText(dateFormat.format(created));
            textViewSummary.setText(
                    getContext().getString(
                            // TODO: Improve
                            R.string.transfer_summary_format_string,
                            from,
                            value,
                            to
                    )
            );

            return view;
        }

        public String memberNameOrAccountString(Either<Tuple2<AccountId, Account>, Player>
                                                        eitherAccountTupleOrMember) {
            String result;
            if (eitherAccountTupleOrMember.isLeft()) {
                AccountId accountId = eitherAccountTupleOrMember.left().get()._1();
                Account account = eitherAccountTupleOrMember.left().get()._2();
                result = getContext().getString(
                        R.string.non_player_transfer_location_format_string,
                        accountId.id().toString(),
                        account.name()
                );
            } else {
                result = eitherAccountTupleOrMember.right().get().member().name();
            }
            return result;
        }

    }

    private static final Comparator<TransferWithCurrency> transferComparator =
            new Comparator<TransferWithCurrency>() {

                @Override
                public int compare(TransferWithCurrency lhs, TransferWithCurrency rhs) {
                    return -1 *
                            Long.compare(lhs.transaction().created(), rhs.transaction().created());
                }

            };

    private ArrayAdapter<TransferWithCurrency> listAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        listAdapter = new TransfersAdapter(getActivity());
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
        listAdapter.setNotifyOnChange(false);
        listAdapter.clear();
        Iterator<TransferWithCurrency> iterator = transfers.iterator();
        while (iterator.hasNext()) {
            listAdapter.add(iterator.next());
        }
        listAdapter.sort(transferComparator);
        listAdapter.notifyDataSetChanged();
    }

}
