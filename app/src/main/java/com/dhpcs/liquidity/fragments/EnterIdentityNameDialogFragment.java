package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.EditText;

import com.dhpcs.liquidity.MonopolyGame.Identity;
import com.dhpcs.liquidity.R;

public class EnterIdentityNameDialogFragment extends DialogFragment {

    public interface Listener {

        void onIdentityNameEntered(Identity identity, String name);

    }

    private static final String ARG_IDENTITY = "identity";

    public static EnterIdentityNameDialogFragment newInstance(Identity identity) {
        EnterIdentityNameDialogFragment transferToPlayerDialogFragment =
                new EnterIdentityNameDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_IDENTITY, identity);
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
                    + " must implement EnterIdentityNameDialogFragment.Listener");
        }
    }

    // TODO: Remember and suggest previous names if arg is null
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.enter_identity_name)
                .setView(R.layout.fragment_enter_identity_name_dialog)
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
                                    listener.onIdentityNameEntered(
                                            (Identity) getArguments().getSerializable(ARG_IDENTITY),
                                            ((EditText) getDialog().findViewById(
                                                    R.id.edittext_identity_name
                                            )).getText().toString()
                                    );
                                }
                                getDialog().dismiss();
                            }

                        }
                )
                .create();
// TODO
//        ((EditText) dialog.findViewById(R.id.edittext_game_name)).setText(
//                ((Identity) getArguments().getSerializable(ARG_IDENTITY))
//                        .member().name()
//        );
        return dialog;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

}
