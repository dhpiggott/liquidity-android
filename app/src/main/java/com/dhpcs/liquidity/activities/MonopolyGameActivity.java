package com.dhpcs.liquidity.activities;

import android.app.FragmentManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.dhpcs.liquidity.MonopolyGame;
import com.dhpcs.liquidity.MonopolyGame.IdentityWithBalance;
import com.dhpcs.liquidity.MonopolyGame.PlayerWithBalanceAndConnectionState;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.fragments.AddPlayersDialogFragment;
import com.dhpcs.liquidity.fragments.IdentitiesFragment;
import com.dhpcs.liquidity.fragments.MonopolyGameHolderFragment;
import com.dhpcs.liquidity.fragments.PlayersFragment;
import com.dhpcs.liquidity.fragments.TransferToPlayerDialogFragment;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.models.ZoneId;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;

import scala.Tuple2;

// TODO: Why AppCompat?
public class MonopolyGameActivity extends AppCompatActivity
        implements MonopolyGame.Listener, PlayersFragment.Listener,
        TransferToPlayerDialogFragment.Listener {

    public static final String EXTRA_INITIAL_CAPITAL = "initial_capital";
    public static final String EXTRA_ZONE_ID = "zone_id";

    public static String formatBalance(Tuple2<scala.math.BigDecimal, String>
                                               balanceWithCurrencyCode) {
        BigDecimal balance = balanceWithCurrencyCode._1().bigDecimal();
        String currencyCode = balanceWithCurrencyCode._2();

        String balanceString;
        try {
            Currency currency = Currency.getInstance(currencyCode);

            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance();
            currencyFormat.setCurrency(currency);
            balanceString = currencyFormat.format(balance);
        } catch (IllegalArgumentException e) {
            NumberFormat numberFormat = NumberFormat.getNumberInstance();
            balanceString = currencyCode + " " + numberFormat.format(balance);
        }

        return balanceString;
    }

    private MonopolyGameHolderFragment monopolyGameHolderFragment;
    private IdentitiesFragment identitiesFragment;
    private PlayersFragment playersFragment;

    private ZoneId zoneId;

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
                        scala.math.BigDecimal.javaBigDecimal2bigDecimal(
                                (BigDecimal) getIntent().getSerializableExtra(EXTRA_INITIAL_CAPITAL)
                        )
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
    public void onIdentitiesChanged(scala.collection.immutable
                                            .Map<MemberId, IdentityWithBalance>
                                            identities) {
        identitiesFragment.onIdentitiesChanged(identities);
    }

    @Override
    public void onIdentityAdded(
            Tuple2<MemberId, IdentityWithBalance> addedIdentity) {
        identitiesFragment.onIdentityAdded(addedIdentity);
    }

    @Override
    public void onIdentityRemoved(
            Tuple2<MemberId, IdentityWithBalance> removedIdentity) {
        identitiesFragment.onIdentityRemoved(removedIdentity);
    }

    @Override
    public void onIdentityUpdated(Tuple2<MemberId, IdentityWithBalance> removedIdentity,
                                  Tuple2<MemberId, IdentityWithBalance> addedIdentity) {
        identitiesFragment.onIdentityUpdated(removedIdentity, addedIdentity);
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
    public void onPlayersChanged(scala.collection.immutable
                                         .Map<MemberId, PlayerWithBalanceAndConnectionState>
                                         players) {
        playersFragment.onPlayersChanged(players);
    }

    @Override
    public void onPlayerAdded(
            Tuple2<MemberId, PlayerWithBalanceAndConnectionState> addedPlayer) {
        playersFragment.onPlayerAdded(addedPlayer);
    }

    @Override
    public void onPlayerClicked(MemberId playerId) {
        MemberId identityId = identitiesFragment.getIdentityId();
        if (identityId == null) {
            // TODO
        } else {
            TransferToPlayerDialogFragment.newInstance(identityId, playerId)
                    .show(
                            getFragmentManager(),
                            "transfer_to_player_dialog_fragment"
                    );
        }
    }

    @Override
    public void onPlayerRemoved(
            Tuple2<MemberId, PlayerWithBalanceAndConnectionState> removedPlayer) {
        playersFragment.onPlayerRemoved(removedPlayer);
    }

    @Override
    public void onPlayerUpdated(Tuple2<MemberId, PlayerWithBalanceAndConnectionState> removedPlayer,
                                Tuple2<MemberId, PlayerWithBalanceAndConnectionState> addedPlayer) {
        playersFragment.onPlayerUpdated(removedPlayer, addedPlayer);
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
    public void onTransferValueEntered(MemberId fromMemberId,
                                       MemberId toMemberId,
                                       BigDecimal transferValue) {
        monopolyGameHolderFragment.getMonopolyGame().transfer(
                fromMemberId,
                toMemberId,
                scala.math.BigDecimal.javaBigDecimal2bigDecimal(transferValue)
        );
    }

}
