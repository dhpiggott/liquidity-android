package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import com.dhpcs.liquidity.MonopolyGame.Identity;
import com.dhpcs.liquidity.R;

public class ConfirmIdentityDeletionDialogFragment extends DialogFragment {

    public interface Listener {

        void onIdentityDeleteConfirmed(Identity identity);

    }

    public static final String TAG = "confirm_identity_deletion_dialog_fragment";

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
        listener = (Listener) activity;
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
                .setNegativeButton(R.string.cancel, null)
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
