package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dhpcs.liquidity.MonopolyGame.IdentityWithBalance;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.MemberId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import scala.Tuple2;
import scala.collection.Iterator;

public class IdentitiesFragment extends Fragment {

    public interface Listener {

        void onIdentityPageSelected(int page);

    }

    private static final Comparator<Tuple2<MemberId, IdentityWithBalance>> identityComparator =
            new Comparator<Tuple2<MemberId, IdentityWithBalance>>() {

                @Override
                public int compare(Tuple2<MemberId, IdentityWithBalance> lhs,
                                   Tuple2<MemberId, IdentityWithBalance> rhs) {
                    return lhs._2().member().name().compareToIgnoreCase(rhs._2().member().name());
                }

            };

    private static class IdentitiesFragmentStatePagerAdapter extends FragmentStatePagerAdapter {

        private final ArrayList<Tuple2<MemberId, IdentityWithBalance>> identities
                = new ArrayList<>();

        public IdentitiesFragmentStatePagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        public void add(Tuple2<MemberId, IdentityWithBalance> identity) {
            identities.add(identity);
        }

        public void clear() {
            identities.clear();
        }

        public Tuple2<MemberId, IdentityWithBalance> get(int position) {
            return identities.get(position);
        }

        @Override
        public int getCount() {
            return identities.size();
        }

        @Override
        public Fragment getItem(int position) {
            return IdentityFragment.newInstance(identities.get(position));
        }

        @Override
        public int getItemPosition(Object item) {
            return POSITION_NONE;
        }

        public int getPosition(Tuple2<MemberId, IdentityWithBalance> identity) {
            return identities.indexOf(identity);
        }

        public void sort(Comparator<Tuple2<MemberId, IdentityWithBalance>> comparator) {
            Collections.sort(identities, comparator);
        }

    }

    private IdentitiesFragmentStatePagerAdapter playersFragmentStatePagerAdapter;
    private ViewPager viewPagerIdentities;

    private Listener listener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (Listener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement IdentitiesFragment.Listener");
        }
    }

    public MemberId getIdentityId(int page) {
        if (playersFragmentStatePagerAdapter.getCount() == 0) {
            return null;
        } else {
            return playersFragmentStatePagerAdapter.get(page)._1();
        }
    }

    public int getSelectedIdentityPage() {
        return viewPagerIdentities.getCurrentItem();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        playersFragmentStatePagerAdapter = new IdentitiesFragmentStatePagerAdapter(
                getFragmentManager()
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_identities, container, false);

        viewPagerIdentities = (ViewPager) view.findViewById(R.id.viewpager_identities);
        viewPagerIdentities.setAdapter(playersFragmentStatePagerAdapter);
        viewPagerIdentities.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {

            @Override
            public void onPageSelected(int position) {
                if (listener != null) {
                    listener.onIdentityPageSelected(position);
                }
            }

        });

        return view;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public void onIdentitiesChanged(scala.collection.immutable
                                            .Map<MemberId, IdentityWithBalance>
                                            identities) {
        MemberId selectedIdentityId = getIdentityId(getSelectedIdentityPage());
        playersFragmentStatePagerAdapter.clear();
        Iterator<Tuple2<MemberId, IdentityWithBalance>> iterator = identities
                .iterator();
        while (iterator.hasNext()) {
            Tuple2<MemberId, IdentityWithBalance> changedIdentity = iterator.next();
            playersFragmentStatePagerAdapter.add(changedIdentity);
        }
        playersFragmentStatePagerAdapter.sort(identityComparator);
        playersFragmentStatePagerAdapter.notifyDataSetChanged();
        if (selectedIdentityId != null) {
            viewPagerIdentities.setCurrentItem(
                    playersFragmentStatePagerAdapter.getPosition(
                            Tuple2.apply(
                                    selectedIdentityId,
                                    identities.apply(selectedIdentityId)
                            )
                    ),
                    false
            );
        }
    }

}
