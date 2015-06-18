package com.dhpcs.liquidity.fragments;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.Member;

public class IdentityFragment extends Fragment {

    private static final String ARG_MEMBER = "member";

    public static IdentityFragment newInstance(Member member) {
        IdentityFragment identityFragment = new IdentityFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_MEMBER, member);
        identityFragment.setArguments(args);
        return identityFragment;
    }

    private Member member;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        member = (Member) getArguments().getSerializable(ARG_MEMBER);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_identity, container, false);

        TextView textViewPlayerName = (TextView) view.findViewById(R.id.textview_player_name);
        TextView textViewPlayerBalance = (TextView) view.findViewById(R.id.textview_player_balance);

        textViewPlayerName.setText(member.name());
        // TODO
        textViewPlayerBalance.setText("15, 000");

        return view;
    }

}
