package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.EditText;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.MemberId;

import java.math.BigDecimal;

public class TransferToPlayerDialogFragment extends DialogFragment {

    public interface Listener {

        void onTransferValueEntered(MemberId fromMemberId,
                                    MemberId toMemberId,
                                    BigDecimal transferValue);

    }

    private static final String ARG_FROM_MEMBER_ID = "from_member_id";
    private static final String ARG_TO_MEMBER_ID = "to_member_id";

    public static TransferToPlayerDialogFragment newInstance(MemberId fromMemberId,
                                                             MemberId toMemberId) {
        TransferToPlayerDialogFragment transferToPlayerDialogFragment =
                new TransferToPlayerDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_FROM_MEMBER_ID, fromMemberId);
        args.putSerializable(ARG_TO_MEMBER_ID, toMemberId);
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
    // Currently this crashes when parsing '0' or '00'...
    // Add buttons with quantity suffixes
    // Show source with spinner
    // Show destination
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.enter_transfer_amount)
                .setView(R.layout.fragment_transfer_to_player_dialog)
                .setPositiveButton(
                        R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                if (listener != null) {
                                    listener.onTransferValueEntered(
                                            (MemberId) getArguments()
                                                    .getSerializable(ARG_FROM_MEMBER_ID),
                                            (MemberId) getArguments()
                                                    .getSerializable(ARG_TO_MEMBER_ID),
                                            new BigDecimal(
                                                    ((EditText) getDialog().findViewById(
                                                            R.id.edittext_transfer_value
                                                    )).getText().toString()
                                            )
                                    );
                                }
                                getDialog().dismiss();
                            }
                        }
                )
                .setNegativeButton(
                        R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                getDialog().cancel();
                            }
                        }
                )
                .create();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

}
