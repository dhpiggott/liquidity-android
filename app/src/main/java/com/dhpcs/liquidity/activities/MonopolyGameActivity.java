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

public class MonopolyGameActivity extends AppCompatActivity
        implements MonopolyGame.Listener, PlayersFragment.Listener {

    public static final String EXTRA_STARTING_CAPITAL = "starting_capital";
    public static final String EXTRA_ZONE_ID = "zone_id";

    private MonopolyGameHolderFragment monopolyGameHolderFragment;
    private IdentitiesFragment identitiesFragment;
    private PlayersFragment playersFragment;

    private ZoneId zoneId;
    private boolean isJoined;
    private MenuItem addPlayers;

    @Override
    public void onConnectedMembersChanged(Map<MemberId, Member> connectedMembers) {
        // TODO
    }

    @Override
    public void onConnected() {

        if (getIntent().hasExtra(EXTRA_STARTING_CAPITAL)) {

            monopolyGameHolderFragment.getMonopolyGame().createAndJoinZone();

        } else if (getIntent().hasExtra(EXTRA_ZONE_ID)) {

            monopolyGameHolderFragment.getMonopolyGame().join(
                    (ZoneId) getIntent().getSerializableExtra(EXTRA_ZONE_ID)
            );

        } else {
            throw new RuntimeException("Neither EXTRA_STARTING_CAPITAL nor EXTRA_ZONE_ID was provided");
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_monopoly_game);

        FragmentManager fragmentManager = getFragmentManager();
        monopolyGameHolderFragment = (MonopolyGameHolderFragment) fragmentManager
                .findFragmentByTag("monopoly_game_holder");

        if (monopolyGameHolderFragment == null) {

            monopolyGameHolderFragment = new MonopolyGameHolderFragment(
                    new MonopolyGame(this)
            );

            fragmentManager.beginTransaction()
                    .add(monopolyGameHolderFragment, "monopoly_game_holder")
                    .commit();
        }

        identitiesFragment = (IdentitiesFragment) fragmentManager.findFragmentById(R.id.fragment_identities);
        playersFragment = (PlayersFragment) fragmentManager.findFragmentById(R.id.fragment_players);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_monopoly_game, menu);
        addPlayers = menu.findItem(R.id.action_add_players);
        return true;
    }

    @Override
    public void onCreated(ZoneId zoneId) {
        this.zoneId = zoneId;
        addPlayers.setVisible(true);
        monopolyGameHolderFragment.getMonopolyGame().createBanker();
    }

    @Override
    protected void onDestroy() {
        // TODO
        if (isFinishing()) {
            monopolyGameHolderFragment.getMonopolyGame().quit();
        }

        super.onDestroy();
    }

    @Override
    public void onDisconnected() {

        // TODO

    }

    @Override
    public void onMemberBalanceChanged(MemberId memberId, BigDecimal balance) {
        // TODO
    }

    @Override
    public void onOtherMembersChanged(Map<MemberId, Member> otherMembers) {
        playersFragment.onPlayersChanged(otherMembers);
    }

    @Override
    public void onUserMembersChanged(Map<MemberId, Member> userMembers) {
        if(userMembers.isEmpty()) {
            monopolyGameHolderFragment.getMonopolyGame().createPlayer();
        } else {
            identitiesFragment.onIdentitiesChanged(userMembers);
        }
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
    public void onPause() {
        super.onPause();
        monopolyGameHolderFragment.getMonopolyGame().removeListener(this);
    }

    @Override
    public void onPlayerClicked(MemberId memberId) {
        // TODO
        Toast.makeText(this, "onPlayerClicked: " + memberId, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        monopolyGameHolderFragment.getMonopolyGame().addListener(this);
    }

}
