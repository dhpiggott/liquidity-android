package com.dhpcs.liquidity.activity;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;

import com.dhpcs.liquidity.R;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.CompoundBarcodeView;

import scala.Option;

public class TransferIdentityActivity extends BoardGameChildActivity {

    public static final String EXTRA_IDENTITY_NAME_HOLDER = "identity_name_holder";
    public static final String EXTRA_IDENTITY_NAME = "identity_name";

    private CaptureManager capture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer_identity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        @SuppressWarnings("unchecked") Option<String> identityName = (Option<String>) getIntent()
                .getBundleExtra(EXTRA_IDENTITY_NAME_HOLDER)
                .getSerializable(EXTRA_IDENTITY_NAME);

        CompoundBarcodeView barcodeScannerView = (CompoundBarcodeView)
                findViewById(R.id.zxing_barcode_scanner);
        barcodeScannerView.setStatusText(
                getString(
                        R.string.transfer_identity_identity_name_format_string,
                        BoardGameActivity.formatNullable(this, identityName)
                )
        );

        capture = new CaptureManager(this, barcodeScannerView);
        capture.initializeFromIntent(getIntent(), savedInstanceState);
        capture.decode();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        capture.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_FOCUS:
            case KeyEvent.KEYCODE_CAMERA:

                /*
                 * We handle these events so they don't launch the Camera app.
                 */
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        capture.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        capture.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        capture.onSaveInstanceState(outState);
    }

}