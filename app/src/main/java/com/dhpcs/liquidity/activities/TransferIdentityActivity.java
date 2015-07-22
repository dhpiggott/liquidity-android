package com.dhpcs.liquidity.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.dhpcs.liquidity.R;
import com.google.zxing.Result;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class TransferIdentityActivity extends AppCompatActivity
        implements ZXingScannerView.ResultHandler {

    public static final String EXTRA_RESULT_TEXT = "text";

    private ZXingScannerView scannerView;

    @Override
    public void handleResult(Result rawResult) {
        setResult(
                RESULT_OK,
                new Intent().putExtra(
                        EXTRA_RESULT_TEXT,
                        rawResult.getText()
                )
        );
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer_identity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);

        toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                NavUtils.navigateUpFromSameTask(TransferIdentityActivity.this);
            }

        });

        scannerView = (ZXingScannerView) findViewById(R.id.scannerview);
        scannerView.setResultHandler(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scannerView.stopCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scannerView.startCamera();
    }

}
