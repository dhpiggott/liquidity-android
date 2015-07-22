package com.dhpcs.liquidity.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.dhpcs.liquidity.MONOPOLY$;
import com.dhpcs.liquidity.R;

public class GameTypesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_types);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        findViewById(R.id.button_monopoly).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startActivity(
                        new Intent(
                                GameTypesActivity.this,
                                GamesActivity.class
                        ).putExtra(
                                GamesActivity.EXTRA_GAME_TYPE,
                                MONOPOLY$.MODULE$
                        )
                );
            }

        });
    }

}
