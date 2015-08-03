package com.dhpcs.liquidity.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;

import com.dhpcs.liquidity.MonopolyGame;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.ZoneId;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.CompoundBarcodeView;

public class TransferIdentityActivity extends AppCompatActivity {

    public static final String EXTRA_ZONE_ID_HOLDER = "zone_id_holder";

    public static final String EXTRA_IDENTITY_NAME = "identity_name";
    public static final String EXTRA_ZONE_ID = "zone_id";

    private MonopolyGame.JoinRequestToken joinRequestToken;

    private MonopolyGame monopolyGame;
    private CaptureManager capture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer_identity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        String identityName = getIntent().getStringExtra(EXTRA_IDENTITY_NAME);

        CompoundBarcodeView barcodeScannerView = (CompoundBarcodeView)
                findViewById(R.id.zxing_barcode_scanner);
        barcodeScannerView.setStatusText(
                getString(
                        R.string.transfer_identity_identity_name_format_string,
                        identityName
                )
        );

        joinRequestToken = (MonopolyGame.JoinRequestToken) getLastCustomNonConfigurationInstance();

        if (joinRequestToken == null) {

            joinRequestToken = new MonopolyGame.JoinRequestToken() {
            };

        }

        monopolyGame = MonopolyGame.getInstance(
                (ZoneId) getIntent()
                        .getBundleExtra(EXTRA_ZONE_ID_HOLDER)
                        .getSerializable(EXTRA_ZONE_ID)
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
        if (!isChangingConfigurations()) {
            if (!isFinishing()) {
                monopolyGame.unrequestJoin(joinRequestToken);
            }
        }
        capture.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        monopolyGame.requestJoin(joinRequestToken, false);
        capture.onResume();
    }

    @Override
    public MonopolyGame.JoinRequestToken onRetainCustomNonConfigurationInstance() {
        return joinRequestToken;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        capture.onSaveInstanceState(outState);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            if (isFinishing()) {
                monopolyGame.unrequestJoin(joinRequestToken);
            }
        }
    }

}
