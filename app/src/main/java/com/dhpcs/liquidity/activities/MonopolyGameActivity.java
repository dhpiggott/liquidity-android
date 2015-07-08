package com.dhpcs.liquidity.activities;

import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.dhpcs.liquidity.ClientKey;
import com.dhpcs.liquidity.MONOPOLY$;
import com.dhpcs.liquidity.MonopolyGame;
import com.dhpcs.liquidity.MonopolyGame.Identity;
import com.dhpcs.liquidity.MonopolyGame.IdentityWithBalance;
import com.dhpcs.liquidity.MonopolyGame.Player;
import com.dhpcs.liquidity.MonopolyGame.PlayerWithBalanceAndConnectionState;
import com.dhpcs.liquidity.MonopolyGame.TransferWithCurrency;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.fragments.AddPlayersDialogFragment;
import com.dhpcs.liquidity.fragments.ConfirmIdentityDeletionDialogFragment;
import com.dhpcs.liquidity.fragments.CreateExtraIdentityDialogFragment;
import com.dhpcs.liquidity.fragments.EnterGameNameDialogFragment;
import com.dhpcs.liquidity.fragments.EnterIdentityNameDialogFragment;
import com.dhpcs.liquidity.fragments.ErrorResponseDialogFragment;
import com.dhpcs.liquidity.fragments.IdentitiesFragment;
import com.dhpcs.liquidity.fragments.MonopolyGameHolderFragment;
import com.dhpcs.liquidity.fragments.PlayersFragment;
import com.dhpcs.liquidity.fragments.ReceiveIdentityDialogFragment;
import com.dhpcs.liquidity.fragments.RestoreIdentityDialogFragment;
import com.dhpcs.liquidity.fragments.TransferIdentityDialogFragment;
import com.dhpcs.liquidity.fragments.TransferToPlayerDialogFragment;
import com.dhpcs.liquidity.fragments.TransfersDialogFragment;
import com.dhpcs.liquidity.fragments.TransfersFragment;
import com.dhpcs.liquidity.models.Account;
import com.dhpcs.liquidity.models.AccountId;
import com.dhpcs.liquidity.models.ErrorResponse;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.models.PublicKey;
import com.dhpcs.liquidity.models.ZoneId;
import com.google.common.io.BaseEncoding;
import com.google.zxing.Result;

import java.math.BigDecimal;
import java.text.Collator;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;

import scala.Option;
import scala.Tuple2;
import scala.collection.JavaConversions;
import scala.util.Either;

public class MonopolyGameActivity extends AppCompatActivity
        implements EnterGameNameDialogFragment.Listener,
        EnterIdentityNameDialogFragment.Listener,
        ConfirmIdentityDeletionDialogFragment.Listener,
        CreateExtraIdentityDialogFragment.Listener,
        IdentitiesFragment.Listener,
        MonopolyGame.Listener,
        PlayersFragment.Listener,
        RestoreIdentityDialogFragment.Listener,
        TransferIdentityDialogFragment.Listener,
        TransferToPlayerDialogFragment.Listener {

    public static final String EXTRA_GAME_ID = "game_id";
    public static final String EXTRA_GAME_NAME = "game_name";
    public static final String EXTRA_ZONE_ID = "zone_id";

    public static final Comparator<IdentityWithBalance> identityComparator =
            new Comparator<IdentityWithBalance>() {

                private final Collator collator = Collator.getInstance();

                @Override
                public int compare(IdentityWithBalance lhs, IdentityWithBalance rhs) {
                    return collator.compare(lhs.member().name(), rhs.member().name());
                }

            };

    public static final Comparator<PlayerWithBalanceAndConnectionState> playerComparator =
            new Comparator<PlayerWithBalanceAndConnectionState>() {

                private final Collator collator = Collator.getInstance();

                @Override
                public int compare(PlayerWithBalanceAndConnectionState lhs,
                                   PlayerWithBalanceAndConnectionState rhs) {
                    return collator.compare(lhs.member().name(), rhs.member().name());
                }

            };

    public static final Comparator<TransferWithCurrency> transferComparator =
            new Comparator<TransferWithCurrency>() {

                @Override
                public int compare(TransferWithCurrency lhs, TransferWithCurrency rhs) {
                    return -1 *
                            Long.compare(lhs.transaction().created(), rhs.transaction().created());
                }

            };

    public static String formatCurrency(scala.math.BigDecimal value,
                                        Option<Either<String, Currency>> currency) {

        BigDecimal v = value.bigDecimal();

        String valueString;
        if (!currency.isDefined()) {
            valueString = NumberFormat.getNumberInstance().format(v);
        } else {

            Either<String, Currency> c = currency.get();
            if (c.isLeft()) {
                String currencyCode = c.left().get();
                // TODO
                valueString = currencyCode + " "
                        + NumberFormat.getNumberInstance().format(v);
            } else {
                NumberFormat currencyFormat = NumberFormat.getCurrencyInstance();
                currencyFormat.setCurrency(c.right().get());
                valueString = currencyFormat.format(v);
            }
        }

        return valueString;
    }

    public static String formatMemberOrAccount(Context context,
                                               Either<Tuple2<AccountId, Account>, Player>
                                                       eitherAccountTupleOrMember) {
        String result;
        if (eitherAccountTupleOrMember.isLeft()) {
            AccountId accountId = eitherAccountTupleOrMember.left().get()._1();
            Account account = eitherAccountTupleOrMember.left().get()._2();
            result = context.getString(
                    R.string.non_player_transfer_location_format_string,
                    accountId.id().toString(),
                    account.name()
            );
        } else {
            result = eitherAccountTupleOrMember.right().get().member().name();
        }
        return result;
    }

    private MonopolyGameHolderFragment monopolyGameHolderFragment;
    private IdentitiesFragment identitiesFragment;
    private PlayersFragment playersFragment;
    private TransfersFragment transfersFragment;

    private ZoneId zoneId;
    private MemberId selectedIdentityId;
    private scala.collection.Iterable<IdentityWithBalance> hiddenIdentities;
    private scala.collection.Iterable<PlayerWithBalanceAndConnectionState> players;
    private scala.collection.Iterable<TransferWithCurrency> transfers;

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

                if (getIntent().getExtras().containsKey(EXTRA_GAME_NAME)) {

                    setTitle(
                            getIntent().getExtras().getString(EXTRA_GAME_NAME)
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
        transfersFragment = (TransfersFragment)
                fragmentManager.findFragmentById(R.id.fragment_transfers);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_monopoly_game, menu);
        return true;
    }

    @Override
    public void onErrorResponse(ErrorResponse errorResponse) {
        ErrorResponseDialogFragment.newInstance(errorResponse)
                .show(
                        getFragmentManager(),
                        "error_dialog_fragment"
                );
    }

    @Override
    public void onGameNameChanged(String name) {
        setTitle(name);
    }

    @Override
    public void onGameNameEntered(String name) {
        monopolyGameHolderFragment.getMonopolyGame().setGameName(name);
    }

    @Override
    public void onHiddenIdentitiesChanged(scala.collection.Iterable<IdentityWithBalance>
                                                  hiddenIdentities) {
        this.hiddenIdentities = hiddenIdentities;
    }

    @Override
    public void onIdentitiesChanged(scala.collection.immutable.Map<MemberId, IdentityWithBalance>
                                            identities) {
        identitiesFragment.onIdentitiesChanged(identities);
        Identity identity = identitiesFragment.getIdentity(identitiesFragment.getSelectedPage());
        this.selectedIdentityId = identity == null ? null : identity.memberId();
        if (players != null) {
            playersFragment.onPlayersChanged(selectedIdentityId, players);
        }
    }

    @Override
    public void onIdentityDeleteConfirmed(Identity identity) {
        monopolyGameHolderFragment.getMonopolyGame().deleteIdentity(identity);
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
    public void onIdentityNameEntered(Identity identity, String name) {
        if (identity == null) {
            monopolyGameHolderFragment.getMonopolyGame().createIdentity(name);
        } else {
            monopolyGameHolderFragment.getMonopolyGame().setIdentityName(identity, name);
        }
    }

    @Override
    public void onIdentityPageSelected(int page) {
        Identity identity = identitiesFragment.getIdentity(page);
        this.selectedIdentityId = identity == null ? null : identity.memberId();
        if (players != null) {
            playersFragment.onPlayersChanged(selectedIdentityId, players);
        }
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
    public void onIdentityRequired() {
        EnterIdentityNameDialogFragment.newInstance(null)
                .show(
                        getFragmentManager(),
                        "enter_identity_name_dialog_fragment"
                );
    }

    @Override
    public void onIdentityRestored(IdentityWithBalance identity) {
        identitiesFragment.setSelectedPage(identitiesFragment.getPage(identity));
    }

    @Override
    public void onIdentityRestorationRequested(Identity identity) {
        monopolyGameHolderFragment.getMonopolyGame().restoreIdentity(identity);
    }

    @Override
    public void onJoined(ZoneId zoneId) {
        this.zoneId = zoneId;
        supportInvalidateOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Identity identity;
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpTo(
                        this,
                        NavUtils.getParentActivityIntent(this)
                                .putExtra(
                                        GamesActivity.GAME_TYPE,
                                        MONOPOLY$.MODULE$
                                )
                );
                return true;
            case R.id.action_add_players:
                AddPlayersDialogFragment.newInstance(zoneId)
                        .show(
                                getFragmentManager(),
                                "add_players_dialog_fragment"
                        );
                return true;
            case R.id.action_change_game_name:
                EnterGameNameDialogFragment.newInstance(getTitle().toString())
                        .show(
                                getFragmentManager(),
                                "enter_game_name_dialog_fragment"
                        );
                return true;
            case R.id.action_change_identity_name:
                identity = identitiesFragment.getIdentity(identitiesFragment.getSelectedPage());
                if (identity != null) {
                    EnterIdentityNameDialogFragment.newInstance(identity)
                            .show(
                                    getFragmentManager(),
                                    "enter_identity_name_dialog_fragment"
                            );
                }
                return true;
            case R.id.action_create_extra_identity:
                CreateExtraIdentityDialogFragment.newInstance()
                        .show(
                                getFragmentManager(),
                                "create_extra_identity_dialog_fragment"
                        );
                return true;
            case R.id.action_delete_identity:
                identity = identitiesFragment.getIdentity(identitiesFragment.getSelectedPage());
                if (identity != null) {
                    ConfirmIdentityDeletionDialogFragment.newInstance(identity)
                            .show(
                                    getFragmentManager(),
                                    "confirm_identity_deletion_dialog_fragment"
                            );
                }
                return true;
            case R.id.action_restore_identity:
                RestoreIdentityDialogFragment.newInstance(
                        new ArrayList<>(
                                JavaConversions.bufferAsJavaList(
                                        hiddenIdentities.<IdentityWithBalance>toBuffer()
                                )
                        )
                ).show(
                        getFragmentManager(),
                        "restore_identity_dialog_fragment"
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
                TransferIdentityDialogFragment.newInstance()
                        .show(
                                getFragmentManager(),
                                "transfer_identity_dialog_fragment"
                        );
                return true;
            case R.id.action_view_transfers:
                identity = identitiesFragment.getIdentity(identitiesFragment.getSelectedPage());
                if (identity != null) {
                    TransfersDialogFragment.newInstance(
                            identity,
                            new ArrayList<>(
                                    JavaConversions.bufferAsJavaList(
                                            transfers.<TransferWithCurrency>toBuffer()
                                    )
                            )
                    )
                            .show(
                                    getFragmentManager(),
                                    "transfers_dialog_fragment"
                            );
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPlayersChanged(scala.collection.Iterable<PlayerWithBalanceAndConnectionState>
                                         players) {
        this.players = players;
        playersFragment.onPlayersChanged(selectedIdentityId, players);
    }

    @Override
    public void onPlayerClicked(Player player) {
        Identity identity = identitiesFragment.getIdentity(identitiesFragment.getSelectedPage());
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
    public void onPlayerLongClicked(Player player) {
        // TODO: Show menu
        TransfersDialogFragment.newInstance(
                player,
                new ArrayList<>(
                        JavaConversions.bufferAsJavaList(
                                transfers.<TransferWithCurrency>toBuffer()
                        )
                )
        )
                .show(
                        getFragmentManager(),
                        "transfers_dialog_fragment"
                );
    }

    // TODO: Set strings based on current identity etc.
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Identity identity = identitiesFragment.getIdentity(identitiesFragment.getSelectedPage());
        menu.findItem(R.id.action_add_players).setVisible(zoneId != null);
        menu.findItem(R.id.action_change_game_name).setVisible(zoneId != null);
        menu.findItem(R.id.action_change_identity_name).setVisible(
                zoneId != null && identity != null
        );
        menu.findItem(R.id.action_create_extra_identity).setVisible(zoneId != null);
        menu.findItem(R.id.action_delete_identity).setVisible(zoneId != null && identity != null);
        menu.findItem(R.id.action_restore_identity).setVisible(
                zoneId != null && hiddenIdentities.nonEmpty()
        );
        menu.findItem(R.id.action_receive_identity).setVisible(zoneId != null && identity != null);
        menu.findItem(R.id.action_transfer_identity).setVisible(zoneId != null);
        menu.findItem(R.id.action_view_transfers).setVisible(zoneId != null);
        return true;
    }

    @Override
    public void onPublicKeyScanned(Result rawResult) {
        Identity identity = identitiesFragment.getIdentity(identitiesFragment.getSelectedPage());
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
    public void onTransfersChanged(scala.collection.Iterable<TransferWithCurrency> transfers) {
        this.transfers = transfers;
        transfersFragment.onTransfersChanged(transfers);
        TransfersDialogFragment transfersDialogFragment = (TransfersDialogFragment)
                getFragmentManager().findFragmentByTag("transfers_dialog_fragment");
        if (transfersDialogFragment != null) {
            transfersDialogFragment.onTransfersChanged(transfers);
        }
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
