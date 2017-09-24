package com.dhpcs.liquidity.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.fragment.AddGameBottomSheetDialogFragment;
import com.dhpcs.liquidity.fragment.CreateGameDialogFragment;
import com.dhpcs.liquidity.fragment.GamesFragment;
import com.dhpcs.liquidity.model.ZoneId;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.Collections;
import java.util.Currency;

public class GamesActivity extends AppCompatActivity
        implements AddGameBottomSheetDialogFragment.Listener,
        CreateGameDialogFragment.Listener,
        GamesFragment.Listener {

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            String contents = result.getContents();
            if (contents != null) {
                startActivity(
                        new Intent(
                                GamesActivity.this,
                                BoardGameActivity.class
                        ).putExtra(
                                BoardGameActivity.EXTRA_ZONE_ID,
                                new ZoneId(contents)
                        )
                );
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_games);

        Toolbar toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);

        View floatingActionButtonAddGame = findViewById(R.id.floatingactionbutton_add_game);
        assert floatingActionButtonAddGame != null;
        floatingActionButtonAddGame.setOnClickListener(v -> AddGameBottomSheetDialogFragment.newInstance()
                .show(
                        getSupportFragmentManager(),
                        AddGameBottomSheetDialogFragment.TAG
                ));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.games_toolbar, menu);
        return true;
    }

    @Override
    public void onGameClicked(long gameId, ZoneId zoneId, String gameName) {
        startActivity(
                new Intent(
                        GamesActivity.this,
                        BoardGameActivity.class
                ).putExtra(
                        BoardGameActivity.EXTRA_GAME_ID,
                        gameId
                ).putExtra(
                        BoardGameActivity.EXTRA_ZONE_ID,
                        zoneId
                ).putExtra(
                        BoardGameActivity.EXTRA_GAME_NAME,
                        gameName
                )
        );
    }

    @Override
    public void onGameDetailsEntered(String name, Currency currency) {
        startActivity(
                new Intent(
                        GamesActivity.this,
                        BoardGameActivity.class
                ).putExtra(
                        BoardGameActivity.EXTRA_CURRENCY,
                        currency
                ).putExtra(
                        BoardGameActivity.EXTRA_GAME_NAME,
                        name
                )
        );
    }

    @Override
    public void onJoinGameClicked() {
        new IntentIntegrator(GamesActivity.this)
                .setCaptureActivity(JoinGameActivity.class)
                .setDesiredBarcodeFormats(
                        Collections.singleton("QR_CODE")
                )
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                .initiateScan();
    }

    @Override
    public void onNewGameClicked() {
        CreateGameDialogFragment.newInstance()
                .show(
                        getSupportFragmentManager(),
                        CreateGameDialogFragment.TAG
                );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_preferences:
                startActivity(
                        new Intent(
                                GamesActivity.this,
                                PreferencesActivity.class
                        )
                );
                return true;
            case R.id.action_about:
                startActivity(
                        new Intent(
                                GamesActivity.this,
                                AboutActivity.class
                        )
                );
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
