package com.dhpcs.liquidity.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.dhpcs.liquidity.BoardGame;
import com.dhpcs.liquidity.BoardGame.IdentityWithBalance;
import com.dhpcs.liquidity.BoardGame.PlayerWithBalanceAndConnectionState;
import com.dhpcs.liquidity.BoardGame.TransferWithCurrency;
import com.dhpcs.liquidity.models.MemberId;
import com.dhpcs.liquidity.models.TransactionId;
import com.dhpcs.liquidity.models.ZoneId;

import scala.Option;
import scala.collection.Iterable;
import scala.collection.immutable.Map;

public abstract class BoardGameChildActivity extends AppCompatActivity
        implements BoardGame.GameActionListener,
        BoardGame.JoinStateListener {

    public static final String EXTRA_ZONE_ID_HOLDER = "zone_id_holder";
    public static final String EXTRA_ZONE_ID = "zone_id";

    private BoardGame.JoinRequestToken joinRequestToken;

    private BoardGame boardGame;

    @Override
    public void onChangeGameNameError(Option<String> name) {
    }

    @Override
    public void onChangeIdentityNameError(Option<String> name) {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ZoneId zoneId = (ZoneId) getIntent()
                .getBundleExtra(EXTRA_ZONE_ID_HOLDER)
                .getSerializable(EXTRA_ZONE_ID);

        joinRequestToken = (BoardGame.JoinRequestToken) getLastCustomNonConfigurationInstance();

        if (joinRequestToken == null) {

            joinRequestToken = new BoardGame.JoinRequestToken();

        }

        boardGame = BoardGame.getInstance(zoneId);

        boardGame.registerListener((BoardGame.JoinStateListener) this);
        boardGame.registerListener((BoardGame.GameActionListener) this);
    }

    @Override
    public void onCreateGameError(Option<String> name) {
    }

    @Override
    public void onCreateIdentityAccountError(Option<String> name) {
    }

    @Override
    public void onCreateIdentityMemberError(Option<String> name) {
    }

    @Override
    public void onDeleteIdentityError(Option<String> name) {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        boardGame.unregisterListener((BoardGame.GameActionListener) this);
        boardGame.unregisterListener((BoardGame.JoinStateListener) this);
    }

    @Override
    public void onGameNameChanged(Option<String> name) {
    }

    @Override
    public void onIdentitiesUpdated(Map<MemberId, IdentityWithBalance> identities) {
    }

    @Override
    public void onIdentityCreated(IdentityWithBalance identity) {
    }

    @Override
    public void onIdentityReceived(IdentityWithBalance identity) {
    }

    @Override
    public void onIdentityRequired() {
    }

    @Override
    public void onIdentityRestored(IdentityWithBalance identity) {
    }

    @Override
    public void onJoinGameError() {
    }

    @Override
    public void onJoinStateChanged(BoardGame.JoinState joinState) {
        if (joinState != BoardGame.JOINED$.MODULE$) {
            finish();
        }
    }

    @Override
    public void onPlayerAdded(PlayerWithBalanceAndConnectionState addedPlayer) {
    }

    @Override
    public void onPlayerChanged(PlayerWithBalanceAndConnectionState changedPlayer) {
    }

    @Override
    public void onPlayerRemoved(PlayerWithBalanceAndConnectionState removedPlayer) {
    }

    @Override
    public void onPlayersInitialized(Iterable<PlayerWithBalanceAndConnectionState> players) {
    }

    @Override
    public void onPlayersUpdated(Map<MemberId, PlayerWithBalanceAndConnectionState> players) {
    }

    @Override
    public void onQuitGameError() {
    }

    @Override
    public void onRestoreIdentityError(Option<String> name) {
    }

    @Override
    public BoardGame.JoinRequestToken onRetainCustomNonConfigurationInstance() {
        return joinRequestToken;
    }

    @Override
    protected void onStart() {
        super.onStart();
        boardGame.requestJoin(joinRequestToken, false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            boardGame.unrequestJoin(joinRequestToken);
        }
    }

    @Override
    public void onTransferAdded(TransferWithCurrency addedTransfer) {
    }

    @Override
    public void onTransferIdentityError(Option<String> name) {
    }

    @Override
    public void onTransferToPlayerError(Option<String> name) {
    }

    @Override
    public void onTransfersChanged(Iterable<TransferWithCurrency> changedTransfers) {
    }

    @Override
    public void onTransfersInitialized(scala.collection.Iterable<TransferWithCurrency> transfers) {
    }

    @Override
    public void onTransfersUpdated(Map<TransactionId, TransferWithCurrency> transfers) {
    }

}
