package com.dhpcs.liquidity.activity;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.dhpcs.liquidity.R;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import scala.Option;

public class TransferIdentityActivity extends BoardGameChildActivity {

    public static final String EXTRA_IDENTITY_NAME_HOLDER = "identity_name_holder";
    public static final String EXTRA_IDENTITY_NAME = "identity_name";

    private static final int REQUEST_CODE_GRANT_CAMERA_PERMISSION = 0;

    private LinearLayout linearLayoutTransferIdentity;

    private CaptureManager capture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer_identity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayHomeAsUpEnabled(true);

        @SuppressWarnings("unchecked") Option<String> identityName = (Option<String>) getIntent()
                .getBundleExtra(EXTRA_IDENTITY_NAME_HOLDER)
                .getSerializable(EXTRA_IDENTITY_NAME);

        linearLayoutTransferIdentity = (LinearLayout)
                findViewById(R.id.linearlayout_transfer_identity);

        DecoratedBarcodeView barcodeScannerView = (DecoratedBarcodeView)
                findViewById(R.id.zxing_barcode_scanner);
        assert barcodeScannerView != null;
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
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_GRANT_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(
                        linearLayoutTransferIdentity,
                        R.string.camera_permission_rationale,
                        Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.settings, new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        try {
                            startActivity(
                                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(Uri.parse("package:" + getPackageName()))
                            );
                        } catch (ActivityNotFoundException e1) {
                            try {
                                startActivity(
                                        new Intent(Settings.ACTION_APPLICATION_SETTINGS)
                                );
                            } catch (ActivityNotFoundException e2) {
                                startActivity(
                                        new Intent(Settings.ACTION_SETTINGS)
                                );
                            }
                        }
                    }

                }).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {
            capture.onResume();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        capture.onSaveInstanceState(outState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        }
    }

    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            Snackbar.make(
                    linearLayoutTransferIdentity,
                    R.string.camera_permission_rationale,
                    Snackbar.LENGTH_INDEFINITE
            ).setAction(R.string.ok, new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    ActivityCompat.requestPermissions(
                            TransferIdentityActivity.this,
                            new String[]{Manifest.permission.CAMERA},
                            REQUEST_CODE_GRANT_CAMERA_PERMISSION
                    );
                }

            }).show();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CODE_GRANT_CAMERA_PERMISSION
            );
        }
    }

}
