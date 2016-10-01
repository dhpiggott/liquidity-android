package com.dhpcs.liquidity.activity;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.model.ZoneId;

import net.glxn.qrgen.android.QRCode;

import scala.Option;

public class AddPlayersActivity extends BoardGameChildActivity {

    public static final String EXTRA_GAME_NAME = "game_name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_add_players);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayHomeAsUpEnabled(true);

        final ZoneId zoneId = (ZoneId) getIntent()
                .getBundleExtra(EXTRA_ZONE_ID_HOLDER)
                .getSerializable(EXTRA_ZONE_ID);
        @SuppressWarnings("unchecked") Option<String> gameName = (Option<String>) getIntent()
                .getSerializableExtra(EXTRA_GAME_NAME);

        TextView textViewGameName = (TextView) findViewById(R.id.textview_game_name);
        assert textViewGameName != null;
        textViewGameName.setText(
                getString(
                        R.string.add_players_game_name_format_string,
                        BoardGameActivity.formatNullable(this, gameName)
                )
        );
        final ImageView imageViewQrCode = (ImageView) findViewById(R.id.imageview_qr_code);
        assert imageViewQrCode != null;
        imageViewQrCode.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {

            @Override
            public void onLayoutChange(View v,
                                       int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                assert zoneId != null;
                imageViewQrCode.setImageBitmap(
                        ((QRCode) QRCode.from(zoneId.id().toString())
                                .withSize(right - left, bottom - top))
                                .bitmap()
                );
            }

        });
    }

}
