package com.dhpcs.liquidity.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import com.dhpcs.liquidity.R;

public class EnterGameNameDialogFragment extends DialogFragment {

    public interface Listener {

        void onGameNameEntered(String name);

    }

    public static final String TAG = "enter_game_name_dialog_fragment";

    private static final String ARG_NAME = "name";

    public static EnterGameNameDialogFragment newInstance(String name) {
        EnterGameNameDialogFragment enterGameNameDialogFragment = new EnterGameNameDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NAME, name);
        enterGameNameDialogFragment.setArguments(args);
        return enterGameNameDialogFragment;
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
                R.layout.fragment_enter_game_name_dialog,
                null
        );

        final String name = getArguments().getString(ARG_NAME);

        final EditText editTextGameName = (EditText) view.findViewById(R.id.edittext_game_name);

        final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.enter_game_name)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.ok,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                if (listener != null) {
                                    listener.onGameNameEntered(
                                            editTextGameName.getText().toString()
                                    );
                                }
                            }

                        }
                )
                .create();

        editTextGameName.addTextChangedListener(new TextWatcher() {

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
                            !TextUtils.isEmpty(s)
                    );
                }
            }

        });

        editTextGameName.setText(name);

        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(DialogInterface dialog) {
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(
                        !TextUtils.isEmpty(editTextGameName.getText())
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
