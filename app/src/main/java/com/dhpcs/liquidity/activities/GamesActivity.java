package com.dhpcs.liquidity.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.dhpcs.liquidity.GameType;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.fragments.GamesFragment;
import com.dhpcs.liquidity.fragments.JoinGameDialogFragment;
import com.dhpcs.liquidity.models.ZoneId;
import com.google.zxing.Result;

import java.util.UUID;

public class GamesActivity extends AppCompatActivity
        implements GamesFragment.Listener,
        JoinGameDialogFragment.Listener {

    public static final String GAME_TYPE = GamesFragment.GAME_TYPE;

    private static void createGame(Context context) {
        context.startActivity(
                new Intent(
                        context,
                        MonopolyGameActivity.class
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

    private static void rejoinGame(Context context, long gameId, ZoneId zoneId, String gameName) {
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
                ).putExtra(
                        MonopolyGameActivity.EXTRA_GAME_NAME,
                        gameName
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
                createGame(GamesActivity.this);
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
    public void onGameClicked(long gameId, ZoneId zoneId, String gameName) {
        rejoinGame(this, gameId, zoneId, gameName);
    }

    @Override
    public void onGameIdScanned(Result rawResult) {
        try {
            ZoneId zoneId = new ZoneId(
                    UUID.fromString(
                            rawResult.getText()
                    )
            );
            joinGame(this, zoneId);
        } catch (IllegalArgumentException iae) {
            // TODO
            Toast.makeText(
                    this,
                    R.string.qr_code_is_not_a_zone_id,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

}
