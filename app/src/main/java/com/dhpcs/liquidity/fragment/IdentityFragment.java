package com.dhpcs.liquidity.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dhpcs.liquidity.MonopolyGame.IdentityWithBalance;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activity.MonopolyGameActivity;
import com.dhpcs.liquidity.models.Identifier;
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

        Identicon identiconId = (Identicon) view.findViewById(R.id.identicon_id);
        TextView textViewName = (TextView) view.findViewById(R.id.textview_name);
        TextView textViewBalance = (TextView) view.findViewById(R.id.textview_balance);

        Identifier identifier = identity.memberId();
        String name = identity.member().name();
        String balance = MonopolyGameActivity.formatCurrencyValue(
                getActivity(),
                identity.balanceWithCurrency()._2(),
                identity.balanceWithCurrency()._1()
        );

        identiconId.show(identifier);
        textViewName.setText(name);
        textViewBalance.setText(balance);

        return view;
    }

}
