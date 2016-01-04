package com.dhpcs.liquidity.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activity.BoardGameActivity;
import com.dhpcs.liquidity.models.package$;

public class CreateIdentityDialogFragment extends DialogFragment {

    public interface Listener {

        void onIdentityNameEntered(String name);

    }

    public static final String TAG = "create_identity_dialog_fragment";

    public static CreateIdentityDialogFragment newInstance() {
        return new CreateIdentityDialogFragment();
    }

    private Listener listener;

    private Button buttonPositive;

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

        TextInputLayout textInputLayoutGameName = (TextInputLayout)
                view.findViewById(R.id.textinputlayout_game_name);
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
                                            editTextIdentityName.getText().toString()
                                    );
                                }
                            }

                        }
                )
                .create();

        textInputLayoutGameName.setCounterMaxLength(package$.MODULE$.MaxStringLength());
        editTextIdentityName.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (buttonPositive != null) {
                    validateInput(s);
                }
            }

        });

        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(DialogInterface dialog) {
                buttonPositive = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                validateInput(editTextIdentityName.getText());
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

    private void validateInput(CharSequence identityName) {
        buttonPositive.setEnabled(
                ((BoardGameActivity) getActivity()).isIdentityNameValid(
                        identityName
                )
        );
    }

}
