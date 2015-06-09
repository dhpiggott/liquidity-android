package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.ZoneId;
import com.google.zxing.Result;

import java.util.UUID;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class JoinGameDialogFragment extends DialogFragment
        implements ZXingScannerView.ResultHandler {

    public interface Listener {

        void onGameZoneIdScanned(ZoneId zoneId);

    }

    public static JoinGameDialogFragment newInstance() {
        return new JoinGameDialogFragment();
    }

    private Listener listener;
    private ZXingScannerView scannerView;

    @Override
    public void handleResult(Result rawResult) {
        try {
            UUID zoneIdUuid = UUID.fromString(rawResult.getText());
            if (listener != null) {
                listener.onGameZoneIdScanned(new ZoneId(zoneIdUuid));
                getDialog().dismiss();
            }
        } catch (IllegalArgumentException iae) {
            Toast.makeText(
                    getActivity(),
                    R.string.qr_code_is_not_a_liquidity_game,
                    Toast.LENGTH_LONG
            ).show();
            scannerView.startCamera();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (Listener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement JoinGameDialogFragment.Listener");
        }
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
