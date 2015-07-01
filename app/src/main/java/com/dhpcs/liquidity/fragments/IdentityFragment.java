package com.dhpcs.liquidity.fragments;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dhpcs.liquidity.MonopolyGame.IdentityWithBalance;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;
import com.dhpcs.liquidity.models.MemberId;

import scala.Tuple2;

public class IdentityFragment extends Fragment {

    private static final String ARG_IDENTITY = "identity";

    public static IdentityFragment newInstance(Tuple2<MemberId, IdentityWithBalance> identity) {
        IdentityFragment identityFragment = new IdentityFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_IDENTITY, identity);
        identityFragment.setArguments(args);
        return identityFragment;
    }

    private Tuple2<MemberId, IdentityWithBalance> identity;

    @Override
    @SuppressWarnings("unchecked")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        identity = (Tuple2<MemberId, IdentityWithBalance>)
                getArguments().getSerializable(ARG_IDENTITY);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_identity, container, false);

        TextView textViewPlayerName = (TextView) view.findViewById(R.id.textview_player_name);
        TextView textViewPlayerBalance = (TextView) view.findViewById(R.id.textview_player_balance);

        String name = identity._2().member().name();
        String balance = MonopolyGameActivity.formatBalance(
                identity._2().balanceWithCurrency()
        );

        textViewPlayerName.setText(name);
        textViewPlayerBalance.setText(balance);

        return view;
    }

}
