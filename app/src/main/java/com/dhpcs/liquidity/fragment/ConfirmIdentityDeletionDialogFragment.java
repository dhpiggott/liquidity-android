package com.dhpcs.liquidity.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import com.dhpcs.liquidity.BoardGame.Identity;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activity.BoardGameActivity;

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
        Identity identity = (Identity) getArguments().getSerializable(ARG_IDENTITY);
        assert identity != null;
        return new AlertDialog.Builder(getActivity())
                .setTitle(
                        getString(
                                R.string.delete_identity_title_format_string,
                                BoardGameActivity.formatNullable(
                                        getActivity(),
                                        identity.member().name()
                                )
                        )
                )
                .setMessage(
                        getString(
                                R.string.delete_identity_message_format_string,
                                BoardGameActivity.formatNullable(
                                        getActivity(),
                                        identity.member().name()
                                )
                        )
                )
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.delete,
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
