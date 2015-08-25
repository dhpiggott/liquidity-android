package com.dhpcs.liquidity.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.fragment.CreateGameDialogFragment;
import com.dhpcs.liquidity.fragment.GamesFragment;
import com.dhpcs.liquidity.models.ZoneId;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.Collections;
import java.util.Currency;
import java.util.UUID;

public class GamesActivity extends AppCompatActivity implements CreateGameDialogFragment.Listener,
        GamesFragment.Listener {

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
                    Toast.makeText(
                            this,
                            R.string.join_game_error,
                            Toast.LENGTH_LONG
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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_games, menu);
        return true;
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

    @Override
    public void onGameDetailsEntered(String name, Currency currency) {
        startActivity(
                new Intent(
                        GamesActivity.this,
                        MonopolyGameActivity.class
                ).putExtra(
                        MonopolyGameActivity.EXTRA_CURRENCY,
                        currency
                ).putExtra(
                        MonopolyGameActivity.EXTRA_GAME_NAME,
                        name
                )
        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_new_game:
                CreateGameDialogFragment.newInstance()
                        .show(
                                getFragmentManager(),
                                CreateGameDialogFragment.TAG
                        );
                return true;
            case R.id.action_join_game:
                new IntentIntegrator(GamesActivity.this)
                        .setCaptureActivity(JoinGameActivity.class)
                        .setDesiredBarcodeFormats(Collections.singleton("QR_CODE"))
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                        .initiateScan();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
