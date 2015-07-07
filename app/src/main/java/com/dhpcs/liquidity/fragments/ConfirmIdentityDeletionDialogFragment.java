package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import com.dhpcs.liquidity.MonopolyGame.Identity;
import com.dhpcs.liquidity.R;

public class ConfirmIdentityDeletionDialogFragment extends DialogFragment {

    public interface Listener {

        void onIdentityDeleteConfirmed(Identity identity);

    }

    private static final String ARG_IDENTITY = "identity";

    public static ConfirmIdentityDeletionDialogFragment newInstance(Identity identity) {
        ConfirmIdentityDeletionDialogFragment confirmIdentityDeletionDialogFragment =
                new ConfirmIdentityDeletionDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_IDENTITY, identity);
        confirmIdentityDeletionDialogFragment.setArguments(args);
        return confirmIdentityDeletionDialogFragment;
    }

    private Listener listener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (Listener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement ConfirmIdentityDeletionDialogFragment.Listener");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.confirm_delete)
                .setMessage(
                        getString(
                                R.string.confirm_delete_format_string,
                                ((Identity) getArguments().getSerializable(ARG_IDENTITY))
                                        .member().name()
                        )
                )
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
                                    listener.onIdentityDeleteConfirmed(
                                            (Identity) getArguments().getSerializable(ARG_IDENTITY)
                                    );
                                }
                                getDialog().dismiss();
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
