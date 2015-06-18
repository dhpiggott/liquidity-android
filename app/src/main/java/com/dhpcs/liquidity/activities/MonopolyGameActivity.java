package com.dhpcs.liquidity.activities;

import android.app.FragmentManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.dhpcs.liquidity.MonopolyGame;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.fragments.AddPlayersDialogFragment;
import com.dhpcs.liquidity.fragments.IdentitiesFragment;
import com.dhpcs.liquidity.fragments.MonopolyGameHolderFragment;
import com.dhpcs.liquidity.fragments.PlayersFragment;
import com.dhpcs.liquidity.models.Member;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.models.ZoneId;

import java.math.BigDecimal;
import java.util.Map;

// TODO: Why AppCompat?
public class MonopolyGameActivity extends AppCompatActivity
        implements MonopolyGame.Listener, PlayersFragment.Listener {

    public static final String EXTRA_INITIAL_CAPITAL = "initial_capital";
    public static final String EXTRA_ZONE_ID = "zone_id";

    private MonopolyGameHolderFragment monopolyGameHolderFragment;
    private IdentitiesFragment identitiesFragment;
    private PlayersFragment playersFragment;

    private ZoneId zoneId;

    @Override
    public void onConnectedPlayersChanged(Map<MemberId, Member> connectedPlayers) {
        // TODO
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_monopoly_game, menu);
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_monopoly_game);

        FragmentManager fragmentManager = getFragmentManager();
        monopolyGameHolderFragment = (MonopolyGameHolderFragment) fragmentManager
                .findFragmentByTag("monopoly_game_holder");

        if (monopolyGameHolderFragment == null) {

            MonopolyGame monopolyGame = new MonopolyGame(this);

            if (getIntent().hasExtra(EXTRA_INITIAL_CAPITAL)) {

                monopolyGame.setInitialCapital(
                        (BigDecimal) getIntent().getSerializableExtra(EXTRA_INITIAL_CAPITAL)
                );

            } else if (getIntent().hasExtra(EXTRA_ZONE_ID)) {

                monopolyGame.setZoneId(
                        (ZoneId) getIntent().getSerializableExtra(EXTRA_ZONE_ID)
                );

            } else {
                throw new RuntimeException(
                        "Neither EXTRA_INITIAL_CAPITAL nor EXTRA_ZONE_ID was provided"
                );
            }

            monopolyGameHolderFragment = new MonopolyGameHolderFragment(
                    monopolyGame
            );

            fragmentManager.beginTransaction()
                    .add(monopolyGameHolderFragment, "monopoly_game_holder")
                    .commit();
        }

        identitiesFragment = (IdentitiesFragment)
                fragmentManager.findFragmentById(R.id.fragment_identities);
        playersFragment = (PlayersFragment)
                fragmentManager.findFragmentById(R.id.fragment_players);
    }

    @Override
    public void onJoined(ZoneId zoneId) {
        this.zoneId = zoneId;
        supportInvalidateOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
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
    public void onOtherPlayersChanged(Map<MemberId, Member> otherPlayers) {
        playersFragment.onPlayersChanged(otherPlayers);
    }

    @Override
    public void onPlayerBalancesChanged(Map<MemberId, BigDecimal> playerBalances) {
        // TODO
    }

    @Override
    public void onPlayerClicked(MemberId memberId) {
        // TODO
        Toast.makeText(this, "onPlayerClicked: " + memberId, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_add_players).setVisible(zoneId != null);
        return true;
    }

    @Override
    public void onQuit() {
        this.zoneId = null;
        supportInvalidateOptionsMenu();
    }

    @Override
    public void onStart() {
        super.onStart();
        monopolyGameHolderFragment.getMonopolyGame().setListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        monopolyGameHolderFragment.getMonopolyGame().setListener(null);
    }

    @Override
    public void onUserIdentitiesChanged(Map<MemberId, Member> userIdentities) {
        identitiesFragment.onIdentitiesChanged(userIdentities);
    }

}
