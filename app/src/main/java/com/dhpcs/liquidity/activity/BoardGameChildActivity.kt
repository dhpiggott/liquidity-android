package com.dhpcs.liquidity.activity

import android.os.Bundle
import android.support.v7.app.AppCompatActivity

import com.dhpcs.liquidity.boardgame.BoardGame
import com.dhpcs.liquidity.boardgame.BoardGame.IdentityWithBalance
import com.dhpcs.liquidity.boardgame.BoardGame.PlayerWithBalanceAndConnectionState
import com.dhpcs.liquidity.boardgame.BoardGame.TransferWithCurrency
import com.dhpcs.liquidity.model.MemberId
import com.dhpcs.liquidity.model.TransactionId
import com.dhpcs.liquidity.model.ZoneId

import scala.Option
import scala.collection.Iterable
import scala.collection.immutable.Map

abstract class BoardGameChildActivity :
        AppCompatActivity(), BoardGame.GameActionListener, BoardGame.JoinStateListener {

    companion object {

        const val EXTRA_ZONE_ID_HOLDER = "zone_id_holder"
        const val EXTRA_ZONE_ID = "zone_id"

    }

    private var joinRequestToken: BoardGame.JoinRequestToken? = null
    private var retry: Boolean = false

    private var boardGame: BoardGame? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val zoneId = intent
                .getBundleExtra(EXTRA_ZONE_ID_HOLDER)
                .getSerializable(EXTRA_ZONE_ID) as ZoneId

        joinRequestToken = lastCustomNonConfigurationInstance as BoardGame.JoinRequestToken?

        if (joinRequestToken == null) joinRequestToken = BoardGame.JoinRequestToken()

        retry = savedInstanceState == null

        boardGame = BoardGame.getInstance(zoneId)

        boardGame!!.registerListener(this as BoardGame.JoinStateListener)
        boardGame!!.registerListener(this as BoardGame.GameActionListener)
    }

    override fun onStart() {
        super.onStart()
        boardGame!!.requestJoin(joinRequestToken, retry)
        retry = false
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) boardGame!!.unrequestJoin(joinRequestToken)
    }

    override fun onRetainCustomNonConfigurationInstance(): BoardGame.JoinRequestToken? {
        return joinRequestToken
    }

    override fun onDestroy() {
        super.onDestroy()
        boardGame!!.unregisterListener(this as BoardGame.GameActionListener)
        boardGame!!.unregisterListener(this as BoardGame.JoinStateListener)
    }

    override fun onJoinStateChanged(joinState: BoardGame.JoinState) {
        if (joinState !== BoardGame.`JOINED$`.`MODULE$`) finish()
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

    override fun onIdentitiesUpdated(identities: Map<MemberId, IdentityWithBalance>) {}

    override fun onPlayersInitialized(players: Iterable<PlayerWithBalanceAndConnectionState>) {}
    override fun onPlayerAdded(addedPlayer: PlayerWithBalanceAndConnectionState) {}
    override fun onPlayerChanged(changedPlayer: PlayerWithBalanceAndConnectionState) {}
    override fun onPlayerRemoved(removedPlayer: PlayerWithBalanceAndConnectionState) {}
    override fun onPlayersUpdated(players: Map<MemberId, PlayerWithBalanceAndConnectionState>) {}

    override fun onTransferToPlayerError(name: Option<String>) {}

    override fun onTransfersInitialized(transfers: scala.collection.Iterable<TransferWithCurrency>
    ) {
    }
    override fun onTransferAdded(addedTransfer: TransferWithCurrency) {}
    override fun onTransfersChanged(changedTransfers: Iterable<TransferWithCurrency>) {}
    override fun onTransfersUpdated(transfers: Map<TransactionId, TransferWithCurrency>) {}

    override fun onChangeGameNameError(name: Option<String>) {}
    override fun onGameNameChanged(name: Option<String>) {}
    override fun onQuitGameError() {}

}
