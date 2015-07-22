package com.dhpcs.liquidity.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.dhpcs.liquidity.GameType;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.fragments.GamesFragment;
import com.dhpcs.liquidity.models.ZoneId;

import java.util.UUID;

public class GamesActivity extends AppCompatActivity implements GamesFragment.Listener {

    public static final String EXTRA_GAME_TYPE = GamesFragment.EXTRA_GAME_TYPE;

    private static final int REQUEST_JOIN_GAME = 1;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_JOIN_GAME) {
            if (resultCode == RESULT_OK) {
                try {
                    ZoneId zoneId = new ZoneId(
                            UUID.fromString(
                                    data.getStringExtra(JoinGameActivity.EXTRA_RESULT_TEXT)
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
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_games);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);

        toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                NavUtils.navigateUpFromSameTask(GamesActivity.this);
            }

        });

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
                startActivityForResult(
                        new Intent(
                                GamesActivity.this,
                                JoinGameActivity.class
                        ).putExtra(
                                JoinGameActivity.EXTRA_GAME_TYPE,
                                getIntent().getSerializableExtra(EXTRA_GAME_TYPE)
                        ),
                        REQUEST_JOIN_GAME
                );
            }

        });

        getFragmentManager().beginTransaction().add(
                R.id.framelayout_games,
                GamesFragment.newInstance(
                        (GameType) getIntent().getSerializableExtra(EXTRA_GAME_TYPE)
                )
        ).commit();
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
