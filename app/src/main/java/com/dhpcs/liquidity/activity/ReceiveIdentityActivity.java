package com.dhpcs.liquidity.activity;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.dhpcs.liquidity.BoardGame.IdentityWithBalance;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.PublicKey;

import net.glxn.qrgen.android.QRCode;

import okio.ByteString;

public class ReceiveIdentityActivity extends BoardGameChildActivity {

    public static final String EXTRA_PUBLIC_KEY = "public_key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_receive_identity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayHomeAsUpEnabled(true);

        final PublicKey publicKey = (PublicKey) getIntent().getSerializableExtra(EXTRA_PUBLIC_KEY);

        final ImageView imageViewQrCode = (ImageView) findViewById(R.id.imageview_qr_code);
        assert imageViewQrCode != null;
        imageViewQrCode.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {

            @Override
            public void onLayoutChange(View v,
                                       int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                imageViewQrCode.setImageBitmap(
                        ((QRCode) QRCode.from(ByteString.of(publicKey.value()).base64())
                                .withSize(right - left, bottom - top))
                                .bitmap()
                );
            }

        });
    }

    @Override
    public void onIdentityReceived(IdentityWithBalance identity) {
        setResult(RESULT_OK);
        finish();
    }

}
