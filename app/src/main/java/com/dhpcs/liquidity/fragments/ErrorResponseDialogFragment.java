package com.dhpcs.liquidity.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.ErrorResponse;

public class ErrorResponseDialogFragment extends DialogFragment {

    public static final String TAG = "error_response_dialog_fragment";

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
        ErrorResponse errorResponse =
                (ErrorResponse) getArguments().getSerializable(ARG_ERROR_RESPONSE);
        assert errorResponse != null;
        return new AlertDialog.Builder(getActivity())
                .setMessage(errorResponse.message())
                .setPositiveButton(R.string.ok, null)
                .create();
    }

}
