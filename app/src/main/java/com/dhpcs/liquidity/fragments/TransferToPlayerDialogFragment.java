package com.dhpcs.liquidity.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.dhpcs.liquidity.MonopolyGame.Identity;
import com.dhpcs.liquidity.MonopolyGame.Player;
import com.dhpcs.liquidity.R;

import java.math.BigDecimal;

public class TransferToPlayerDialogFragment extends DialogFragment {

    public interface Listener {

        void onTransferValueEntered(Identity actingAs,
                                    Player from,
                                    Player to,
                                    BigDecimal transferValue);

    }

    private static final String ARG_ACTING_AS = "acting_as";
    private static final String ARG_FROM = "from";
    private static final String ARG_TO = "to";

    public static TransferToPlayerDialogFragment newInstance(Identity actingAs,
                                                             Player from,
                                                             Player to) {
        TransferToPlayerDialogFragment transferToPlayerDialogFragment =
                new TransferToPlayerDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_ACTING_AS, actingAs);
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
    // Improve:
    // Using http://stackoverflow.com/questions/5107901/better-way-to-format-currency-input-edittext
    // or https://github.com/BlacKCaT27/CurrencyEditText
    // Currently this crashes when parsing '0' or '00'...
    // Add buttons with quantity suffixes
    // Show source (with spinner?)
    // Show destination
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        @SuppressLint("InflateParams") final View view = getActivity().getLayoutInflater().inflate(
                R.layout.fragment_transfer_to_player_dialog,
                null
        );
        final EditText editTextTransferValue = (EditText) view.findViewById(
                R.id.edittext_transfer_value
        );
        Dialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.enter_transfer_amount)
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
                                            (Identity) getArguments()
                                                    .getSerializable(ARG_ACTING_AS),
                                            (Player) getArguments()
                                                    .getSerializable(ARG_FROM),
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
