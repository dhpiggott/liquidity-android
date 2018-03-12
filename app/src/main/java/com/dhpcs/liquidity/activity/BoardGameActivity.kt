package com.dhpcs.liquidity.activity

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.DialogFragment
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.fragment.*
import com.google.protobuf.ByteString
import com.google.zxing.integration.android.IntentIntegrator
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState
import java.math.BigDecimal
import java.text.Collator
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*

class BoardGameActivity :
        AppCompatActivity(),
        EnterGameNameDialogFragment.Companion.Listener,
        EnterIdentityNameDialogFragment.Companion.Listener,
        ConfirmIdentityDeletionDialogFragment.Companion.Listener,
        CreateIdentityDialogFragment.Companion.Listener,
        IdentitiesFragment.Companion.Listener,
        BoardGame.Companion.GameActionListener,
        PlayersFragment.Companion.Listener,
        RestoreIdentityDialogFragment.Companion.Listener,
        TransferToPlayerDialogFragment.Companion.Listener {

    companion object {

        const val EXTRA_CURRENCY = "currency"
        const val EXTRA_GAME_NAME = "game_name"
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_ZONE_ID = "zone_id"

        private const val REQUEST_CODE_RECEIVE_IDENTITY = 0

        fun formatCurrency(context: Context, currencyCode: String?): String {
            return if (currencyCode == null) {
                ""
            } else {
                try {
                    val currency = Currency.getInstance(currencyCode)
                    if (currency.symbol == currency.currencyCode) {
                        context.getString(
                                R.string.currency_code_format_string,
                                currency.currencyCode
                        )
                    } else {
                        currency.symbol
                    }
                } catch (_: IllegalArgumentException) {
                    context.getString(
                            R.string.currency_code_format_string,
                            currencyCode
                    )
                }
            }
        }

        fun formatCurrencyValue(context: Context, currency: String?, value: BigDecimal): String {

            val scaleAmount: Int
            val scaledValue: BigDecimal
            val multiplier: String
            when {
                value >= BigDecimal(1000000000000000) -> {
                    scaleAmount = -15
                    scaledValue = value.scaleByPowerOfTen(-15)
                    multiplier = context.getString(R.string.value_multiplier_quadrillion)
                }
                value >= BigDecimal(1000000000000) -> {
                    scaleAmount = -12
                    scaledValue = value.scaleByPowerOfTen(-12)
                    multiplier = context.getString(R.string.value_multiplier_trillion)
                }
                value >= BigDecimal(1000000000) -> {
                    scaleAmount = -9
                    scaledValue = value.scaleByPowerOfTen(-9)
                    multiplier = context.getString(R.string.value_multiplier_billion)
                }
                value >= BigDecimal(1000000) -> {
                    scaleAmount = -6
                    scaledValue = value.scaleByPowerOfTen(-6)
                    multiplier = context.getString(R.string.value_multiplier_million)
                }
                value >= BigDecimal(1000) -> {
                    scaleAmount = -3
                    scaledValue = value.scaleByPowerOfTen(-3)
                    multiplier = context.getString(R.string.value_multiplier_thousand)
                }
                else -> {
                    scaleAmount = 0
                    scaledValue = value
                    multiplier = ""
                }
            }

            val maximumFractionDigits: Int
            val minimumFractionDigits: Int
            when (scaleAmount) {
                0 -> when {
                    scaledValue.scale() == 0 -> {
                        maximumFractionDigits = 0
                        minimumFractionDigits = 0
                    }
                    else -> {
                        maximumFractionDigits = scaledValue.scale()
                        minimumFractionDigits = 2
                    }
                }
                else -> {
                    maximumFractionDigits = scaledValue.scale()
                    minimumFractionDigits = 0
                }
            }

            val numberFormat = NumberFormat.getNumberInstance() as DecimalFormat
            numberFormat.maximumFractionDigits = maximumFractionDigits
            numberFormat.minimumFractionDigits = minimumFractionDigits

            return context.getString(
                    R.string.currency_value_format_string,
                    formatCurrency(context, currency),
                    numberFormat.format(scaledValue),
                    multiplier
            )
        }

        fun formatAccountOrPlayer(context: Context,
                                  accountId: String,
                                  accountName: String?,
                                  player: BoardGame.Companion.Player?): String {
            return if (player == null) {
                context.getString(
                        R.string.non_player_transfer_location_format_string,
                        accountId,
                        formatNullable(context, accountName)
                )
            } else {
                formatNullable(context, player.name)
            }
        }

        fun formatNullable(context: Context, nullable: String?): String {
            return nullable ?: context.getString(R.string.unnamed)
        }

        fun playerComparator(context: Context): Comparator<BoardGame.Companion.Player> {
            return object : Comparator<BoardGame.Companion.Player> {

                private val collator = Collator.getInstance()

                override fun compare(lhs: BoardGame.Companion.Player,
                                     rhs: BoardGame.Companion.Player
                ): Int {
                    val nameOrdered = collator.compare(
                            BoardGameActivity.formatNullable(
                                    context,
                                    lhs.name
                            ),
                            BoardGameActivity.formatNullable(
                                    context,
                                    rhs.name
                            )
                    )
                    return when (nameOrdered) {
                        0 -> {
                            val lhsId = lhs.memberId
                            val rhsId = rhs.memberId
                            lhsId.compareTo(rhsId)
                        }
                        else -> nameOrdered
                    }
                }

            }
        }

    }

    private var transferReceiptMediaPlayer: MediaPlayer? = null

    private var joinRequestToken: BoardGame.Companion.JoinRequestToken? = null
    private var boardGame: BoardGame? = null

    private var progressBarState: ProgressBar? = null
    private var textViewState: TextView? = null
    private var buttonReconnect: Button? = null
    private var slidingUpPanelLayout: SlidingUpPanelLayout? = null

    private var identitiesFragment: IdentitiesFragment? = null
    private var playersFragment: PlayersFragment? = null
    private var playersTransfersFragment: PlayersTransfersFragment? = null

    fun isIdentityNameValid(name: CharSequence): Boolean = boardGame!!.isIdentityNameValid(name)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_board_game)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        setSupportActionBar(toolbar)
        val actionBar = supportActionBar!!
        actionBar.setDisplayHomeAsUpEnabled(true)

        progressBarState = findViewById(R.id.progressbar_state)
        textViewState = findViewById(R.id.textview_state)
        buttonReconnect = findViewById(R.id.button_reconnect)
        slidingUpPanelLayout = findViewById(R.id.slidinguppanellayout)

        buttonReconnect!!.setOnClickListener {
            boardGame!!.requestJoin(joinRequestToken!!, true)
        }

        slidingUpPanelLayout!!.addPanelSlideListener(
                object : SlidingUpPanelLayout.PanelSlideListener {

                    override fun onPanelSlide(view: View, v: Float) {}

                    override fun onPanelStateChanged(panel: View,
                                                     previousState: PanelState,
                                                     newState: PanelState
                    ) {
                        when (newState) {
                            SlidingUpPanelLayout.PanelState.EXPANDED ->
                                setTitle(R.string.transfers)
                            SlidingUpPanelLayout.PanelState.COLLAPSED ->
                                title = if (boardGame!!.joinState ==
                                        BoardGame.Companion.JoinState.JOINED) {
                                    formatNullable(
                                            this@BoardGameActivity,
                                            boardGame!!.gameName
                                    )
                                } else {
                                    getString(R.string.activity_board_game_title)
                                }
                            SlidingUpPanelLayout.PanelState.ANCHORED,
                            SlidingUpPanelLayout.PanelState.HIDDEN,
                            SlidingUpPanelLayout.PanelState.DRAGGING -> {
                            }
                        }
                    }

                }
        )

        identitiesFragment = supportFragmentManager
                .findFragmentById(R.id.fragment_identities) as IdentitiesFragment
        playersFragment = supportFragmentManager
                .findFragmentById(R.id.fragment_players) as PlayersFragment
        playersTransfersFragment = supportFragmentManager
                .findFragmentById(R.id.fragment_players_transfers) as PlayersTransfersFragment

        transferReceiptMediaPlayer = MediaPlayer.create(
                this,
                R.raw.antique_cash_register_punching_single_key
        )

        joinRequestToken = lastCustomNonConfigurationInstance as
                BoardGame.Companion.JoinRequestToken?

        if (joinRequestToken == null) joinRequestToken = BoardGame.Companion.JoinRequestToken()

        val zoneId = if (savedInstanceState != null) {
            savedInstanceState.getString(EXTRA_ZONE_ID)
        } else {
            intent.extras!!.getString(EXTRA_ZONE_ID)
        }

        if (zoneId == null) {
            val currency = intent.extras!!.getSerializable(EXTRA_CURRENCY) as Currency
            val gameName = intent.extras!!.getString(EXTRA_GAME_NAME)
            boardGame = BoardGame(
                    applicationContext,
                    LiquidityApplication.getServerConnection(applicationContext),
                    LiquidityApplication.getGameDatabase(applicationContext),
                    currency,
                    gameName,
                    getString(R.string.bank_member_name)
            )
        } else {
            boardGame = BoardGame.Companion.getInstance(zoneId)
            if (boardGame == null) {
                boardGame = if (!intent.extras!!.containsKey(EXTRA_GAME_ID)) {
                    BoardGame(
                            applicationContext,
                            LiquidityApplication.getServerConnection(applicationContext),
                            LiquidityApplication.getGameDatabase(applicationContext),
                            zoneId
                    )
                } else {
                    BoardGame(
                            applicationContext,
                            LiquidityApplication.getServerConnection(applicationContext),
                            LiquidityApplication.getGameDatabase(applicationContext),
                            zoneId,
                            intent.extras!!.getLong(EXTRA_GAME_ID)
                    )
                }
                if (intent.extras!!.containsKey(EXTRA_GAME_NAME)) {
                    title = intent.extras!!.getString(EXTRA_GAME_NAME)
                }
            }
        }

        boardGame!!.registerListener(this)
    }

    override fun onStart() {
        super.onStart()
        boardGame!!.requestJoin(joinRequestToken!!)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.board_game_toolbar, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val isJoined = boardGame!!.joinState == BoardGame.Companion.JoinState.JOINED
        val identity = identitiesFragment!!.getIdentity(identitiesFragment!!.selectedPage)
        val isPanelCollapsed = slidingUpPanelLayout!!.panelState == PanelState.COLLAPSED
        menu.findItem(R.id.action_add_players).isVisible =
                isJoined
        menu.findItem(R.id.action_group_transfer).isVisible =
                isJoined && identity != null && isPanelCollapsed
        menu.findItem(R.id.action_change_game_name).isVisible =
                isJoined
        menu.findItem(R.id.action_change_identity_name).isVisible =
                isJoined && identity != null && isPanelCollapsed && !identity.isBanker
        menu.findItem(R.id.action_create_identity).isVisible =
                isJoined && isPanelCollapsed
        menu.findItem(R.id.action_restore_identity).isVisible =
                isJoined && isPanelCollapsed && boardGame!!.hiddenIdentities.isNotEmpty()
        menu.findItem(R.id.action_delete_identity).isVisible =
                isJoined && identity != null && isPanelCollapsed
        menu.findItem(R.id.action_receive_identity).isVisible =
                isJoined && isPanelCollapsed
        menu.findItem(R.id.action_transfer_identity).isVisible =
                isJoined && identity != null && isPanelCollapsed
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home ->
                return if (slidingUpPanelLayout!!.panelState == PanelState.EXPANDED
                        || slidingUpPanelLayout!!.panelState == PanelState.ANCHORED) {
                    slidingUpPanelLayout!!.panelState = PanelState.COLLAPSED
                    true
                } else {
                    false
                }
            R.id.action_add_players -> {
                val zoneIdHolder = Bundle()
                zoneIdHolder.putString(
                        BoardGameChildActivity.EXTRA_ZONE_ID,
                        boardGame!!.zoneId
                )
                startActivity(
                        Intent(this, AddPlayersActivity::class.java)
                                .putExtra(
                                        BoardGameChildActivity.EXTRA_ZONE_ID_HOLDER,
                                        zoneIdHolder
                                )
                                .putExtra(
                                        AddPlayersActivity.EXTRA_GAME_NAME,
                                        boardGame!!.gameName
                                )
                )
                return true
            }
            R.id.action_group_transfer -> {
                val identity = identitiesFragment!!.getIdentity(identitiesFragment!!.selectedPage)
                if (identity != null) {
                    TransferToPlayerDialogFragment.newInstance(
                            ArrayList(boardGame!!.identities.toList()),
                            ArrayList(boardGame!!.players.toList()),
                            boardGame!!.currency,
                            identity, null
                    ).show(supportFragmentManager, TransferToPlayerDialogFragment.TAG)
                }
                return true
            }
            R.id.action_change_game_name -> {
                EnterGameNameDialogFragment.newInstance(title.toString())
                        .show(supportFragmentManager, EnterGameNameDialogFragment.TAG)
                return true
            }
            R.id.action_change_identity_name -> {
                val identity = identitiesFragment!!.getIdentity(identitiesFragment!!.selectedPage)
                if (identity != null) {
                    EnterIdentityNameDialogFragment.newInstance(identity)
                            .show(supportFragmentManager, EnterIdentityNameDialogFragment.TAG)
                }
                return true
            }
            R.id.action_create_identity -> {
                CreateIdentityDialogFragment.newInstance()
                        .show(supportFragmentManager, CreateIdentityDialogFragment.TAG)
                return true
            }
            R.id.action_restore_identity -> {
                RestoreIdentityDialogFragment.newInstance(
                        ArrayList(boardGame!!.hiddenIdentities.toList())
                ).show(supportFragmentManager, RestoreIdentityDialogFragment.TAG)
                return true
            }
            R.id.action_delete_identity -> {
                val identity = identitiesFragment!!.getIdentity(identitiesFragment!!.selectedPage)
                if (identity != null) {
                    ConfirmIdentityDeletionDialogFragment.newInstance(identity)
                            .show(supportFragmentManager, ConfirmIdentityDeletionDialogFragment.TAG)
                }
                return true
            }
            R.id.action_receive_identity -> {
                val zoneIdHolder = Bundle()
                zoneIdHolder.putString(
                        BoardGameChildActivity.EXTRA_ZONE_ID,
                        boardGame!!.zoneId
                )
                startActivityForResult(
                        Intent(this, ReceiveIdentityActivity::class.java)
                                .putExtra(BoardGameChildActivity.EXTRA_ZONE_ID_HOLDER, zoneIdHolder)
                                .putExtra(
                                        ReceiveIdentityActivity.EXTRA_PUBLIC_KEY,
                                        LiquidityApplication.getServerConnection(applicationContext)
                                                .clientKey
                                ),
                        REQUEST_CODE_RECEIVE_IDENTITY
                )
                return true
            }
            R.id.action_transfer_identity -> {
                val identityNameHolder = Bundle()
                identityNameHolder.putString(
                        TransferIdentityActivity.EXTRA_IDENTITY_NAME,
                        identitiesFragment!!.getIdentity(identitiesFragment!!.selectedPage)!!.name
                )
                val zoneIdHolder = Bundle()
                zoneIdHolder.putString(
                        BoardGameChildActivity.EXTRA_ZONE_ID,
                        boardGame!!.zoneId
                )
                IntentIntegrator(this)
                        .setCaptureActivity(TransferIdentityActivity::class.java)
                        .addExtra(
                                TransferIdentityActivity.EXTRA_IDENTITY_NAME_HOLDER,
                                identityNameHolder
                        )
                        .addExtra(
                                BoardGameChildActivity.EXTRA_ZONE_ID_HOLDER,
                                zoneIdHolder
                        )
                        .setDesiredBarcodeFormats(setOf("QR_CODE"))
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                        .initiateScan()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        when {
            result != null -> {
                val contents = result.contents
                if (contents != null) {
                    if (boardGame!!.joinState == BoardGame.Companion.JoinState.JOINED) {
                        val identity = identitiesFragment!!.getIdentity(
                                identitiesFragment!!.selectedPage
                        )
                        try {
                            val publicKey = ByteString.copyFrom(
                                    okio.ByteString.decodeBase64(contents)!!.toByteArray()
                            )
                            if (!boardGame!!.isPublicKeyConnectedAndImplicitlyValid(publicKey)) {
                                throw IllegalArgumentException()
                            }
                            boardGame!!.transferIdentity(identity!!, publicKey)
                        } catch (_: IllegalArgumentException) {
                            Toast.makeText(
                                    this,
                                    getString(
                                            R.string.transfer_identity_invalid_code_format_string,
                                            formatNullable(this, boardGame!!.gameName)
                                    ),
                                    Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            else ->
                super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onBackPressed() = when {
        slidingUpPanelLayout!!.panelState == PanelState.EXPANDED ||
                slidingUpPanelLayout!!.panelState == PanelState.ANCHORED ->
            slidingUpPanelLayout!!.panelState = PanelState.COLLAPSED
        else ->
            super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations) closeDialogFragments()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) boardGame!!.unrequestJoin(joinRequestToken!!)
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState!!.putString(EXTRA_ZONE_ID, boardGame!!.zoneId)
    }

    override fun onRetainCustomNonConfigurationInstance(): BoardGame.Companion.JoinRequestToken? {
        return joinRequestToken
    }

    override fun onDestroy() {
        super.onDestroy()
        boardGame!!.unregisterListener(this)
        transferReceiptMediaPlayer!!.release()
    }

    override fun onJoinStateChanged(joinState: BoardGame.Companion.JoinState) {
        when (joinState) {
            BoardGame.Companion.JoinState.UNAVAILABLE -> {

                closeDialogFragments()

                slidingUpPanelLayout!!.visibility = View.GONE
                progressBarState!!.visibility = View.GONE

                textViewState!!.visibility = View.VISIBLE
                textViewState!!.setText(R.string.join_state_unavailable)
                buttonReconnect!!.visibility = View.GONE

            }
            BoardGame.Companion.JoinState.FAILED -> {

                closeDialogFragments()

                slidingUpPanelLayout!!.visibility = View.GONE
                progressBarState!!.visibility = View.GONE

                textViewState!!.visibility = View.VISIBLE
                textViewState!!.setText(R.string.join_state_general_failure)
                buttonReconnect!!.visibility = View.VISIBLE

            }
            BoardGame.Companion.JoinState.AVAILABLE -> {

                closeDialogFragments()

                slidingUpPanelLayout!!.visibility = View.GONE
                progressBarState!!.visibility = View.GONE

                textViewState!!.visibility = View.VISIBLE
                textViewState!!.setText(R.string.join_state_available)
                buttonReconnect!!.visibility = View.VISIBLE

            }
            BoardGame.Companion.JoinState.CREATING -> {

                closeDialogFragments()

                slidingUpPanelLayout!!.visibility = View.GONE
                buttonReconnect!!.visibility = View.GONE

                progressBarState!!.visibility = View.VISIBLE
                textViewState!!.visibility = View.VISIBLE
                textViewState!!.setText(R.string.join_state_creating)

            }
            BoardGame.Companion.JoinState.JOINING -> {

                closeDialogFragments()

                slidingUpPanelLayout!!.visibility = View.GONE
                buttonReconnect!!.visibility = View.GONE

                progressBarState!!.visibility = View.VISIBLE
                textViewState!!.visibility = View.VISIBLE
                textViewState!!.setText(R.string.join_state_joining)

            }
            BoardGame.Companion.JoinState.JOINED -> {

                buttonReconnect!!.visibility = View.GONE
                textViewState!!.text = null
                textViewState!!.visibility = View.GONE
                progressBarState!!.visibility = View.GONE

                slidingUpPanelLayout!!.visibility = View.VISIBLE

            }
        }
        invalidateOptionsMenu()
    }

    override fun onCreateGameError(name: String?) {
        Toast.makeText(
                this,
                getString(
                        R.string.create_game_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show()
        finish()
    }

    override fun onJoinGameError() {
        Toast.makeText(
                this,
                R.string.join_game_error,
                Toast.LENGTH_LONG
        ).show()
        finish()
    }

    override fun onIdentityRequired() {
        CreateIdentityDialogFragment.newInstance().show(
                supportFragmentManager,
                CreateIdentityDialogFragment.TAG
        )
    }

    override fun onNoIdentitiesTextClicked() {
        CreateIdentityDialogFragment.newInstance().show(
                supportFragmentManager,
                CreateIdentityDialogFragment.TAG
        )
    }

    override fun onIdentityNameEntered(name: String) = boardGame!!.createIdentity(name)

    override fun onCreateIdentityMemberError(name: String?) {
        Toast.makeText(
                this,
                getString(
                        R.string.create_identity_member_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show()
    }

    override fun onCreateIdentityAccountError(name: String?) {
        Toast.makeText(
                this,
                getString(
                        R.string.create_identity_account_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show()
    }

    override fun onIdentityCreated(identity: BoardGame.Companion.IdentityWithBalance) {
        identitiesFragment!!.selectedPage = identitiesFragment!!.getPage(identity)
    }

    override fun onTransferIdentityError(name: String?) {
        Toast.makeText(
                this,
                getString(
                        R.string.transfer_identity_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show()
    }

    override fun onIdentityReceived(identity: BoardGame.Companion.IdentityWithBalance) {
        identitiesFragment!!.selectedPage = identitiesFragment!!.getPage(identity)
    }

    override fun onIdentityDeleteConfirmed(identity: BoardGame.Companion.Identity) {
        boardGame!!.deleteIdentity(identity)
    }

    override fun onDeleteIdentityError(name: String?) {
        Toast.makeText(
                this,
                getString(
                        R.string.delete_identity_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show()
    }

    override fun onIdentityRestorationRequested(identity: BoardGame.Companion.Identity) {
        boardGame!!.restoreIdentity(identity)
    }

    override fun onRestoreIdentityError(name: String?) {
        Toast.makeText(
                this,
                getString(
                        R.string.restore_identity_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show()
    }

    override fun onIdentityRestored(identity: BoardGame.Companion.IdentityWithBalance) {
        identitiesFragment!!.selectedPage = identitiesFragment!!.getPage(identity)
    }

    override fun onIdentityNameEntered(identity: BoardGame.Companion.Identity, name: String) {
        boardGame!!.changeIdentityName(identity, name)
    }

    override fun onChangeIdentityNameError(name: String?) {
        Toast.makeText(
                this,
                getString(
                        R.string.change_identity_name_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show()
    }

    override fun onIdentityPageSelected(page: Int) {
        playersFragment!!.onSelectedIdentityChanged(identitiesFragment!!.getIdentity(page)!!)
    }

    override fun onIdentitiesUpdated(identities: Map<String,
            BoardGame.Companion.IdentityWithBalance>
    ) {
        identitiesFragment!!.onIdentitiesUpdated(identities)
        playersFragment!!.onSelectedIdentityChanged(
                identitiesFragment!!.getIdentity(identitiesFragment!!.selectedPage)
        )
        val transferToPlayerDialogFragment = supportFragmentManager
                .findFragmentByTag(TransferToPlayerDialogFragment.TAG) as
                TransferToPlayerDialogFragment?
        transferToPlayerDialogFragment?.onIdentitiesUpdated(identities)
    }

    override fun onPlayersInitialized(players: Collection<
            BoardGame.Companion.PlayerWithBalanceAndConnectionState>
    ) {
        playersFragment!!.onPlayersInitialized(players)
    }

    override fun onNoPlayersTextClicked() {
        val zoneIdHolder = Bundle()
        zoneIdHolder.putString(BoardGameChildActivity.EXTRA_ZONE_ID, boardGame!!.zoneId)
        startActivity(
                Intent(this, AddPlayersActivity::class.java)
                        .putExtra(BoardGameChildActivity.EXTRA_ZONE_ID_HOLDER, zoneIdHolder)
                        .putExtra(AddPlayersActivity.EXTRA_GAME_NAME, boardGame!!.gameName)
        )
    }

    override fun onPlayerAdded(
            addedPlayer: BoardGame.Companion.PlayerWithBalanceAndConnectionState
    ) {
        playersFragment!!.onPlayerAdded(addedPlayer)
    }

    override fun onPlayerClicked(player: BoardGame.Companion.Player) {
        val identity = identitiesFragment!!.getIdentity(identitiesFragment!!.selectedPage)
        if (identity != null) {
            TransferToPlayerDialogFragment.newInstance(
                    ArrayList(boardGame!!.identities.toList()),
                    ArrayList(boardGame!!.players.toList()),
                    boardGame!!.currency,
                    identity,
                    player
            ).show(supportFragmentManager, TransferToPlayerDialogFragment.TAG)
        }
    }

    override fun onPlayerChanged(
            changedPlayer: BoardGame.Companion.PlayerWithBalanceAndConnectionState
    ) {
        playersFragment!!.onPlayerChanged(changedPlayer)
    }

    override fun onPlayerRemoved(
            removedPlayer: BoardGame.Companion.PlayerWithBalanceAndConnectionState
    ) {
        playersFragment!!.onPlayerRemoved(removedPlayer)
    }

    override fun onPlayersUpdated(players: Map<String,
            BoardGame.Companion.PlayerWithBalanceAndConnectionState>) {
        playersFragment!!.onPlayersUpdated(players)
        playersTransfersFragment!!.onPlayersUpdated(players)
    }

    override fun onTransferValueEntered(from: BoardGame.Companion.Identity,
                                        to: Collection<BoardGame.Companion.Player>,
                                        transferValue: BigDecimal
    ) {
        to.forEach {
            boardGame!!.transferToPlayer(
                    from,
                    from,
                    it,
                    transferValue
            )
        }
    }

    override fun onTransferToPlayerError(name: String?) {
        Toast.makeText(
                this,
                getString(
                        R.string.transfer_to_player_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show()
    }

    override fun onTransfersInitialized(transfers:
                                        Collection<BoardGame.Companion.TransferWithCurrency>
    ) {
        playersTransfersFragment!!.onTransfersInitialized(transfers)
    }

    override fun onTransferAdded(addedTransfer: BoardGame.Companion.TransferWithCurrency) {
        if (addedTransfer.toPlayer != null &&
                PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean("play_transfer_receipt_sounds", true) &&
                addedTransfer.toPlayer.ownerPublicKey ==
                        LiquidityApplication.getServerConnection(applicationContext).clientKey
                ) {
            if (transferReceiptMediaPlayer!!.isPlaying) {
                transferReceiptMediaPlayer!!.seekTo(0)
            } else {
                transferReceiptMediaPlayer!!.start()
            }
        }
        playersTransfersFragment!!.onTransferAdded(addedTransfer)
    }

    override fun onTransfersChanged(changedTransfers: Collection<
            BoardGame.Companion.TransferWithCurrency>
    ) {
        playersTransfersFragment!!.onTransfersChanged(changedTransfers)
    }

    override fun onTransfersUpdated(transfers: Map<String,
            BoardGame.Companion.TransferWithCurrency>
    ) {
        playersTransfersFragment!!.onTransfersUpdated(transfers)
    }

    override fun onGameNameEntered(name: String) = boardGame!!.changeGameName(name)

    override fun onChangeGameNameError(name: String?) {
        Toast.makeText(
                this,
                getString(
                        R.string.change_game_name_error_format_string,
                        formatNullable(this, name)
                ),
                Toast.LENGTH_LONG
        ).show()
    }

    override fun onGameNameChanged(name: String?) {
        title = formatNullable(this, name)
    }

    override fun onQuitGameError() {
        Toast.makeText(
                this,
                R.string.quit_game_error,
                Toast.LENGTH_LONG
        ).show()
        finish()
    }

    private fun closeDialogFragments() {
        arrayOf(ConfirmIdentityDeletionDialogFragment.TAG,
                CreateIdentityDialogFragment.TAG,
                EnterGameNameDialogFragment.TAG,
                EnterIdentityNameDialogFragment.TAG,
                RestoreIdentityDialogFragment.TAG,
                TransferToPlayerDialogFragment.TAG
        )
                .map { supportFragmentManager.findFragmentByTag(it) as DialogFragment? }
                .forEach { it?.dismiss() }
    }

}
