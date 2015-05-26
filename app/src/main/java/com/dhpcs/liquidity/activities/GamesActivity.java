package com.dhpcs.liquidity.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

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
                                "newMonopolyGameDialogFragment"
                        );
            }
        });

        findViewById(R.id.button_join_game).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                JoinGameDialogFragment.newInstance()
                        .show(
                                getFragmentManager(),
                                "joinGameDialogFragment"
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
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_games, menu);
        return true;
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
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // TODO
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStartingCapitalEntered(BigDecimal startingCapital) {
        // TODO
        Toast.makeText(this, "onStartingCapitalEntered: " + startingCapital, Toast.LENGTH_SHORT).show();
        // MonopolyGame.getCreateZoneCommand("Dave's zone");
    }

}
