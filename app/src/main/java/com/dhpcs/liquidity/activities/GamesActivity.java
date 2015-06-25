package com.dhpcs.liquidity.activities;

import android.content.Context;
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
        implements GamesFragment.Listener,
        JoinGameDialogFragment.Listener,
        NewMonopolyGameDialogFragment.Listener {

    public static final String GAME_TYPE = GamesFragment.GAME_TYPE;

    private static void createGame(Context context, BigDecimal initialCapital) {
        context.startActivity(
                new Intent(
                        context,
                        MonopolyGameActivity.class
                ).putExtra(
                        MonopolyGameActivity.EXTRA_INITIAL_CAPITAL,
                        initialCapital
                )
        );
    }

    private static void joinGame(Context context, ZoneId zoneId) {
        context.startActivity(
                new Intent(
                        context,
                        MonopolyGameActivity.class
                ).putExtra(
                        MonopolyGameActivity.EXTRA_ZONE_ID,
                        zoneId
                )
        );
    }

    private static void rejoinGame(Context context, long gameId, ZoneId zoneId) {
        context.startActivity(
                new Intent(
                        context,
                        MonopolyGameActivity.class
                ).putExtra(
                        MonopolyGameActivity.EXTRA_GAME_ID,
                        gameId
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
    public void onGameClicked(long gameId, ZoneId zoneId) {
        rejoinGame(this, gameId, zoneId);
    }

    @Override
    public void onGameZoneIdScanned(ZoneId zoneId) {
        joinGame(this, zoneId);
    }

    @Override
    public void onInitialCapitalEntered(BigDecimal initialCapital) {
        createGame(this, initialCapital);
    }

}
