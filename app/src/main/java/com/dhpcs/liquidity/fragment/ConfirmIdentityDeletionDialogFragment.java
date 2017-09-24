package com.dhpcs.liquidity.fragment;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activity.BoardGameActivity;
import com.dhpcs.liquidity.boardgame.BoardGame.Identity;

public class ConfirmIdentityDeletionDialogFragment extends AppCompatDialogFragment {

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
    public void onAttach(Context context) {
        super.onAttach(context);
        listener = (Listener) context;
    }

    @NonNull
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
                .setMessage(R.string.delete_identity_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.delete,
                        (dialog, whichButton) -> {
                            if (listener != null) {
                                listener.onIdentityDeleteConfirmed(
                                        (Identity) getArguments().getSerializable(ARG_IDENTITY)
                                );
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
