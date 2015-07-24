package com.dhpcs.liquidity.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.ZoneId;

import net.glxn.qrgen.android.QRCode;

public class AddPlayersActivity extends AppCompatActivity {

    public static final String EXTRA_GAME_NAME = "game_name";
    public static final String EXTRA_ZONE_ID = "zone_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_players);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final String gameName = getIntent().getStringExtra(EXTRA_GAME_NAME);
        final ZoneId zoneId = (ZoneId) getIntent().getSerializableExtra(EXTRA_ZONE_ID);

        ((TextView) findViewById(R.id.textview_game_name)).setText(
                getString(
                        R.string.add_players_game_name_format_string,
                        gameName
                )
        );
        final ImageView imageViewQrCode = (ImageView) findViewById(R.id.imageview_qr_code);
        imageViewQrCode.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {

            @Override
            public void onLayoutChange(View v,
                                       int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                imageViewQrCode.setImageBitmap(
                        ((QRCode) QRCode.from(zoneId.id().toString())
                                .withSize(right - left, bottom - top))
                                .bitmap()
                );
            }

        });
    }

}
