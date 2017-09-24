package com.dhpcs.liquidity.activity;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.WindowManager;
import android.widget.ImageView;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.boardgame.BoardGame.IdentityWithBalance;
import com.dhpcs.liquidity.model.PublicKey;

import net.glxn.qrgen.android.QRCode;

public class ReceiveIdentityActivity extends BoardGameChildActivity {

    public static final String EXTRA_PUBLIC_KEY = "public_key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_receive_identity);

        Toolbar toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayHomeAsUpEnabled(true);

        final PublicKey publicKey = (PublicKey) getIntent().getSerializableExtra(EXTRA_PUBLIC_KEY);

        final ImageView imageViewQrCode = findViewById(R.id.imageview_qr_code);
        assert imageViewQrCode != null;
        imageViewQrCode.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> imageViewQrCode.setImageBitmap(
                ((QRCode) QRCode.from(publicKey.value().base64())
                        .withSize(right - left, bottom - top))
                        .bitmap()
        ));
    }

    @Override
    public void onIdentityReceived(IdentityWithBalance identity) {
        setResult(RESULT_OK);
        finish();
    }

}
