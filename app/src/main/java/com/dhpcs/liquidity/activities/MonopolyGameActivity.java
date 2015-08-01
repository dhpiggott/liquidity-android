package com.dhpcs.liquidity.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dhpcs.liquidity.ClientKey;
import com.dhpcs.liquidity.MonopolyGame;
import com.dhpcs.liquidity.MonopolyGame.Identity;
import com.dhpcs.liquidity.MonopolyGame.IdentityWithBalance;
import com.dhpcs.liquidity.MonopolyGame.Player;
import com.dhpcs.liquidity.MonopolyGame.PlayerWithBalanceAndConnectionState;
import com.dhpcs.liquidity.MonopolyGame.TransferWithCurrency;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.fragments.ConfirmIdentityDeletionDialogFragment;
import com.dhpcs.liquidity.fragments.CreateIdentityDialogFragment;
import com.dhpcs.liquidity.fragments.EnterGameNameDialogFragment;
import com.dhpcs.liquidity.fragments.EnterIdentityNameDialogFragment;
import com.dhpcs.liquidity.fragments.ErrorResponseDialogFragment;
import com.dhpcs.liquidity.fragments.IdentitiesFragment;
import com.dhpcs.liquidity.fragments.LastTransferFragment;
import com.dhpcs.liquidity.fragments.PlayersFragment;
import com.dhpcs.liquidity.fragments.PlayersTransfersFragment;
import com.dhpcs.liquidity.fragments.RestoreIdentityDialogFragment;
import com.dhpcs.liquidity.fragments.TransferToPlayerDialogFragment;
import com.dhpcs.liquidity.models.Account;
import com.dhpcs.liquidity.models.AccountId;
import com.dhpcs.liquidity.models.ErrorResponse;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.models.PublicKey;
import com.dhpcs.liquidity.models.TransactionId;
import com.dhpcs.liquidity.models.ZoneId;
import com.google.common.io.BaseEncoding;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState;

import java.math.BigDecimal;
import java.text.Collator;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;

import scala.Option;
import scala.Tuple2;
import scala.collection.JavaConversions;
import scala.util.Either;

public class MonopolyGameActivity extends AppCompatActivity
        implements EnterGameNameDialogFragment.Listener,
        EnterIdentityNameDialogFragment.Listener,
        ConfirmIdentityDeletionDialogFragment.Listener,
        CreateIdentityDialogFragment.Listener,
        IdentitiesFragment.Listener,
        MonopolyGame.Listener,
        PlayersFragment.Listener,
        RestoreIdentityDialogFragment.Listener,
        TransferToPlayerDialogFragment.Listener {

    public static final String EXTRA_GAME_ID = "game_id";
    public static final String EXTRA_GAME_NAME = "game_name";
    public static final String EXTRA_ZONE_ID = "zone_id";

    private static final int REQUEST_CODE_RECEIVE_IDENTITY = 0;

    public static final Comparator<Player> playerComparator = new Comparator<Player>() {

        private final Collator collator = Collator.getInstance();

        @Override
        public int compare(Player lhs,
                           Player rhs) {
            return collator.compare(lhs.member().name(), rhs.member().name());
        }

    };

    public static String formatCurrency(Context context,
                                        Option<Either<String, Currency>> currency) {

        String result;
        if (!currency.isDefined()) {
            result = context.getString(R.string.currency_none);
        } else {

            Either<String, Currency> c = currency.get();
            if (c.isLeft()) {
                result = context.getString(R.string.currency_code_format_string, c.left().get());
            } else {
                result = c.right().get().getSymbol();
            }
        }

        return result;
    }

    public static String formatCurrencyValue(Context context,
                                             Option<Either<String, Currency>> currency,
                                             scala.math.BigDecimal value) {
        return formatCurrencyValue(context, currency, value.bigDecimal());
    }

    public static String formatCurrencyValue(Context context,
                                             Option<Either<String, Currency>> currency,
                                             BigDecimal value) {

        int scaleAmount;
        BigDecimal scaledValue;
        String multiplier;
        if ((scaledValue = value.scaleByPowerOfTen(scaleAmount = -6))
                .abs().compareTo(BigDecimal.ONE) >= 0) {
            multiplier = context.getString(R.string.value_multiplier_million_with_leading_space);
        } else if ((scaledValue = value.scaleByPowerOfTen(scaleAmount = -3))
                .abs().compareTo(BigDecimal.ONE) >= 0) {
            multiplier = context.getString(R.string.value_multiplier_thousand_with_leading_space);
        } else {
            scaleAmount = 0;
            scaledValue = value;
            multiplier = context.getString(R.string.value_multiplier_none);
        }

        int maximumFractionDigits;
        int minimumFractionDigits;
        if (scaleAmount == 0) {
            if (scaledValue.scale() == 0) {
                maximumFractionDigits = 0;
                minimumFractionDigits = 0;
            } else {
                maximumFractionDigits = scaledValue.scale();
                minimumFractionDigits = 2;
            }
        } else {
            maximumFractionDigits = scaledValue.scale();
            minimumFractionDigits = 0;
        }

        NumberFormat numberFormat = NumberFormat.getNumberInstance();
        numberFormat.setMaximumFractionDigits(maximumFractionDigits);
        numberFormat.setMinimumFractionDigits(minimumFractionDigits);

        return context.getString(
                R.string.currency_value_format_string,
                formatCurrency(context, currency),
                numberFormat.format(scaledValue),
                multiplier
        );
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

    public static boolean isIdentityNameValid(Context context, CharSequence identityName) {
        return !TextUtils.isEmpty(identityName)
                && !identityName.toString().equals(context.getString(R.string.bank_member_name));
    }

    private MonopolyGame monopolyGame;

    private ProgressBar progressBarState;
    private TextView textViewState;
    private Button buttonReconnect;
    private SlidingUpPanelLayout slidingUpPanelLayout;

    private IdentitiesFragment identitiesFragment;
    private PlayersFragment playersFragment;
    private LastTransferFragment lastTransferFragment;
    private PlayersTransfersFragment playersTransfersFragment;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            String contents = result.getContents();
            if (contents != null) {
                if (monopolyGame.getJoinState() == MonopolyGame.JOINED$.MODULE$) {
                    Identity identity = identitiesFragment.getIdentity(
                            identitiesFragment.getSelectedPage()
                    );
                    try {
                        PublicKey publicKey = new PublicKey(
                                BaseEncoding.base64().decode(
                                        contents
                                )
                        );
                        if (!monopolyGame.isPublicKeyConnectedAndImplicitlyValid(publicKey)) {
                            throw new IllegalArgumentException();
                        }
                        monopolyGame.transferIdentity(identity.memberId(), publicKey);
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(
                                this,
                                getString(
                                        R.string.transfer_identity_invalid_code_format_string,
                                        monopolyGame.getGameName()
                                ),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (slidingUpPanelLayout.getPanelState() == PanelState.EXPANDED
                || slidingUpPanelLayout.getPanelState() == PanelState.ANCHORED) {
            slidingUpPanelLayout.setPanelState(PanelState.COLLAPSED);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monopoly_game);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        progressBarState = (ProgressBar) findViewById(R.id.progressbar_state);
        textViewState = (TextView) findViewById(R.id.textview_state);
        buttonReconnect = (Button) findViewById(R.id.button_reconnect);
        slidingUpPanelLayout = (SlidingUpPanelLayout) findViewById(R.id.slidinguppanellayout);

        buttonReconnect.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                monopolyGame.connectCreateAndOrJoinZone();
            }

        });

        slidingUpPanelLayout.setPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {

            @Override
            public void onPanelSlide(View view, float v) {
            }

            @Override
            public void onPanelCollapsed(View view) {
                setTitle(monopolyGame.getJoinState() == MonopolyGame.JOINED$.MODULE$
                        ?
                        monopolyGame.getGameName()
                        :
                        getString(R.string.title_activity_monopoly_game));
            }

            @Override
            public void onPanelExpanded(View view) {
                setTitle(R.string.transfers);
            }

            @Override
            public void onPanelAnchored(View view) {
            }

            @Override
            public void onPanelHidden(View view) {
            }

        });

        identitiesFragment = (IdentitiesFragment)
                getFragmentManager().findFragmentById(R.id.fragment_identities);
        playersFragment = (PlayersFragment)
                getFragmentManager().findFragmentById(R.id.fragment_players);
        lastTransferFragment = (LastTransferFragment)
                getFragmentManager().findFragmentById(R.id.fragment_last_transfer);
        playersTransfersFragment = (PlayersTransfersFragment)
                getFragmentManager().findFragmentById(R.id.fragment_players_transfers);

        monopolyGame = (MonopolyGame) getLastCustomNonConfigurationInstance();

        if (monopolyGame == null) {

            if (getIntent().getExtras() == null
                    || !getIntent().getExtras().containsKey(EXTRA_ZONE_ID)) {
                monopolyGame = new MonopolyGame(this);
            } else {
                ZoneId zoneId = (ZoneId) getIntent().getExtras().getSerializable(EXTRA_ZONE_ID);
                if (!getIntent().getExtras().containsKey(EXTRA_GAME_ID)) {
                    monopolyGame = new MonopolyGame(
                            this,
                            zoneId
                    );
                } else {
                    monopolyGame = new MonopolyGame(
                            this,
                            zoneId,
                            getIntent().getExtras().getLong(EXTRA_GAME_ID)
                    );
                }
                if (getIntent().getExtras().containsKey(EXTRA_GAME_NAME)) {
                    setTitle(getIntent().getExtras().getString(EXTRA_GAME_NAME));
                }
            }

            monopolyGame.connectCreateAndOrJoinZone();

        }

        monopolyGame.setListener(this);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_monopoly_game, menu);
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        monopolyGame.setListener(null);
        if (isFinishing()) {
            monopolyGame.quitAndOrDisconnect();
        }
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
        monopolyGame.setGameName(name);
    }

    @Override
    public void onIdentitiesUpdated(
            scala.collection.immutable.Map<MemberId, IdentityWithBalance> identities) {
        identitiesFragment.onIdentitiesUpdated(identities);
        playersFragment.onSelectedIdentityChanged(
                identitiesFragment.getIdentity(identitiesFragment.getSelectedPage())
        );
        TransferToPlayerDialogFragment transferToPlayerDialogFragment =
                (TransferToPlayerDialogFragment) getFragmentManager()
                        .findFragmentByTag("transfer_to_player_dialog_fragment");
        if (transferToPlayerDialogFragment != null) {
            transferToPlayerDialogFragment.onIdentitiesUpdated(identities);
        }
    }

    @Override
    public void onIdentityDeleteConfirmed(Identity identity) {
        monopolyGame.deleteIdentity(identity);
    }

    @Override
    public void onIdentityCreated(IdentityWithBalance identity) {
        identitiesFragment.setSelectedPage(identitiesFragment.getPage(identity));
    }

    @Override
    public void onIdentityNameEntered(boolean isInitialPrompt, String name) {
        monopolyGame.createIdentity(isInitialPrompt, name);
    }

    @Override
    public void onIdentityNameEntered(Identity identity, String name) {
        monopolyGame.setIdentityName(identity, name);
    }

    @Override
    public void onIdentityPageSelected(int page) {
        playersFragment.onSelectedIdentityChanged(identitiesFragment.getIdentity(page));
    }

    @Override
    public void onIdentityReceived(IdentityWithBalance identity) {
        finishActivity(REQUEST_CODE_RECEIVE_IDENTITY);
        identitiesFragment.setSelectedPage(identitiesFragment.getPage(identity));
    }

    @Override
    public void onIdentityRequired() {
        CreateIdentityDialogFragment.newInstance(true)
                .show(
                        getFragmentManager(),
                        "create_identity_dialog_fragment"
                );
    }

    @Override
    public void onIdentityRestored(IdentityWithBalance identity) {
        identitiesFragment.setSelectedPage(identitiesFragment.getPage(identity));
    }

    @Override
    public void onIdentityRestorationRequested(Identity identity) {
        monopolyGame.restoreIdentity(identity);
    }

    // TODO: Dismiss any open dialog fragments and child activities on disconnect
    @Override
    public void onJoinStateChanged(MonopolyGame.JoinState joinState) {
        if (joinState == MonopolyGame.DISCONNECTED$.MODULE$) {

            slidingUpPanelLayout.setVisibility(View.GONE);
            progressBarState.setVisibility(View.GONE);

            textViewState.setVisibility(View.VISIBLE);
            textViewState.setText(R.string.disconnected);
            buttonReconnect.setVisibility(View.VISIBLE);

        } else if (joinState == MonopolyGame.CONNECTING$.MODULE$) {

            slidingUpPanelLayout.setVisibility(View.GONE);
            buttonReconnect.setVisibility(View.GONE);

            progressBarState.setVisibility(View.VISIBLE);
            textViewState.setVisibility(View.VISIBLE);
            textViewState.setText(R.string.connecting);

        } else if (joinState == MonopolyGame.JOINING$.MODULE$) {

            slidingUpPanelLayout.setVisibility(View.GONE);
            buttonReconnect.setVisibility(View.GONE);

            progressBarState.setVisibility(View.VISIBLE);
            textViewState.setVisibility(View.VISIBLE);
            textViewState.setText(R.string.joining);

        } else if (joinState == MonopolyGame.JOINED$.MODULE$) {

            buttonReconnect.setVisibility(View.GONE);
            textViewState.setText(null);
            textViewState.setVisibility(View.GONE);
            progressBarState.setVisibility(View.GONE);

            slidingUpPanelLayout.setVisibility(View.VISIBLE);

        } else if (joinState == MonopolyGame.QUITTING$.MODULE$) {

            slidingUpPanelLayout.setVisibility(View.GONE);
            buttonReconnect.setVisibility(View.GONE);

            progressBarState.setVisibility(View.VISIBLE);
            textViewState.setVisibility(View.VISIBLE);
            textViewState.setText(R.string.quitting);

        } else if (joinState == MonopolyGame.DISCONNECTING$.MODULE$) {

            slidingUpPanelLayout.setVisibility(View.GONE);
            buttonReconnect.setVisibility(View.GONE);

            progressBarState.setVisibility(View.VISIBLE);
            textViewState.setVisibility(View.VISIBLE);
            textViewState.setText(R.string.disconnecting);

        }
        supportInvalidateOptionsMenu();
    }

    @Override
    public void onNoIdentitiesTextClicked() {
        CreateIdentityDialogFragment.newInstance(false)
                .show(
                        getFragmentManager(),
                        "create_identity_dialog_fragment"
                );
    }

    @Override
    public void onNoPlayersTextClicked() {
        startActivity(
                new Intent(
                        this,
                        AddPlayersActivity.class
                ).putExtra(
                        AddPlayersActivity.EXTRA_GAME_NAME,
                        monopolyGame.getGameName()
                ).putExtra(
                        AddPlayersActivity.EXTRA_ZONE_ID,
                        monopolyGame.getZoneId()
                )
        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        IdentityWithBalance identity;
        switch (item.getItemId()) {
            case android.R.id.home:
                if (slidingUpPanelLayout.getPanelState() == PanelState.EXPANDED
                        || slidingUpPanelLayout.getPanelState() == PanelState.ANCHORED) {
                    slidingUpPanelLayout.setPanelState(PanelState.COLLAPSED);
                    return true;
                } else {
                    return false;
                }
            case R.id.action_add_players:
                startActivity(
                        new Intent(
                                this,
                                AddPlayersActivity.class
                        ).putExtra(
                                AddPlayersActivity.EXTRA_GAME_NAME,
                                monopolyGame.getGameName()
                        ).putExtra(
                                AddPlayersActivity.EXTRA_ZONE_ID,
                                monopolyGame.getZoneId()
                        )
                );
                return true;
            case R.id.action_group_transfer:
                identity = identitiesFragment.getIdentity(identitiesFragment.getSelectedPage());
                if (identity != null) {
                    TransferToPlayerDialogFragment.newInstance(
                            monopolyGame.getIdentities(),
                            monopolyGame.getPlayers(),
                            monopolyGame.getCurrency(),
                            identity,
                            null
                    ).show(
                            getFragmentManager(),
                            "transfer_to_player_dialog_fragment"
                    );
                }
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
            case R.id.action_create_identity:
                CreateIdentityDialogFragment.newInstance(false)
                        .show(
                                getFragmentManager(),
                                "create_identity_dialog_fragment"
                        );
                return true;
            case R.id.action_restore_identity:
                RestoreIdentityDialogFragment.newInstance(monopolyGame.getHiddenIdentities())
                        .show(
                                getFragmentManager(),
                                "restore_identity_dialog_fragment"
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
            case R.id.action_receive_identity:
                startActivityForResult(
                        new Intent(
                                this,
                                ReceiveIdentityActivity.class
                        ).putExtra(
                                ReceiveIdentityActivity.EXTRA_PUBLIC_KEY,
                                ClientKey.getPublicKey(this)
                        ),
                        REQUEST_CODE_RECEIVE_IDENTITY
                );
                return true;
            case R.id.action_transfer_identity:
                new IntentIntegrator(MonopolyGameActivity.this)
                        .setCaptureActivity(TransferIdentityActivity.class)
                        .addExtra(
                                TransferIdentityActivity.EXTRA_IDENTITY_NAME,
                                identitiesFragment.getIdentity(identitiesFragment.getSelectedPage())
                                        .member().name()
                        )
                        .setDesiredBarcodeFormats(Collections.singleton("QR_CODE"))
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                        .initiateScan();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPlayerAdded(PlayerWithBalanceAndConnectionState addedPlayer) {
        playersFragment.onPlayerAdded(addedPlayer);
    }

    @Override
    public void onPlayerChanged(PlayerWithBalanceAndConnectionState changedPlayer) {
        playersFragment.onPlayerChanged(changedPlayer);
    }

    @Override
    public void onPlayersInitialized(
            scala.collection.Iterable<PlayerWithBalanceAndConnectionState> players) {
        playersFragment.onPlayersInitialized(players);
    }

    @Override
    public void onPlayerRemoved(PlayerWithBalanceAndConnectionState removedPlayer) {
        playersFragment.onPlayerRemoved(removedPlayer);
    }

    @Override
    public void onPlayersUpdated(
            scala.collection.immutable.Map<MemberId, PlayerWithBalanceAndConnectionState> players) {
        playersFragment.onPlayersUpdated(players);
        playersTransfersFragment.onPlayersUpdated(players);
    }

    @Override
    public void onPlayerClicked(Player player) {
        IdentityWithBalance identity = identitiesFragment.getIdentity(
                identitiesFragment.getSelectedPage()
        );
        if (identity != null) {
            TransferToPlayerDialogFragment.newInstance(
                    monopolyGame.getIdentities(),
                    monopolyGame.getPlayers(),
                    monopolyGame.getCurrency(),
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
        boolean isJoined = monopolyGame.getJoinState() == MonopolyGame.JOINED$.MODULE$;
        Identity identity = identitiesFragment.getIdentity(identitiesFragment.getSelectedPage());
        boolean isPanelCollapsed = slidingUpPanelLayout.getPanelState() == PanelState.COLLAPSED;
        menu.findItem(R.id.action_add_players).setVisible(
                isJoined
        );
        menu.findItem(R.id.action_group_transfer).setVisible(
                isJoined && identity != null && isPanelCollapsed
        );
        menu.findItem(R.id.action_change_game_name).setVisible(
                isJoined
        );
        menu.findItem(R.id.action_change_identity_name).setVisible(
                isJoined && identity != null && isPanelCollapsed && !identity.isBanker()
        );
        menu.findItem(R.id.action_create_identity).setVisible(
                isJoined && isPanelCollapsed
        );
        menu.findItem(R.id.action_restore_identity).setVisible(
                isJoined && isPanelCollapsed && monopolyGame.getHiddenIdentities().nonEmpty()
        );
        menu.findItem(R.id.action_delete_identity).setVisible(
                isJoined && identity != null && isPanelCollapsed
        );
        menu.findItem(R.id.action_receive_identity).setVisible(
                isJoined && isPanelCollapsed
        );
        menu.findItem(R.id.action_transfer_identity).setVisible(
                isJoined && identity != null && isPanelCollapsed
        );
        return true;
    }

    @Override
    public MonopolyGame onRetainCustomNonConfigurationInstance() {
        return monopolyGame;
    }

    @Override
    public void onTransferAdded(TransferWithCurrency addedTransfer) {
        lastTransferFragment.onTransferAdded(addedTransfer);
        playersTransfersFragment.onTransferAdded(addedTransfer);
    }

    @Override
    public void onTransfersChanged(
            scala.collection.Iterable<TransferWithCurrency> changedTransfers) {
        lastTransferFragment.onTransfersChanged(changedTransfers);
        playersTransfersFragment.onTransfersChanged(changedTransfers);
    }

    @Override
    public void onTransfersInitialized(scala.collection.Iterable<TransferWithCurrency> transfers) {
        lastTransferFragment.onTransfersInitialized(transfers);
        playersTransfersFragment.onTransfersInitialized(transfers);
    }

    @Override
    public void onTransfersUpdated(
            scala.collection.immutable.Map<TransactionId, TransferWithCurrency> transfers) {
        playersTransfersFragment.onTransfersUpdated(transfers);
    }

    @Override
    public void onTransferValueEntered(Identity from, List<Player> to, BigDecimal transferValue) {
        monopolyGame.transfer(
                from,
                from,
                JavaConversions.asScalaBuffer(to),
                scala.math.BigDecimal.javaBigDecimal2bigDecimal(transferValue)
        );
    }

}
