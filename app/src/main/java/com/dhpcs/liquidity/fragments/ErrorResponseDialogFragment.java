package com.dhpcs.liquidity.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.ErrorResponse;

public class ErrorResponseDialogFragment extends DialogFragment {

    private static final String ARG_ERROR_RESPONSE = "error_response";

    public static ErrorResponseDialogFragment newInstance(ErrorResponse errorResponse) {
        ErrorResponseDialogFragment transferToPlayerDialogFragment =
                new ErrorResponseDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_ERROR_RESPONSE, errorResponse);
        transferToPlayerDialogFragment.setArguments(args);
        return transferToPlayerDialogFragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setMessage(
                        ((ErrorResponse) getArguments().getSerializable(ARG_ERROR_RESPONSE))
                                .message()
                )
                .setPositiveButton(
                        R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                getDialog().dismiss();
                            }
                        }
                )
                .create();
    }

}
