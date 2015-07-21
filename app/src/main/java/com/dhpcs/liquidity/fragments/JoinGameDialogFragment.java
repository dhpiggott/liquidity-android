package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dhpcs.liquidity.R;
import com.google.zxing.Result;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class JoinGameDialogFragment extends DialogFragment
        implements ZXingScannerView.ResultHandler {

    public interface Listener {

        void onGameIdScanned(Result rawResult);

    }

    public static JoinGameDialogFragment newInstance() {
        return new JoinGameDialogFragment();
    }

    private Listener listener;
    private ZXingScannerView scannerView;

    @Override
    public void handleResult(Result rawResult) {
        if (listener != null) {
            listener.onGameIdScanned(rawResult);
            getDialog().dismiss();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        listener = (Listener) activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Light);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_join_game_dialog, container, false);

        scannerView = (ZXingScannerView) view.findViewById(R.id.scannerview_scanner);

        return view;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onPause() {
        super.onPause();
        scannerView.stopCamera();
    }

    @Override
    public void onResume() {
        super.onResume();
        scannerView.setResultHandler(this);
        scannerView.startCamera();
    }

}
