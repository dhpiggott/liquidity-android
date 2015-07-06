package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.EditText;

import com.dhpcs.liquidity.R;

public class EnterGameNameDialogFragment extends DialogFragment {

    public interface Listener {

        void onGameNameEntered(String name);

    }

    private static final String ARG_NAME = "name";

    public static EnterGameNameDialogFragment newInstance(String name) {
        EnterGameNameDialogFragment transferToPlayerDialogFragment =
                new EnterGameNameDialogFragment();
        Bundle args = new Bundle();
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

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.enter_game_name)
                .setView(R.layout.fragment_enter_game_name_dialog)
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
                                    listener.onGameNameEntered(
                                            ((EditText) getDialog().findViewById(
                                                    R.id.edittext_game_name
                                            )).getText().toString()
                                    );
                                }
                                getDialog().dismiss();
                            }

                        }
                )
                .create();
        // TODO: Make assignment of args to fields consistent across all fragments.
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
