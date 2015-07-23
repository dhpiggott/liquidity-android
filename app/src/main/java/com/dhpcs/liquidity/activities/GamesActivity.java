package com.dhpcs.liquidity.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.fragments.GamesFragment;
import com.dhpcs.liquidity.models.ZoneId;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.Collections;
import java.util.UUID;

public class GamesActivity extends AppCompatActivity implements GamesFragment.Listener {

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            String contents = result.getContents();
            if (contents != null) {
                try {
                    ZoneId zoneId = new ZoneId(
                            UUID.fromString(
                                    contents
                            )
                    );
                    startActivity(
                            new Intent(
                                    GamesActivity.this,
                                    MonopolyGameActivity.class
                            ).putExtra(
                                    MonopolyGameActivity.EXTRA_ZONE_ID,
                                    zoneId
                            )
                    );
                } catch (IllegalArgumentException e) {
                    Snackbar.make(
                            findViewById(R.id.coordinatorlayout),
                            R.string.that_is_not_a_liquidity_game,
                            Snackbar.LENGTH_LONG
                    ).show();
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_games);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);

        findViewById(R.id.button_new_game).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startActivity(
                        new Intent(
                                GamesActivity.this,
                                MonopolyGameActivity.class
                        )
                );
            }

        });

        findViewById(R.id.button_join_game).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                new IntentIntegrator(GamesActivity.this)
                        .setCaptureActivity(JoinGameActivity.class)
                        .setDesiredBarcodeFormats(Collections.singleton("QR_CODE"))
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                        .initiateScan();
            }

        });
    }

    @Override
    public void onGameClicked(long gameId, ZoneId zoneId, String gameName) {
        startActivity(
                new Intent(
                        GamesActivity.this,
                        MonopolyGameActivity.class
                ).putExtra(
                        MonopolyGameActivity.EXTRA_GAME_ID,
                        gameId
                ).putExtra(
                        MonopolyGameActivity.EXTRA_ZONE_ID,
                        zoneId
                ).putExtra(
                        MonopolyGameActivity.EXTRA_GAME_NAME,
                        gameName
                )
        );
    }

}
