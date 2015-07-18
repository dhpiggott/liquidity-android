package com.dhpcs.liquidity.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.dhpcs.liquidity.MonopolyGame.Identity;
import com.dhpcs.liquidity.MonopolyGame.IdentityWithBalance;
import com.dhpcs.liquidity.MonopolyGame.Player;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;
import com.dhpcs.liquidity.models.MemberId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import scala.collection.JavaConversions;

public class TransferToPlayerDialogFragment extends DialogFragment {

    public interface Listener {

        void onTransferValueEntered(Identity from,
                                    Player to,
                                    BigDecimal transferValue);

    }

    private static class IdentitiesAdapter extends ArrayAdapter<Identity> {

        public IdentitiesAdapter(Context context, List<Identity> identities) {
            super(context, android.R.layout.simple_spinner_item, identities);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView textView = (TextView) super.getDropDownView(position, convertView, parent);
            textView.setText(getItem(position).member().name());
            return textView;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView textView = (TextView) super.getView(position, convertView, parent);
            textView.setText(getItem(position).member().name());
            return textView;
        }

    }

    private static final String ARG_IDENTITIES = "identities";
    private static final String ARG_FROM = "from";
    private static final String ARG_TO = "to";

    public static TransferToPlayerDialogFragment newInstance(
            scala.collection.immutable.Map<MemberId, IdentityWithBalance> identities,
            Identity from,
            Player to) {
        TransferToPlayerDialogFragment transferToPlayerDialogFragment =
                new TransferToPlayerDialogFragment();
        Bundle args = new Bundle();
        // TODO
//        args.putSerializable(
//                ARG_IDENTITIES,
//                new ArrayList<>(
//                        JavaConversions.bufferAsJavaList(
//                                identities.$minus(to.memberId()).values().toBuffer()
//                        )
//                )
//        );
        ArrayList<Identity> identitiesMinusTo = new ArrayList<>(
                JavaConversions.bufferAsJavaList(
                        identities.values().<Identity>toBuffer()
                )
        );
        Iterator<Identity> iterator = identitiesMinusTo.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().memberId().equals(to.memberId())) {
                iterator.remove();
            }
        }
        args.putSerializable(ARG_IDENTITIES, identitiesMinusTo);
        args.putSerializable(ARG_FROM, from);
        args.putSerializable(ARG_TO, to);
        transferToPlayerDialogFragment.setArguments(args);
        return transferToPlayerDialogFragment;
    }

    private Listener listener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (Listener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement TransferToPlayerDialogFragment.Listener");
        }
    }

    // TODO:
    // Use http://stackoverflow.com/questions/5107901/better-way-to-format-currency-input-edittext
    // or https://github.com/BlacKCaT27/CurrencyEditText
    // Currently this crashes when parsing '0' or '00'...
    // Add buttons with quantity suffixes
    @Override
    @SuppressWarnings("unchecked")
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        @SuppressLint("InflateParams") final View view = getActivity().getLayoutInflater().inflate(
                R.layout.fragment_transfer_to_player_dialog,
                null
        );
        final Identity from = (Identity) getArguments().getSerializable(ARG_FROM);
        final List<Identity> identities = (List<Identity>)
                getArguments().getSerializable(ARG_IDENTITIES);
        final ArrayAdapter<Identity> spinnerAdapter = new IdentitiesAdapter(
                getActivity(),
                identities
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAdapter.sort(MonopolyGameActivity.playerComparator);
        final Spinner spinnerIdentity = (Spinner) view.findViewById(R.id.spinner_identities);
        TextView textViewIdentity = (TextView) view.findViewById(R.id.textview_identity);
        if (identities.size() == 1) {
            textViewIdentity.setText(from.member().name());
        } else {
            textViewIdentity.setVisibility(View.GONE);
            spinnerIdentity.setVisibility(View.VISIBLE);
            spinnerIdentity.setAdapter(spinnerAdapter);
            spinnerIdentity.setSelection(spinnerAdapter.getPosition(from));
        }
        final EditText editTextTransferValue = (EditText) view.findViewById(
                R.id.edittext_transfer_value
        );
        Dialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(
                        getString(
                                R.string.transfer_to_format_string,
                                ((Player) getArguments().getSerializable(ARG_TO)).member().name()
                        )
                )
                .setView(view)
                .setNegativeButton(
                        R.string.cancel,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                getDialog().cancel();
                            }

                        }
                )
                .setPositiveButton(
                        R.string.ok,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                if (listener != null) {
                                    listener.onTransferValueEntered(
                                            identities.size() == 1 ?
                                                    from :
                                                    spinnerAdapter.getItem(
                                                            spinnerIdentity.
                                                                    getSelectedItemPosition()
                                                    ),
                                            (Player) getArguments()
                                                    .getSerializable(ARG_TO),
                                            new BigDecimal(
                                                    editTextTransferValue.getText().toString()
                                            )
                                    );
                                }
                                getDialog().dismiss();
                            }

                        }
                )
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        return dialog;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

}
