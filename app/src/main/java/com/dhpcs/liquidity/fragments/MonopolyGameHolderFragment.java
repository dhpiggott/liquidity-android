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
        // TODO: Review https://code.google.com/p/android/issues/detail?id=22564 and
        // https://android.googlesource.com/platform/development/+/master/samples/ApiDemos/src/com/example/android/apis/app/FragmentRetainInstance.java,
        // fix by pushing containing activity code into fragment and making it hold this holder.
        setRetainInstance(true);
        monopolyGame.connectCreateAndOrJoinZone();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        monopolyGame.quitAndOrDisconnect();
    }

}
