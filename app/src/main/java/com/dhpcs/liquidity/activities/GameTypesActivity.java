package com.dhpcs.liquidity.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.dhpcs.liquidity.GameType;
import com.dhpcs.liquidity.MONOPOLY$;
import com.dhpcs.liquidity.R;

public class GameTypesActivity extends AppCompatActivity {

    private static void startGamesActivity(Context context, GameType gameType) {
        context.startActivity(
                new Intent(
                        context,
                        GamesActivity.class
                ).putExtra(
                        GamesActivity.GAME_TYPE,
                        gameType
                )
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_types);

        findViewById(R.id.button_monopoly).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGamesActivity(GameTypesActivity.this, MONOPOLY$.MODULE$);
            }
        });
    }

}
