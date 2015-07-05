package com.dhpcs.liquidity.activities;

import android.app.DialogFragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.dhpcs.liquidity.ClientKey;
import com.dhpcs.liquidity.MonopolyGame;
import com.dhpcs.liquidity.MonopolyGame.Identity;
import com.dhpcs.liquidity.MonopolyGame.IdentityWithBalance;
import com.dhpcs.liquidity.MonopolyGame.Player;
import com.dhpcs.liquidity.MonopolyGame.PlayerWithBalanceAndConnectionState;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.fragments.AddPlayersDialogFragment;
import com.dhpcs.liquidity.fragments.ChangeGameNameDialogFragment;
import com.dhpcs.liquidity.fragments.ChangeIdentityNameDialogFragment;
import com.dhpcs.liquidity.fragments.CreateExtraIdentityDialogFragment;
import com.dhpcs.liquidity.fragments.ErrorResponseDialogFragment;
import com.dhpcs.liquidity.fragments.IdentitiesFragment;
import com.dhpcs.liquidity.fragments.MonopolyGameHolderFragment;
import com.dhpcs.liquidity.fragments.PlayersFragment;
import com.dhpcs.liquidity.fragments.ReceiveIdentityDialogFragment;
import com.dhpcs.liquidity.fragments.TransferIdentityDialogFragment;
import com.dhpcs.liquidity.fragments.TransferToPlayerDialogFragment;
import com.dhpcs.liquidity.models.ErrorResponse;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.models.PublicKey;
import com.dhpcs.liquidity.models.ZoneId;
import com.google.common.io.BaseEncoding;
import com.google.zxing.Result;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;

import scala.Option;
import scala.Tuple2;
import scala.util.Either;

public class MonopolyGameActivity extends AppCompatActivity
        implements ChangeGameNameDialogFragment.Listener,
        ChangeIdentityNameDialogFragment.Listener,
        CreateExtraIdentityDialogFragment.Listener,
        IdentitiesFragment.Listener,
        MonopolyGame.Listener,
        PlayersFragment.Listener,
        TransferIdentityDialogFragment.Listener,
        TransferToPlayerDialogFragment.Listener {

    public static final String EXTRA_GAME_ID = "game_id";
    public static final String EXTRA_ZONE_ID = "zone_id";

    public static String formatBalance(
            Tuple2<scala.math.BigDecimal, Option<Either<String, Currency>>> balanceWithCurrency) {

        BigDecimal balance = balanceWithCurrency._1().bigDecimal();

        String balanceString;
        if (!balanceWithCurrency._2().isDefined()) {
            balanceString = NumberFormat.getNumberInstance().format(balance);
        } else {

            Either<String, Currency> currency = balanceWithCurrency._2().get();
            if (currency.isLeft()) {
                String currencyCode = currency.left().get();
                balanceString = currencyCode + " "
                        + NumberFormat.getNumberInstance().format(balance);
            } else {
                NumberFormat currencyFormat = NumberFormat.getCurrencyInstance();
                currencyFormat.setCurrency(currency.right().get());
                balanceString = currencyFormat.format(balance);
            }
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
    public void onIdentitiesChanged(scala.collection.immutable.Map<MemberId, IdentityWithBalance>
                                            identities) {
        identitiesFragment.onIdentitiesChanged(identities);
        IdentityWithBalance selectedIdentity = identitiesFragment.getIdentity(
                identitiesFragment.getSelectedPage()
        );
        monopolyGameHolderFragment.getMonopolyGame().setSelectedIdentity(
                selectedIdentity == null ? null : selectedIdentity.memberId()
        );
    }

    @Override
    public void onIdentityCreated(IdentityWithBalance identity) {
        identitiesFragment.setSelectedPage(identitiesFragment.getPage(identity));
    }

    @Override
    public void onIdentityNameEntered(String name) {
        monopolyGameHolderFragment.getMonopolyGame().createIdentity(name);
    }

    @Override
    public void onIdentityNameEntered(MemberId identityId, String name) {
        monopolyGameHolderFragment.getMonopolyGame().setIdentityName(identityId, name);
    }

    @Override
    public void onIdentityPageSelected(int page) {
        IdentityWithBalance selectedIdentity = identitiesFragment.getIdentity(page);
        monopolyGameHolderFragment.getMonopolyGame().setSelectedIdentity(
                selectedIdentity == null ? null : selectedIdentity.memberId()
        );
    }

    @Override
    public void onIdentityReceived(IdentityWithBalance identity) {
        DialogFragment receiveIdentityDialogFragment = (DialogFragment) getFragmentManager()
                .findFragmentByTag("receive_identity_dialog_fragment");
        if (receiveIdentityDialogFragment != null) {
            receiveIdentityDialogFragment.dismiss();
        }
        identitiesFragment.setSelectedPage(identitiesFragment.getPage(identity));
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
            case R.id.action_change_identity_name:
                IdentityWithBalance selectedIdentity = identitiesFragment.getIdentity(
                        identitiesFragment.getSelectedPage()
                );
                if (selectedIdentity != null) {
                    ChangeIdentityNameDialogFragment.newInstance(
                            selectedIdentity.memberId(),
                            selectedIdentity.member().name()
                    ).show(
                            getFragmentManager(),
                            "change_identity_name_dialog_fragment"
                    );
                }
                return true;
            case R.id.action_create_extra_identity:
                CreateExtraIdentityDialogFragment.newInstance().show(
                        getFragmentManager(),
                        "create_extra_identity_dialog_fragment"
                );
                return true;
            case R.id.action_receive_identity:
                ReceiveIdentityDialogFragment.newInstance(
                        ClientKey.getInstance(this).getPublicKey()
                ).show(
                        getFragmentManager(),
                        "receive_identity_dialog_fragment"
                );
                return true;
            case R.id.action_transfer_identity:
                TransferIdentityDialogFragment.newInstance().show(
                        getFragmentManager(),
                        "transfer_identity_dialog_fragment"
                );
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPlayersChanged(scala.collection.Iterable<PlayerWithBalanceAndConnectionState>
                                         players) {
        playersFragment.onPlayersChanged(players);
    }

    @Override
    public void onPlayerClicked(PlayerWithBalanceAndConnectionState player) {
        IdentityWithBalance identity = identitiesFragment.getIdentity(
                identitiesFragment.getSelectedPage()
        );
        if (identity != null) {
            TransferToPlayerDialogFragment.newInstance(
                    identity,
                    identity,
                    player
            ).show(
                    getFragmentManager(),
                    "transfer_to_player_dialog_fragment"
            );
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_add_players).setVisible(zoneId != null);
        menu.findItem(R.id.action_change_game_name).setVisible(zoneId != null);
        menu.findItem(R.id.action_change_identity_name).setVisible(
                zoneId != null &&
                        identitiesFragment.getIdentity(identitiesFragment.getSelectedPage()) != null
        );
        menu.findItem(R.id.action_create_extra_identity).setVisible(zoneId != null);
        menu.findItem(R.id.action_receive_identity).setVisible(zoneId != null &&
                        identitiesFragment.getIdentity(identitiesFragment.getSelectedPage()) != null
        );
        menu.findItem(R.id.action_transfer_identity).setVisible(zoneId != null);
        return true;
    }

    @Override
    public void onPublicKeyScanned(Result rawResult) {
        IdentityWithBalance identity = identitiesFragment.getIdentity(
                identitiesFragment.getSelectedPage()
        );
        if (identity != null) {
            PublicKey publicKey = new PublicKey(
                    BaseEncoding.base64().decode(
                            rawResult.getText()
                    )
            );
            if (!monopolyGameHolderFragment.getMonopolyGame()
                    .isPublicKeyConnectedAndImplicitlyValid(publicKey)) {
                // TODO
                Toast.makeText(
                        this,
                        R.string.qr_code_is_not_a_public_key,
                        Toast.LENGTH_LONG
                ).show();
            } else {
                monopolyGameHolderFragment.getMonopolyGame().transfer(
                        identity.memberId(),
                        publicKey
                );
            }
        }
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
    public void onTransferValueEntered(Identity actingAs,
                                       Player from,
                                       Player to,
                                       BigDecimal transferValue) {
        monopolyGameHolderFragment.getMonopolyGame().transfer(
                actingAs,
                from,
                to,
                scala.math.BigDecimal.javaBigDecimal2bigDecimal(transferValue)
        );
    }

}
