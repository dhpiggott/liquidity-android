package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.EditText;

import com.dhpcs.liquidity.R;

import java.math.BigDecimal;

public class NewMonopolyGameDialogFragment extends DialogFragment {

    public interface Listener {

        void onInitialCapitalEntered(BigDecimal initialCapital);

    }

    public static NewMonopolyGameDialogFragment newInstance() {
        return new NewMonopolyGameDialogFragment();
    }

    private Listener listener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (Listener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement NewMonopolyGameDialogFragment.Listener");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.enter_starting_capital)
                        // TODO: Spinner, suffixes, memory
                .setView(R.layout.fragment_new_monopoly_game_dialog)
                .setPositiveButton(
                        R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                if (listener != null) {
                                    listener.onInitialCapitalEntered(
                                            new BigDecimal(
                                                    ((EditText) getDialog().findViewById(
                                                            R.id.edittext_starting_capital
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
