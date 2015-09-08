package com.dhpcs.liquidity.activity;

import android.app.DialogFragment;
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

import com.dhpcs.liquidity.BoardGame;
import com.dhpcs.liquidity.BoardGame.Identity;
import com.dhpcs.liquidity.BoardGame.IdentityWithBalance;
import com.dhpcs.liquidity.BoardGame.Player;
import com.dhpcs.liquidity.BoardGame.PlayerWithBalanceAndConnectionState;
import com.dhpcs.liquidity.BoardGame.TransferWithCurrency;
import com.dhpcs.liquidity.ClientKey;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.ServerConnection;
import com.dhpcs.liquidity.fragment.ConfirmIdentityDeletionDialogFragment;
import com.dhpcs.liquidity.fragment.CreateIdentityDialogFragment;
import com.dhpcs.liquidity.fragment.EnterGameNameDialogFragment;
import com.dhpcs.liquidity.fragment.EnterIdentityNameDialogFragment;
import com.dhpcs.liquidity.fragment.IdentitiesFragment;
import com.dhpcs.liquidity.fragment.LastTransferFragment;
import com.dhpcs.liquidity.fragment.PlayersFragment;
import com.dhpcs.liquidity.fragment.PlayersTransfersFragment;
import com.dhpcs.liquidity.fragment.RestoreIdentityDialogFragment;
import com.dhpcs.liquidity.fragment.TransferToPlayerDialogFragment;
import com.dhpcs.liquidity.models.Account;
import com.dhpcs.liquidity.models.AccountId;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.models.PublicKey;
import com.dhpcs.liquidity.models.TransactionId;
import com.dhpcs.liquidity.models.ZoneId;
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

import okio.ByteString;
import scala.Option;
import scala.Tuple2;
import scala.collection.JavaConversions;
import scala.util.Either;

public class BoardGameActivity extends AppCompatActivity
        implements EnterGameNameDialogFragment.Listener,
        EnterIdentityNameDialogFragment.Listener,
        ConfirmIdentityDeletionDialogFragment.Listener,
        CreateIdentityDialogFragment.Listener,
        IdentitiesFragment.Listener,
        BoardGame.GameActionListener,
        BoardGame.JoinStateListener,
        PlayersFragment.Listener,
        RestoreIdentityDialogFragment.Listener,
        TransferToPlayerDialogFragment.Listener {

    public static final String EXTRA_CURRENCY = "currency";
    public static final String EXTRA_GAME_NAME = "game_name";
    public static final String EXTRA_GAME_ID = "game_id";
    public static final String EXTRA_ZONE_ID = "zone_id";

    private static final int REQUEST_CODE_RECEIVE_IDENTITY = 0;

    public static String formatCurrency(Context context,
                                        Option<Either<String, Currency>> currency) {

        String result;
        if (!currency.isDefined()) {
            result = context.getString(R.string.currency_none);
        } else {

            Either<String, Currency> c = currency.get();
            if (c.isLeft()) {
                result = context.getString(
                        R.string.currency_code_format_string,
                        c.left().get()
                );
            } else if (c.right().get().getSymbol().equals(c.right().get().getCurrencyCode())) {
                result = context.getString(
                        R.string.currency_code_format_string,
                        c.right().get().getCurrencyCode()
                );
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
                    accountId.id(),
                    formatNullable(context, account.name())
            );
        } else {
            result = formatNullable(
                    context,
                    eitherAccountTupleOrMember.right().get().member().name()
            );
        }
        return result;
    }

    public static String formatNullable(Context context, Option<String> nullable) {
        if (!nullable.isDefined()) {
            return context.getString(R.string.unnamed);
        } else {
            return nullable.get();
        }
    }

    public static boolean isIdentityNameValid(Context context, CharSequence identityName) {
        return !TextUtils.isEmpty(identityName)
                && !identityName.toString().equals(context.getString(R.string.bank_member_name));
    }

    public static Comparator<Player> playerComparator(final Context context) {
        return new Comparator<Player>() {

            private final Collator collator = Collator.getInstance();

            @Override
            public int compare(Player lhs,
                               Player rhs) {
                int nameOrdered = collator.compare(
                        BoardGameActivity.formatNullable(context, lhs.member().name()),
                        BoardGameActivity.formatNullable(context, rhs.member().name())
                );
                if (nameOrdered == 0) {
                    long lhsId = lhs.member().id().id();
                    long rhsId = rhs.member().id().id();
                    return lhsId < rhsId ? -1 : (lhsId == rhsId ? 0 : 1);
                } else {
                    return nameOrdered;
                }
            }

        };
    }

    private BoardGame.JoinRequestToken joinRequestToken;
    private BoardGame boardGame;

    private boolean isStartingChildActivity;

    private ProgressBar progressBarState;
    private TextView textViewState;
    private Button buttonReconnect;
    private SlidingUpPanelLayout slidingUpPanelLayout;

    private IdentitiesFragment identitiesFragment;
    private PlayersFragment playersFragment;
    private LastTransferFragment lastTransferFragment;
    private PlayersTransfersFragment playersTransfersFragment;

    private void closeDialogFragments() {
        for (String tag : new String[]{
                ConfirmIdentityDeletionDialogFragment.TAG,
                CreateIdentityDialogFragment.TAG,
                EnterGameNameDialogFragment.TAG,
                EnterIdentityNameDialogFragment.TAG,
                RestoreIdentityDialogFragment.TAG,
                TransferToPlayerDialogFragment.TAG
        }) {
            DialogFragment dialogFragment =
                    (DialogFragment) getFragmentManager().findFragmentByTag(tag);
            if (dialogFragment != null) {
                dialogFragment.dismiss();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            String contents = result.getContents();
            if (contents != null) {
                if (boardGame.getJoinState() == BoardGame.JOINED$.MODULE$) {
                    Identity identity = identitiesFragment.getIdentity(
                            identitiesFragment.getSelectedPage()
                    );
                    try {
                        PublicKey publicKey = new PublicKey(
                                ByteString.decodeBase64(
                                        contents
                                ).toByteArray()
                        );
                        if (!boardGame.isPublicKeyConnectedAndImplicitlyValid(publicKey)) {
                            throw new IllegalArgumentException();
                        }
                        boardGame.transferIdentity(identity, publicKey);
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(
                                this,
                                getString(
                                        R.string.transfer_identity_invalid_code_format_string,
                                        formatNullable(this, boardGame.getGameName())
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
    public void onChangeGameNameError(Option<String> name) {
        Toast.makeText(
                this,
                getString(
                        R.string.change_game_name_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    public void onChangeIdentityNameError(Option<String> name) {
        Toast.makeText(
                this,
                getString(
                        R.string.change_identity_name_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_board_game);

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
                boardGame.requestJoin(joinRequestToken, true);
            }

        });

        slidingUpPanelLayout.setPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {

            @Override
            public void onPanelSlide(View view, float v) {
            }

            @Override
            public void onPanelCollapsed(View view) {
                setTitle(boardGame.getJoinState() == BoardGame.JOINED$.MODULE$
                        ?
                        formatNullable(BoardGameActivity.this, boardGame.getGameName())
                        :
                        getString(R.string.activity_board_game_title));
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

        joinRequestToken = (BoardGame.JoinRequestToken) getLastCustomNonConfigurationInstance();

        if (joinRequestToken == null) {

            joinRequestToken = new BoardGame.JoinRequestToken();

        }

        if (getIntent().getExtras() == null) {
            throw new Error();
        }

        ZoneId zoneId = (ZoneId) getIntent().getExtras().getSerializable(EXTRA_ZONE_ID);

        if (savedInstanceState != null) {
            zoneId = (ZoneId) savedInstanceState.getSerializable(EXTRA_ZONE_ID);
        }

        if (zoneId == null) {

            Currency currency = (Currency) getIntent().getExtras().getSerializable(EXTRA_CURRENCY);
            String gameName = getIntent().getExtras().getString(EXTRA_GAME_NAME);

            if (currency == null) {
                throw new Error();
            }
            if (gameName == null) {
                throw new Error();
            }

            boardGame = new BoardGame(
                    this,
                    ServerConnection.getInstance(getApplicationContext()),
                    currency,
                    gameName
            );

        } else {

            boardGame = BoardGame.getInstance(zoneId);

            if (boardGame == null) {

                if (!getIntent().getExtras().containsKey(EXTRA_GAME_ID)) {

                    boardGame = new BoardGame(
                            this,
                            ServerConnection.getInstance(getApplicationContext()),
                            zoneId
                    );

                } else {

                    boardGame = new BoardGame(
                            this,
                            ServerConnection.getInstance(getApplicationContext()),
                            zoneId,
                            getIntent().getExtras().getLong(EXTRA_GAME_ID)
                    );

                }

                if (getIntent().getExtras().containsKey(EXTRA_GAME_NAME)) {

                    setTitle(getIntent().getExtras().getString(EXTRA_GAME_NAME));

                }

            }

        }

        boardGame.registerListener((BoardGame.JoinStateListener) this);
        boardGame.registerListener((BoardGame.GameActionListener) this);
    }

    @Override
    public void onCreateGameError(Option<String> name) {
        Toast.makeText(
                this,
                getString(
                        R.string.create_game_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show();
        finish();
    }

    @Override
    public void onCreateIdentityAccountError(Option<String> name) {
        Toast.makeText(
                this,
                getString(
                        R.string.create_identity_account_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    public void onCreateIdentityMemberError(Option<String> name) {
        Toast.makeText(
                this,
                getString(
                        R.string.create_identity_member_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    public void onDeleteIdentityError(Option<String> name) {
        Toast.makeText(
                this,
                getString(
                        R.string.delete_identity_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_board_game, menu);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        boardGame.unregisterListener((BoardGame.GameActionListener) this);
        boardGame.unregisterListener((BoardGame.JoinStateListener) this);
    }

    @Override
    public void onGameNameChanged(Option<String> name) {
        setTitle(formatNullable(this, name));
    }

    @Override
    public void onGameNameEntered(String name) {
        boardGame.changeGameName(name);
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
                        .findFragmentByTag(TransferToPlayerDialogFragment.TAG);
        if (transferToPlayerDialogFragment != null) {
            transferToPlayerDialogFragment.onIdentitiesUpdated(identities);
        }
    }

    @Override
    public void onIdentityDeleteConfirmed(Identity identity) {
        boardGame.deleteIdentity(identity);
    }

    @Override
    public void onIdentityCreated(IdentityWithBalance identity) {
        identitiesFragment.setSelectedPage(identitiesFragment.getPage(identity));
    }

    @Override
    public void onIdentityNameEntered(String name) {
        boardGame.createIdentity(name);
    }

    @Override
    public void onIdentityNameEntered(Identity identity, String name) {
        boardGame.changeIdentityName(identity, name);
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
        CreateIdentityDialogFragment.newInstance()
                .show(
                        getFragmentManager(),
                        CreateIdentityDialogFragment.TAG
                );
    }

    @Override
    public void onIdentityRestored(IdentityWithBalance identity) {
        identitiesFragment.setSelectedPage(identitiesFragment.getPage(identity));
    }

    @Override
    public void onIdentityRestorationRequested(Identity identity) {
        boardGame.restoreIdentity(identity);
    }

    @Override
    public void onJoinGameError() {
        Toast.makeText(
                this,
                R.string.join_game_error,
                Toast.LENGTH_LONG
        ).show();
        finish();
    }

    @Override
    public void onJoinStateChanged(BoardGame.JoinState joinState) {
        if (joinState == BoardGame.UNAVAILABLE$.MODULE$) {

            closeDialogFragments();

            slidingUpPanelLayout.setVisibility(View.GONE);
            progressBarState.setVisibility(View.GONE);

            textViewState.setVisibility(View.VISIBLE);
            textViewState.setText(R.string.join_state_unavailable);
            buttonReconnect.setVisibility(View.GONE);

        } else if (joinState == BoardGame.AVAILABLE$.MODULE$) {

            closeDialogFragments();

            slidingUpPanelLayout.setVisibility(View.GONE);
            progressBarState.setVisibility(View.GONE);

            textViewState.setVisibility(View.VISIBLE);
            textViewState.setText(R.string.join_state_available);
            buttonReconnect.setVisibility(View.VISIBLE);

        } else if (joinState == BoardGame.CONNECTING$.MODULE$) {

            closeDialogFragments();

            slidingUpPanelLayout.setVisibility(View.GONE);
            buttonReconnect.setVisibility(View.GONE);

            progressBarState.setVisibility(View.VISIBLE);
            textViewState.setVisibility(View.VISIBLE);
            textViewState.setText(R.string.join_state_connecting);

        } else if (joinState == BoardGame.RECONNECTING$.MODULE$) {

            closeDialogFragments();

            slidingUpPanelLayout.setVisibility(View.GONE);
            buttonReconnect.setVisibility(View.GONE);

            progressBarState.setVisibility(View.VISIBLE);
            textViewState.setVisibility(View.VISIBLE);
            textViewState.setText(R.string.join_state_reconnecting);

        } else if (joinState == BoardGame.JOINING$.MODULE$) {

            closeDialogFragments();

            slidingUpPanelLayout.setVisibility(View.GONE);
            buttonReconnect.setVisibility(View.GONE);

            progressBarState.setVisibility(View.VISIBLE);
            textViewState.setVisibility(View.VISIBLE);
            textViewState.setText(R.string.join_state_joining);

        } else if (joinState == BoardGame.JOINED$.MODULE$) {

            buttonReconnect.setVisibility(View.GONE);
            textViewState.setText(null);
            textViewState.setVisibility(View.GONE);
            progressBarState.setVisibility(View.GONE);

            slidingUpPanelLayout.setVisibility(View.VISIBLE);

        } else if (joinState == BoardGame.QUITTING$.MODULE$) {

            closeDialogFragments();

            slidingUpPanelLayout.setVisibility(View.GONE);
            buttonReconnect.setVisibility(View.GONE);

            progressBarState.setVisibility(View.VISIBLE);
            textViewState.setVisibility(View.VISIBLE);
            textViewState.setText(R.string.join_state_quitting);

        } else if (joinState == BoardGame.DISCONNECTING$.MODULE$) {

            closeDialogFragments();

            slidingUpPanelLayout.setVisibility(View.GONE);
            buttonReconnect.setVisibility(View.GONE);

            progressBarState.setVisibility(View.VISIBLE);
            textViewState.setVisibility(View.VISIBLE);
            textViewState.setText(R.string.join_state_disconnecting);

        }
        supportInvalidateOptionsMenu();
    }

    @Override
    public void onNoIdentitiesTextClicked() {
        CreateIdentityDialogFragment.newInstance()
                .show(
                        getFragmentManager(),
                        CreateIdentityDialogFragment.TAG
                );
    }

    @Override
    public void onNoPlayersTextClicked() {
        isStartingChildActivity = true;
        Bundle zoneIdHolder = new Bundle();
        zoneIdHolder.putSerializable(
                TransferIdentityActivity.EXTRA_ZONE_ID,
                boardGame.getZoneId()
        );
        startActivity(
                new Intent(
                        this,
                        AddPlayersActivity.class
                ).putExtra(
                        AddPlayersActivity.EXTRA_ZONE_ID_HOLDER,
                        zoneIdHolder
                ).putExtra(
                        AddPlayersActivity.EXTRA_GAME_NAME,
                        boardGame.getGameName()
                )
        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        IdentityWithBalance identity;
        Bundle zoneIdHolder;
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
                isStartingChildActivity = true;
                zoneIdHolder = new Bundle();
                zoneIdHolder.putSerializable(
                        AddPlayersActivity.EXTRA_ZONE_ID,
                        boardGame.getZoneId()
                );
                startActivity(
                        new Intent(
                                this,
                                AddPlayersActivity.class
                        ).putExtra(
                                AddPlayersActivity.EXTRA_ZONE_ID_HOLDER,
                                zoneIdHolder
                        ).putExtra(
                                AddPlayersActivity.EXTRA_GAME_NAME,
                                boardGame.getGameName()
                        )
                );
                return true;
            case R.id.action_group_transfer:
                identity = identitiesFragment.getIdentity(identitiesFragment.getSelectedPage());
                if (identity != null) {
                    TransferToPlayerDialogFragment.newInstance(
                            boardGame.getIdentities(),
                            boardGame.getPlayers(),
                            boardGame.getCurrency(),
                            identity,
                            null
                    ).show(
                            getFragmentManager(),
                            TransferToPlayerDialogFragment.TAG
                    );
                }
                return true;
            case R.id.action_change_game_name:
                EnterGameNameDialogFragment.newInstance(getTitle().toString())
                        .show(
                                getFragmentManager(),
                                EnterGameNameDialogFragment.TAG
                        );
                return true;
            case R.id.action_change_identity_name:
                identity = identitiesFragment.getIdentity(identitiesFragment.getSelectedPage());
                if (identity != null) {
                    EnterIdentityNameDialogFragment.newInstance(identity)
                            .show(
                                    getFragmentManager(),
                                    EnterIdentityNameDialogFragment.TAG
                            );
                }
                return true;
            case R.id.action_create_identity:
                CreateIdentityDialogFragment.newInstance()
                        .show(
                                getFragmentManager(),
                                CreateIdentityDialogFragment.TAG
                        );
                return true;
            case R.id.action_restore_identity:
                RestoreIdentityDialogFragment.newInstance(boardGame.getHiddenIdentities())
                        .show(
                                getFragmentManager(),
                                RestoreIdentityDialogFragment.TAG
                        );
                return true;
            case R.id.action_delete_identity:
                identity = identitiesFragment.getIdentity(identitiesFragment.getSelectedPage());
                if (identity != null) {
                    ConfirmIdentityDeletionDialogFragment.newInstance(identity)
                            .show(
                                    getFragmentManager(),
                                    ConfirmIdentityDeletionDialogFragment.TAG
                            );
                }
                return true;
            case R.id.action_receive_identity:
                isStartingChildActivity = true;
                zoneIdHolder = new Bundle();
                zoneIdHolder.putSerializable(
                        ReceiveIdentityActivity.EXTRA_ZONE_ID,
                        boardGame.getZoneId()
                );
                startActivityForResult(
                        new Intent(
                                this,
                                ReceiveIdentityActivity.class
                        ).putExtra(
                                ReceiveIdentityActivity.EXTRA_ZONE_ID_HOLDER,
                                zoneIdHolder
                        ).putExtra(
                                ReceiveIdentityActivity.EXTRA_PUBLIC_KEY,
                                ClientKey.getPublicKey(this)
                        ),
                        REQUEST_CODE_RECEIVE_IDENTITY
                );
                return true;
            case R.id.action_transfer_identity:
                isStartingChildActivity = true;
                Bundle identityNameHolder = new Bundle();
                identityNameHolder.putSerializable(
                        TransferIdentityActivity.EXTRA_IDENTITY_NAME,
                        identitiesFragment.getIdentity(identitiesFragment.getSelectedPage())
                                .member().name()
                );
                zoneIdHolder = new Bundle();
                zoneIdHolder.putSerializable(
                        TransferIdentityActivity.EXTRA_ZONE_ID,
                        boardGame.getZoneId()
                );
                new IntentIntegrator(BoardGameActivity.this)
                        .setCaptureActivity(TransferIdentityActivity.class)
                        .addExtra(
                                TransferIdentityActivity.EXTRA_IDENTITY_NAME_HOLDER,
                                identityNameHolder
                        )
                        .addExtra(
                                TransferIdentityActivity.EXTRA_ZONE_ID_HOLDER,
                                zoneIdHolder
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
    protected void onPause() {
        super.onPause();
        boardGame.unregisterListener((BoardGame.GameActionListener) this);
        boardGame.unregisterListener((BoardGame.JoinStateListener) this);
        if (!isChangingConfigurations()) {
            if (!isStartingChildActivity) {
                boardGame.unrequestJoin(joinRequestToken);
            }
        }
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
    public void onQuitGameError() {
        Toast.makeText(
                this,
                R.string.join_game_error,
                Toast.LENGTH_LONG
        ).show();
        finish();
    }

    @Override
    public void onRestoreIdentityError(Option<String> name) {
        Toast.makeText(
                this,
                getString(
                        R.string.restore_identity_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    public void onPlayerClicked(Player player) {
        IdentityWithBalance identity = identitiesFragment.getIdentity(
                identitiesFragment.getSelectedPage()
        );
        if (identity != null) {
            TransferToPlayerDialogFragment.newInstance(
                    boardGame.getIdentities(),
                    boardGame.getPlayers(),
                    boardGame.getCurrency(),
                    identity,
                    player
            ).show(
                    getFragmentManager(),
                    TransferToPlayerDialogFragment.TAG
            );
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isJoined = boardGame.getJoinState() == BoardGame.JOINED$.MODULE$;
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
                isJoined && isPanelCollapsed && boardGame.getHiddenIdentities().nonEmpty()
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
    protected void onResume() {
        super.onResume();
        boardGame.requestJoin(joinRequestToken, false);
        boardGame.registerListener((BoardGame.JoinStateListener) this);
        boardGame.registerListener((BoardGame.GameActionListener) this);
    }

    @Override
    public BoardGame.JoinRequestToken onRetainCustomNonConfigurationInstance() {
        return joinRequestToken;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(EXTRA_ZONE_ID, boardGame.getZoneId());
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            if (isStartingChildActivity) {
                boardGame.unrequestJoin(joinRequestToken);
                isStartingChildActivity = false;
            }
        }
    }

    @Override
    public void onTransferAdded(TransferWithCurrency addedTransfer) {
        lastTransferFragment.onTransferAdded(addedTransfer);
        playersTransfersFragment.onTransferAdded(addedTransfer);
    }

    @Override
    public void onTransferIdentityError(Option<String> name) {
        Toast.makeText(
                this,
                getString(
                        R.string.transfer_identity_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    public void onTransferToPlayerError(Option<String> name) {
        Toast.makeText(
                this,
                getString(
                        R.string.transfer_to_player_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show();
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
        boardGame.transferToPlayer(
                from,
                from,
                JavaConversions.asScalaBuffer(to),
                scala.math.BigDecimal.javaBigDecimal2bigDecimal(transferValue)
        );
    }

}
