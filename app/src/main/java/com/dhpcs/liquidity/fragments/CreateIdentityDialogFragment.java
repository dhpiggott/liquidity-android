package com.dhpcs.liquidity.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;

public class CreateIdentityDialogFragment extends DialogFragment {

    public interface Listener {

        void onIdentityNameEntered(boolean isInitialPrompt, String name);

    }

    private static final String ARG_IS_INITIAL_PROMPT = "is_initial_prompt";

    public static CreateIdentityDialogFragment newInstance(boolean isInitialPrompt) {
        CreateIdentityDialogFragment createIdentityDialogFragment =
                new CreateIdentityDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_INITIAL_PROMPT, isInitialPrompt);
        createIdentityDialogFragment.setArguments(args);
        return createIdentityDialogFragment;
    }

    private Listener listener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        listener = (Listener) activity;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        @SuppressLint("InflateParams") View view = getActivity().getLayoutInflater().inflate(
                R.layout.fragment_create_identity_dialog,
                null
        );

        final boolean isInitialPrompt = getArguments().getBoolean(ARG_IS_INITIAL_PROMPT);

        final EditText editTextIdentityName = (EditText) view.findViewById(
                R.id.edittext_identity_name
        );

        final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.enter_identity_name)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.ok,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                if (listener != null) {
                                    listener.onIdentityNameEntered(
                                            isInitialPrompt,
                                            editTextIdentityName.getText().toString()
                                    );
                                }
                            }

                        }
                )
                .create();

        editTextIdentityName.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                Button buttonPositive = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (buttonPositive != null) {
                    buttonPositive.setEnabled(
                            MonopolyGameActivity.isIdentityNameValid(getActivity(), s)
                    );
                }
            }

        });

        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(DialogInterface dialog) {
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(
                        MonopolyGameActivity.isIdentityNameValid(
                                getActivity(),
                                editTextIdentityName.getText()
                        )
                );
            }

        });

        alertDialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        );

        return alertDialog;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

}
