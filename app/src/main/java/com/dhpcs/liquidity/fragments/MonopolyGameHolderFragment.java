package com.dhpcs.liquidity.fragments;


import android.annotation.SuppressLint;
import android.app.Fragment;
import android.os.Bundle;

import com.dhpcs.liquidity.MonopolyGame;

@SuppressLint("ValidFragment")
public class MonopolyGameHolderFragment extends Fragment {

    private final MonopolyGame monopolyGame;

    public MonopolyGameHolderFragment(MonopolyGame monopolyGame) {
        this.monopolyGame = monopolyGame;
    }

    public MonopolyGame getMonopolyGame() {
        return monopolyGame;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        monopolyGame.connectCreateAndOrJoinZone();
    }

    @Override
    public void onDestroy() {
        monopolyGame.quitAndOrDisconnect();
        super.onDestroy();
    }

}
