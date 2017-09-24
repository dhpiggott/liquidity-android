package com.dhpcs.liquidity.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activity.BoardGameActivity;
import com.dhpcs.liquidity.boardgame.BoardGame.IdentityWithBalance;
import com.dhpcs.liquidity.model.MemberId;
import com.dhpcs.liquidity.model.ZoneId;
import com.dhpcs.liquidity.view.Identicon;

public class IdentityFragment extends Fragment {

    private static final String ARG_IDENTITY = "identity";

    public static IdentityFragment newInstance(IdentityWithBalance identity) {
        IdentityFragment identityFragment = new IdentityFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_IDENTITY, identity);
        identityFragment.setArguments(args);
        return identityFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_identity, container, false);

        IdentityWithBalance identity = (IdentityWithBalance) getArguments()
                .getSerializable(ARG_IDENTITY);

        Identicon identiconId = view.findViewById(R.id.identicon_id);
        TextView textViewName = view.findViewById(R.id.textview_name);
        TextView textViewBalance = view.findViewById(R.id.textview_balance);

        assert identity != null;
        ZoneId zoneId = identity.zoneId();
        MemberId memberId = identity.member().id();
        String name = BoardGameActivity.formatNullable(getActivity(), identity.member().name());
        String balance = BoardGameActivity.formatCurrencyValue(
                getActivity(),
                identity.balanceWithCurrency()._2(),
                identity.balanceWithCurrency()._1()
        );

        identiconId.show(zoneId, memberId);
        textViewName.setText(name);
        textViewBalance.setText(balance);

        return view;
    }

}
