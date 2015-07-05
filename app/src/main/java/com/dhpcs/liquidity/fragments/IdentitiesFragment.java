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

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import scala.collection.Iterator;

public class IdentitiesFragment extends Fragment {

    public interface Listener {

        void onIdentityPageSelected(int page);

    }

    private static class IdentitiesFragmentStatePagerAdapter extends FragmentStatePagerAdapter {

        private final ArrayList<IdentityWithBalance> identities = new ArrayList<>();

        public IdentitiesFragmentStatePagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        public void add(IdentityWithBalance identity) {
            identities.add(identity);
        }

        public void clear() {
            identities.clear();
        }

        public IdentityWithBalance get(int position) {
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

        public int getPosition(IdentityWithBalance identity) {
            return identities.indexOf(identity);
        }

        public void sort(Comparator<IdentityWithBalance> comparator) {
            Collections.sort(identities, comparator);
        }

    }

    private static final Comparator<IdentityWithBalance> identityComparator =
            new Comparator<IdentityWithBalance>() {

                private final Collator collator = Collator.getInstance();

                @Override
                public int compare(IdentityWithBalance lhs, IdentityWithBalance rhs) {
                    return collator.compare(
                            lhs.member().name(),
                            rhs.member().name()
                    );
                }

            };

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

    public IdentityWithBalance getIdentity(int page) {
        if (playersFragmentStatePagerAdapter.getCount() == 0) {
            return null;
        } else {
            return playersFragmentStatePagerAdapter.get(page);
        }
    }

    public int getPage(IdentityWithBalance identity) {
        if (playersFragmentStatePagerAdapter.getCount() == 0) {
            return 0;
        } else {
            return playersFragmentStatePagerAdapter.getPosition(identity);
        }
    }

    public int getSelectedPage() {
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

    public void onIdentitiesChanged(scala.collection.immutable.Map<MemberId, IdentityWithBalance>
                                            identities) {
        IdentityWithBalance selectedIdentity = getIdentity(getSelectedPage());
        playersFragmentStatePagerAdapter.clear();
        Iterator<IdentityWithBalance> iterator = identities.valuesIterator();
        while (iterator.hasNext()) {
            playersFragmentStatePagerAdapter.add(iterator.next());
        }
        playersFragmentStatePagerAdapter.sort(identityComparator);
        playersFragmentStatePagerAdapter.notifyDataSetChanged();
        if (selectedIdentity != null && identities.contains(selectedIdentity.memberId())) {
            viewPagerIdentities.setCurrentItem(
                    playersFragmentStatePagerAdapter.getPosition(
                            identities.apply(selectedIdentity.memberId())
                    ),
                    false
            );
        }
    }

    public void setSelectedPage(int page) {
        viewPagerIdentities.setCurrentItem(page);
    }

}
