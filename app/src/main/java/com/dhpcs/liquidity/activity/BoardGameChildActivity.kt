package com.dhpcs.liquidity.activity

import android.os.Bundle
import android.support.v7.app.AppCompatActivity

import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.BoardGame.Companion.IdentityWithBalance
import com.dhpcs.liquidity.BoardGame.Companion.PlayerWithBalanceAndConnectionState
import com.dhpcs.liquidity.BoardGame.Companion.TransferWithCurrency
import com.dhpcs.liquidity.model.MemberId
import com.dhpcs.liquidity.model.TransactionId
import com.dhpcs.liquidity.model.ZoneId

import scala.Option

abstract class BoardGameChildActivity :
        AppCompatActivity(),
        BoardGame.Companion.GameActionListener,
        BoardGame.Companion.JoinStateListener {

    companion object {

        const val EXTRA_ZONE_ID_HOLDER = "zone_id_holder"
        const val EXTRA_ZONE_ID = "zone_id"

    }

    private var joinRequestToken: BoardGame.Companion.JoinRequestToken? = null
    private var retry: Boolean = false

    private var boardGame: BoardGame? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val zoneId = intent
                .getBundleExtra(EXTRA_ZONE_ID_HOLDER)
                .getSerializable(EXTRA_ZONE_ID) as ZoneId

        joinRequestToken =
                lastCustomNonConfigurationInstance as BoardGame.Companion.JoinRequestToken?

        if (joinRequestToken == null) joinRequestToken = BoardGame.Companion.JoinRequestToken()

        retry = savedInstanceState == null

        boardGame = BoardGame.Companion.getInstance(zoneId)

        boardGame!!.registerListener(this as BoardGame.Companion.JoinStateListener)
        boardGame!!.registerListener(this as BoardGame.Companion.GameActionListener)
    }

    override fun onStart() {
        super.onStart()
        boardGame!!.requestJoin(joinRequestToken!!, retry)
        retry = false
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) boardGame!!.unrequestJoin(joinRequestToken!!)
    }

    override fun onRetainCustomNonConfigurationInstance(): BoardGame.Companion.JoinRequestToken? {
        return joinRequestToken
    }

    override fun onDestroy() {
        super.onDestroy()
        boardGame!!.unregisterListener(this as BoardGame.Companion.GameActionListener)
        boardGame!!.unregisterListener(this as BoardGame.Companion.JoinStateListener)
    }

    override fun onJoinStateChanged(joinState: BoardGame.Companion.JoinState) {
        if (joinState != BoardGame.Companion.JoinState.JOINED) finish()
    }

    override fun onCreateGameError(name: Option<String>) {}
    override fun onJoinGameError() {}

    override fun onIdentityRequired() {}

    override fun onCreateIdentityMemberError(name: Option<String>) {}
    override fun onCreateIdentityAccountError(name: Option<String>) {}
    override fun onIdentityCreated(identity: IdentityWithBalance) {}

    override fun onTransferIdentityError(name: Option<String>) {}

    override fun onIdentityReceived(identity: IdentityWithBalance) {}

    override fun onDeleteIdentityError(name: Option<String>) {}

    override fun onRestoreIdentityError(name: Option<String>) {}
    override fun onIdentityRestored(identity: IdentityWithBalance) {}

    override fun onChangeIdentityNameError(name: Option<String>) {}

    override fun onIdentitiesUpdated(identities: Map<MemberId,
            BoardGame.Companion.IdentityWithBalance>
    ) {
    }

    override fun onPlayersInitialized(players: Collection<PlayerWithBalanceAndConnectionState>) {}
    override fun onPlayerAdded(addedPlayer: PlayerWithBalanceAndConnectionState) {}
    override fun onPlayerChanged(changedPlayer: PlayerWithBalanceAndConnectionState) {}
    override fun onPlayerRemoved(removedPlayer: PlayerWithBalanceAndConnectionState) {}
    override fun onPlayersUpdated(players: Map<MemberId, PlayerWithBalanceAndConnectionState>) {}

    override fun onTransferToPlayerError(name: Option<String>) {}

    override fun onTransfersInitialized(transfers: Collection<TransferWithCurrency>) {}
    override fun onTransferAdded(addedTransfer: TransferWithCurrency) {}
    override fun onTransfersChanged(changedTransfers: Collection<TransferWithCurrency>) {}
    override fun onTransfersUpdated(transfers: Map<TransactionId, TransferWithCurrency>) {}

    override fun onChangeGameNameError(name: Option<String>) {}
    override fun onGameNameChanged(name: Option<String>) {}
    override fun onQuitGameError() {}

}
