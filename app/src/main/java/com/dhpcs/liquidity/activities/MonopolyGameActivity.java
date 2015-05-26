package com.dhpcs.liquidity.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.fragments.AddPlayersDialogFragment;
import com.dhpcs.liquidity.fragments.PlayersFragment;
import com.dhpcs.liquidity.models.Member;
import com.dhpcs.liquidity.models.ZoneId;

public class MonopolyGameActivity extends AppCompatActivity
        implements PlayersFragment.Listener {

    public static final String EXTRA_ZONE_ID = "zoneId";

    private ZoneId zoneId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monopoly_game);
        zoneId = (ZoneId) getIntent().getSerializableExtra(EXTRA_ZONE_ID);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_monopoly_game, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // TODO
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_add_players) {
            AddPlayersDialogFragment.newInstance(zoneId)
                    .show(
                            getFragmentManager(),
                            "addPlayersDialogFragment"
                    );
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPlayerClicked(Member member) {
        // TODO
        Toast.makeText(this, "onPlayerClicked: " + member, Toast.LENGTH_SHORT).show();
    }

}
