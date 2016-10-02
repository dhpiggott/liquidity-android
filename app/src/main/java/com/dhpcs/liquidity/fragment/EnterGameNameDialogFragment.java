package com.dhpcs.liquidity.fragment;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.boardgame.BoardGame$;
import com.dhpcs.liquidity.protocol.package$;

public class
EnterGameNameDialogFragment extends AppCompatDialogFragment {

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

    private Button buttonPositive;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        listener = (Listener) context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        @SuppressLint("InflateParams") View view = getActivity().getLayoutInflater().inflate(
                R.layout.fragment_enter_game_name_dialog,
                null
        );

        String name = getArguments().getString(ARG_NAME);

        TextInputLayout textInputLayoutGameName = (TextInputLayout)
                view.findViewById(R.id.textinputlayout_game_name);
        final TextInputEditText textInputEditTextGameName = (TextInputEditText)
                view.findViewById(R.id.textinputedittext_game_name);

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
                                            textInputEditTextGameName.getText().toString()
                                    );
                                }
                            }

                        }
                )
                .create();

        textInputLayoutGameName.setCounterMaxLength(package$.MODULE$.MaxStringLength());
        textInputEditTextGameName.addTextChangedListener(new TextWatcher() {

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

        textInputEditTextGameName.setText(name);

        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(DialogInterface dialog) {
                buttonPositive = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                validateInput(textInputEditTextGameName.getText());
            }

        });

        Window window = alertDialog.getWindow();
        assert window != null;
        window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        );

        return alertDialog;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    private void validateInput(CharSequence gameName) {
        buttonPositive.setEnabled(
                BoardGame$.MODULE$.isGameNameValid(gameName)
        );
    }

}
