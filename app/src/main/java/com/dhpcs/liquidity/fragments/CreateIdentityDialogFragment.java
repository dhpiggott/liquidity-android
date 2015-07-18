package com.dhpcs.liquidity.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.dhpcs.liquidity.R;

public class CreateIdentityDialogFragment extends DialogFragment {

    public interface Listener {

        void onIdentityNameEntered(String name);

    }

    public static CreateIdentityDialogFragment newInstance() {
        return new CreateIdentityDialogFragment();
    }

    private Listener listener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (Listener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement CreateIdentityDialogFragment.Listener");
        }
    }

    // TODO: Presets - e.g. Free Parking
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        @SuppressLint("InflateParams") final View view = getActivity().getLayoutInflater().inflate(
                R.layout.fragment_create_identity_dialog,
                null
        );
        final EditText editTextIdentityName = (EditText) view.findViewById(
                R.id.edittext_identity_name
        );
        Dialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.enter_identity_name)
                .setView(view)
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
                                            editTextIdentityName.getText().toString()
                                    );
                                }
                                getDialog().dismiss();
                            }

                        }
                )
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        return dialog;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

}
