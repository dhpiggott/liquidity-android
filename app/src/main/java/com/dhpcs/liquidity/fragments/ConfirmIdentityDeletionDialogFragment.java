package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.MemberId;

public class ConfirmIdentityDeletionDialogFragment extends DialogFragment {

    public interface Listener {

        void onIdentityDeleteConfirmed(MemberId identityId);

    }

    private static final String ARG_IDENTITY_ID = "identity_id";
    private static final String ARG_NAME = "name";

    public static ConfirmIdentityDeletionDialogFragment newInstance(MemberId identityId,
                                                                    String name) {
        ConfirmIdentityDeletionDialogFragment confirmIdentityDeletionDialogFragment =
                new ConfirmIdentityDeletionDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_IDENTITY_ID, identityId);
        args.putString(ARG_NAME, name);
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
                                getArguments().getString(ARG_NAME)
                        )
                )
                .setNegativeButton(
                        R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                getDialog().cancel();
                            }
                        }
                )
                .setPositiveButton(
                        R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                if (listener != null) {
                                    listener.onIdentityDeleteConfirmed(
                                            (MemberId) getArguments()
                                                    .getSerializable(ARG_IDENTITY_ID)
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
