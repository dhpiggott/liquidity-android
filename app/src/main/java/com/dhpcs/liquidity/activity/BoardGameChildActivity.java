package com.dhpcs.liquidity.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.dhpcs.liquidity.BoardGame;
import com.dhpcs.liquidity.models.ZoneId;

public abstract class BoardGameChildActivity extends AppCompatActivity
        implements BoardGame.JoinStateListener {

    public static final String EXTRA_ZONE_ID_HOLDER = "zone_id_holder";
    public static final String EXTRA_ZONE_ID = "zone_id";

    private BoardGame.JoinRequestToken joinRequestToken;

    private BoardGame boardGame;

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

        boardGame.registerListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        boardGame.unregisterListener(this);
    }

    @Override
    public void onJoinStateChanged(BoardGame.JoinState joinState) {
        if (joinState != BoardGame.JOINED$.MODULE$) {
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isChangingConfigurations()) {
            if (!isFinishing()) {
                boardGame.unrequestJoin(joinRequestToken);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boardGame.requestJoin(joinRequestToken, false);
    }

    @Override
    public BoardGame.JoinRequestToken onRetainCustomNonConfigurationInstance() {
        return joinRequestToken;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            if (isFinishing()) {
                boardGame.unrequestJoin(joinRequestToken);
            }
        }
    }

}
