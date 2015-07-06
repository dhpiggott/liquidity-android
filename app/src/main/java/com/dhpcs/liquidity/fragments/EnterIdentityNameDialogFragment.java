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

public class EnterIdentityNameDialogFragment extends DialogFragment {

    public interface Listener {

        void onIdentityNameEntered(MemberId identityId, String name);

    }

    private static final String ARG_IDENTITY_ID = "identity_id";
    private static final String ARG_NAME = "name";

    public static EnterIdentityNameDialogFragment newInstance(MemberId identityId, String name) {
        EnterIdentityNameDialogFragment transferToPlayerDialogFragment =
                new EnterIdentityNameDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_IDENTITY_ID, identityId);
        args.putString(ARG_NAME, name);
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
                    + " must implement EnterGameNameDialogFragment.Listener");
        }
    }

    // TODO: Remember and suggest previous names if arg is null
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.enter_identity_name)
                .setView(R.layout.fragment_enter_identity_name_dialog)
                .setPositiveButton(
                        R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                if (listener != null) {
                                    listener.onIdentityNameEntered(
                                            (MemberId) getArguments()
                                                    .getSerializable(ARG_IDENTITY_ID),
                                            ((EditText) getDialog().findViewById(
                                                    R.id.edittext_identity_name
                                            )).getText().toString()
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
        // TODO
//        ((EditText)dialog.findViewById(R.id.edittext_game_name)).setText(getArguments().getString(ARG_NAME));
        return dialog;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

}
