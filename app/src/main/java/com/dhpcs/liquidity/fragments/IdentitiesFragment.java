package com.dhpcs.liquidity.fragments;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.Member;
import com.dhpcs.liquidity.models.MemberId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

public class IdentitiesFragment extends Fragment {

    private static class IdentityItem {

        public final String name;
        public final MemberId memberId;

        public IdentityItem(String name, MemberId memberId) {
            this.name = name;
            this.memberId = memberId;
        }

        @Override
        public String toString() {
            return this.name;
        }

    }

    private final Comparator<IdentityItem> identityItemComparator = new Comparator<IdentityItem>() {

        @Override
        public int compare(IdentityItem lhs, IdentityItem rhs) {
            return lhs.name.compareTo(rhs.name);
        }

    };

    private static class IdentitiesFragmentStatePagerAdapter extends FragmentStatePagerAdapter {

        public IdentitiesFragmentStatePagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        private final ArrayList<IdentityItem> identityItems = new ArrayList<>();

        public void add(IdentityItem identityItem) {
            identityItems.add(identityItem);
        }

        public void clear() {
            identityItems.clear();
        }

        public void sort(Comparator<IdentityItem> comparator) {
            Collections.sort(identityItems, comparator);
        }

        @Override
        public int getCount() {
            return identityItems.size();
        }

        @Override
        public Fragment getItem(int position) {
            return IdentityFragment.newInstance(identityItems.get(position).memberId);
        }

    }

    private IdentitiesFragmentStatePagerAdapter playersFragmentStatePagerAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_identities, container, false);

        playersFragmentStatePagerAdapter = new IdentitiesFragmentStatePagerAdapter(
                getFragmentManager()
        );

        ((ViewPager) view.findViewById(R.id.viewpager_identities)).setAdapter(
                playersFragmentStatePagerAdapter
        );

        return view;
    }

    public void onIdentitiesChanged(Map<MemberId, Member> identities) {
        playersFragmentStatePagerAdapter.clear();
        for (Map.Entry<MemberId, Member> memberIdMemberEntry : identities.entrySet()) {
            playersFragmentStatePagerAdapter.add(
                    new IdentityItem(
                            memberIdMemberEntry.getValue().name(),
                            memberIdMemberEntry.getKey()
                    )
            );
        }
        playersFragmentStatePagerAdapter.sort(identityItemComparator);
        playersFragmentStatePagerAdapter.notifyDataSetChanged();
    }

}
