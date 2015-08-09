package com.dhpcs.liquidity.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.dhpcs.liquidity.MonopolyGame;
import com.dhpcs.liquidity.models.ZoneId;

public class MonopolyGameChildActivity extends AppCompatActivity
        implements MonopolyGame.JoinStateListener {

    public static final String EXTRA_ZONE_ID_HOLDER = "zone_id_holder";
    public static final String EXTRA_ZONE_ID = "zone_id";

    private MonopolyGame.JoinRequestToken joinRequestToken;

    private MonopolyGame monopolyGame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ZoneId zoneId = (ZoneId) getIntent()
                .getBundleExtra(EXTRA_ZONE_ID_HOLDER)
                .getSerializable(EXTRA_ZONE_ID);

        joinRequestToken = (MonopolyGame.JoinRequestToken) getLastCustomNonConfigurationInstance();

        if (joinRequestToken == null) {
            joinRequestToken = new MonopolyGame.JoinRequestToken() {
            };
        }

        monopolyGame = MonopolyGame.getInstance(zoneId);

        monopolyGame.registerListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        monopolyGame.unregisterListener(this);
    }

    @Override
    public void onJoinStateChanged(MonopolyGame.JoinState joinState) {
        if (joinState != MonopolyGame.JOINED$.MODULE$) {
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        monopolyGame.unregisterListener(this);
        if (!isChangingConfigurations()) {
            if (!isFinishing()) {
                monopolyGame.unrequestJoin(joinRequestToken);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        monopolyGame.requestJoin(joinRequestToken, false);
        monopolyGame.registerListener(this);
    }

    @Override
    public MonopolyGame.JoinRequestToken onRetainCustomNonConfigurationInstance() {
        return joinRequestToken;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            if (isFinishing()) {
                monopolyGame.unrequestJoin(joinRequestToken);
            }
        }
    }

}
