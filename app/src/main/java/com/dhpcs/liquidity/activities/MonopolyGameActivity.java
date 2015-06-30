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
import com.dhpcs.liquidity.fragments.ChangeGameNameDialogFragment;
import com.dhpcs.liquidity.fragments.CreateExtraIdentityDialogFragment;
import com.dhpcs.liquidity.fragments.ErrorResponseDialogFragment;
import com.dhpcs.liquidity.fragments.IdentitiesFragment;
import com.dhpcs.liquidity.fragments.MonopolyGameHolderFragment;
import com.dhpcs.liquidity.fragments.PlayersFragment;
import com.dhpcs.liquidity.fragments.TransferToPlayerDialogFragment;
import com.dhpcs.liquidity.models.ErrorResponse;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.models.ZoneId;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;

import scala.Tuple2;

public class MonopolyGameActivity extends AppCompatActivity
        implements ChangeGameNameDialogFragment.Listener,
        CreateExtraIdentityDialogFragment.Listener,
        IdentitiesFragment.Listener,
        MonopolyGame.Listener,
        PlayersFragment.Listener,
        TransferToPlayerDialogFragment.Listener {

    public static final String EXTRA_GAME_ID = "game_id";
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monopoly_game);

        FragmentManager fragmentManager = getFragmentManager();
        monopolyGameHolderFragment = (MonopolyGameHolderFragment) fragmentManager
                .findFragmentByTag("monopoly_game_holder");

        if (monopolyGameHolderFragment == null) {

            MonopolyGame monopolyGame = new MonopolyGame(this);

            if (getIntent().getExtras() != null
                    && getIntent().getExtras().containsKey(EXTRA_ZONE_ID)) {

                monopolyGame.setZoneId(
                        (ZoneId) getIntent().getExtras().getSerializable(EXTRA_ZONE_ID)
                );

                if (getIntent().getExtras().containsKey(EXTRA_GAME_ID)) {

                    monopolyGame.setGameId(
                            getIntent().getExtras().getLong(EXTRA_GAME_ID)
                    );

                }

            }

            monopolyGameHolderFragment = new MonopolyGameHolderFragment(monopolyGame);

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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_monopoly_game, menu);
        return true;
    }

    @Override
    public void onErrorResponse(ErrorResponse errorResponse) {
        ErrorResponseDialogFragment.newInstance(errorResponse).show(
                getFragmentManager(),
                "error_dialog_fragment"
        );
    }

    @Override
    public void onGameNameEntered(String name) {
        monopolyGameHolderFragment.getMonopolyGame().setGameName(name);
    }

    @Override
    public void onIdentitiesChanged(scala.collection.immutable
                                            .Map<MemberId, IdentityWithBalance>
                                            identities) {
        identitiesFragment.onIdentitiesChanged(identities);
        monopolyGameHolderFragment.getMonopolyGame().setSelectedIdentity(
                identitiesFragment.getIdentityId(identitiesFragment.getSelectedIdentityPage())
        );
    }

    @Override
    public void onIdentityNameEntered(String name) {
        monopolyGameHolderFragment.getMonopolyGame().createIdentity(name);
    }

    @Override
    public void onIdentityPageSelected(int page) {
        monopolyGameHolderFragment.getMonopolyGame().setSelectedIdentity(
                identitiesFragment.getIdentityId(page)
        );
    }

    @Override
    public void onJoined(ZoneId zoneId) {
        this.zoneId = zoneId;
        supportInvalidateOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add_players:
                AddPlayersDialogFragment.newInstance(
                        zoneId
                ).show(
                        getFragmentManager(),
                        "add_players_dialog_fragment"
                );
                return true;
            case R.id.action_change_game_name:
                ChangeGameNameDialogFragment.newInstance(
                        monopolyGameHolderFragment.getMonopolyGame().getGameName()
                ).show(
                        getFragmentManager(),
                        "change_game_name_dialog_fragment"
                );
                return true;
            case R.id.action_create_extra_identity:
                CreateExtraIdentityDialogFragment.newInstance().show(
                        getFragmentManager(),
                        "create_extra_identity_dialog_fragment"
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
    public void onPlayerClicked(MemberId playerId) {
        MemberId identityId = identitiesFragment.getIdentityId(
                identitiesFragment.getSelectedIdentityPage()
        );
        if (identityId != null) {
            TransferToPlayerDialogFragment.newInstance(identityId, playerId)
                    .show(
                            getFragmentManager(),
                            "transfer_to_player_dialog_fragment"
                    );
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_add_players).setVisible(zoneId != null);
        menu.findItem(R.id.action_change_game_name).setVisible(zoneId != null);
        menu.findItem(R.id.action_create_extra_identity).setVisible(zoneId != null);
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
