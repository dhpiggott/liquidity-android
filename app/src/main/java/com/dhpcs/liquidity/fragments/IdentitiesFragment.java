package com.dhpcs.liquidity.fragments;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dhpcs.liquidity.ClientKey;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.Member;

import java.util.ArrayList;

public class IdentitiesFragment extends Fragment {

    private static class PlayersFragmentStatePagerAdapter extends FragmentStatePagerAdapter {

        private final ArrayList<Member> players = new ArrayList<>();

        public PlayersFragmentStatePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        public void addPlayer(Member player) {
            players.add(player);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return players.size();
        }

        @Override
        public android.support.v4.app.Fragment getItem(int position) {
            return IdentityFragment.newInstance(players.get(position));
        }

    }

    private PlayersFragmentStatePagerAdapter playersFragmentStatePagerAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_identities, container, false);

        playersFragmentStatePagerAdapter = new PlayersFragmentStatePagerAdapter(
                getFragmentManager()
        );
        // TODO
        playersFragmentStatePagerAdapter.addPlayer(
                new Member(
                        "Dave",
                        ClientKey.getInstance(getActivity()).getPublicKey()
                )
        );
        playersFragmentStatePagerAdapter.addPlayer(
                new Member(
                        "Banker",
                        ClientKey.getInstance(getActivity()).getPublicKey()
                )
        );

        ((ViewPager) view.findViewById(R.id.viewpager_identities)).setAdapter(
                playersFragmentStatePagerAdapter
        );

        return view;
    }

}
