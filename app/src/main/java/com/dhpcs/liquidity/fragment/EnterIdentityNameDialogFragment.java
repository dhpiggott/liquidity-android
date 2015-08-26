package com.dhpcs.liquidity.fragment;

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

import com.dhpcs.liquidity.BoardGame.Identity;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activity.BoardGameActivity;

public class EnterIdentityNameDialogFragment extends DialogFragment {

    public interface Listener {

        void onIdentityNameEntered(Identity identity, String name);

    }

    public static final String TAG = "enter_identity_name_dialog_fragment";

    private static final String ARG_IDENTITY = "identity";

    public static EnterIdentityNameDialogFragment newInstance(Identity identity) {
        EnterIdentityNameDialogFragment enterIdentityNameDialogFragment =
                new EnterIdentityNameDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_IDENTITY, identity);
        enterIdentityNameDialogFragment.setArguments(args);
        return enterIdentityNameDialogFragment;
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
                R.layout.fragment_enter_identity_name_dialog,
                null
        );

        final Identity identity = (Identity) getArguments().getSerializable(ARG_IDENTITY);

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
                                            identity,
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
                            BoardGameActivity.isIdentityNameValid(getActivity(), s)
                    );
                }
            }

        });

        assert identity != null;
        editTextIdentityName.setText(
                BoardGameActivity.formatNullable(getActivity(), identity.member().name())
        );

        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(DialogInterface dialog) {
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(
                        BoardGameActivity.isIdentityNameValid(
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
