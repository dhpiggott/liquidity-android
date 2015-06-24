package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.EditText;

import com.dhpcs.liquidity.R;

public class ChangeGameNameDialogFragment extends DialogFragment {

    public interface Listener {

        void onGameNameEntered(String name);

    }

    private static final String ARG_NAME = "name";

    public static ChangeGameNameDialogFragment newInstance(String name) {
        ChangeGameNameDialogFragment transferToPlayerDialogFragment =
                new ChangeGameNameDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_NAME, name);
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
                    + " must implement ChangeGameNameDialogFragment.Listener");
        }
    }

    // TODO: Improve using http://stackoverflow.com/questions/5107901/better-way-to-format-currency-input-edittext?
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog =  new AlertDialog.Builder(getActivity())
                .setTitle(R.string.enter_game_name)
                        // TODO: suffixes, destination
                .setView(R.layout.fragment_change_game_name_dialog)
                .setPositiveButton(
                        R.string.ok,
                        new DialogInterface.OnClickListener() {
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
                .setNegativeButton(
                        R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                getDialog().cancel();
                            }
                        }
                )
                .create();
        // TODO: Make assignment of args to fields consistent across all fragments.
//        ((EditText)dialog.findViewById(R.id.edittext_game_name)).setText(getArguments().getString(ARG_NAME));
        return dialog;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

}
