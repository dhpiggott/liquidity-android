package com.dhpcs.liquidity.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.dhpcs.liquidity.GameType;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.fragments.GamesFragment;
import com.dhpcs.liquidity.fragments.JoinGameDialogFragment;
import com.dhpcs.liquidity.fragments.NewMonopolyGameDialogFragment;
import com.dhpcs.liquidity.models.ZoneId;

import java.math.BigDecimal;

public class GamesActivity extends AppCompatActivity
        implements NewMonopolyGameDialogFragment.Listener,
        JoinGameDialogFragment.Listener, GamesFragment.Listener {

    public static final String GAME_TYPE = GamesFragment.GAME_TYPE;

    private void createGame(BigDecimal startingCapital) {
        startActivity(
                new Intent(
                        this,
                        MonopolyGameActivity.class
                ).putExtra(
                        MonopolyGameActivity.EXTRA_INITIAL_CAPITAL,
                        startingCapital
                )
        );
    }

    private void joinGame(ZoneId zoneId) {
        startActivity(
                new Intent(
                        this,
                        MonopolyGameActivity.class
                ).putExtra(
                        MonopolyGameActivity.EXTRA_ZONE_ID,
                        zoneId
                )
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_games);

        findViewById(R.id.button_new_game).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NewMonopolyGameDialogFragment.newInstance()
                        .show(
                                getFragmentManager(),
                                "new_monopoly_game_dialog_fragment"
                        );
            }
        });

        findViewById(R.id.button_join_game).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                JoinGameDialogFragment.newInstance()
                        .show(
                                getFragmentManager(),
                                "join_game_dialog_fragment"
                        );
            }
        });

        getFragmentManager().beginTransaction().add(
                R.id.framelayout_games,
                GamesFragment.newInstance(
                        (GameType) getIntent().getSerializableExtra(GAME_TYPE)
                )
        ).commit();
    }

    @Override
    public void onGameClicked(ZoneId zoneId) {
        joinGame(zoneId);
    }

    @Override
    public void onGameZoneIdScanned(ZoneId zoneId) {
        joinGame(zoneId);
    }

    @Override
    public void onStartingCapitalEntered(BigDecimal startingCapital) {
        createGame(startingCapital);
    }

}
