package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.dhpcs.liquidity.MonopolyGame.Identity;
import com.dhpcs.liquidity.MonopolyGame.IdentityWithBalance;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;
import com.dhpcs.liquidity.models.Identifier;
import com.dhpcs.liquidity.views.Identicon;

import java.util.ArrayList;

import scala.collection.JavaConversions;

public class RestoreIdentityDialogFragment extends DialogFragment {

    public interface Listener {

        void onIdentityRestorationRequested(Identity identity);

    }

    private static class IdentitiesAdapter extends ArrayAdapter<IdentityWithBalance> {

        public IdentitiesAdapter(Context context) {
            super(context,
                    R.layout.linearlayout_identity,
                    R.id.textview_name);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            IdentityWithBalance identity = getItem(position);

            Identicon identiconId = (Identicon) view.findViewById(R.id.identicon_id);
            TextView textViewName = (TextView) view.findViewById(R.id.textview_name);
            TextView textViewBalance = (TextView) view.findViewById(R.id.textview_balance);

            Identifier identifier = identity.memberId();
            String name = identity.member().name();
            String balance = MonopolyGameActivity.formatCurrencyValue(
                    getContext(),
                    identity.balanceWithCurrency()._2(),
                    identity.balanceWithCurrency()._1()
            );

            identiconId.show(identifier);
            textViewName.setText(name);
            textViewBalance.setText(balance);

            return view;
        }

    }

    private static final String ARG_IDENTITIES = "identities";

    public static RestoreIdentityDialogFragment newInstance(
            scala.collection.Iterable<IdentityWithBalance> identities) {
        RestoreIdentityDialogFragment restoreIdentityDialogFragment =
                new RestoreIdentityDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(
                ARG_IDENTITIES,
                new ArrayList<>(
                        JavaConversions.bufferAsJavaList(
                                identities.toBuffer()
                        )
                )
        );
        restoreIdentityDialogFragment.setArguments(args);
        return restoreIdentityDialogFragment;
    }

    private Listener listener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        listener = (Listener) activity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final ArrayAdapter<IdentityWithBalance> arrayAdapter = new IdentitiesAdapter(getActivity());
        arrayAdapter.addAll(
                (ArrayList<IdentityWithBalance>) getArguments().getSerializable(ARG_IDENTITIES)
        );
        arrayAdapter.sort(MonopolyGameActivity.identityComparator);
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.choose_identity_to_restore)
                .setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (listener != null) {
                            listener.onIdentityRestorationRequested(arrayAdapter.getItem(which));
                        }
                    }

                })
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

}
