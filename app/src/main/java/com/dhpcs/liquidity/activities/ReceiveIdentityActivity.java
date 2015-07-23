package com.dhpcs.liquidity.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ImageView;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.PublicKey;
import com.google.common.io.BaseEncoding;

import net.glxn.qrgen.android.QRCode;

public class ReceiveIdentityActivity extends AppCompatActivity {

    public static final String EXTRA_PUBLIC_KEY = "public_key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_identity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final PublicKey publicKey = (PublicKey) getIntent().getSerializableExtra(EXTRA_PUBLIC_KEY);

        final ImageView imageViewQrCode = (ImageView) findViewById(R.id.imageview_qr_code);
        imageViewQrCode.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {

            @Override
            public void onLayoutChange(View v,
                                       int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                imageViewQrCode.setImageBitmap(
                        ((QRCode) QRCode.from(BaseEncoding.base64().encode(publicKey.value()))
                                .withSize(right - left, bottom - top))
                                .bitmap()
                );
            }

        });
    }

}
