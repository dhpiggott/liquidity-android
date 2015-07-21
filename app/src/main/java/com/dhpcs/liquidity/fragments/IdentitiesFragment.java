package com.dhpcs.liquidity.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dhpcs.liquidity.MonopolyGame.Identity;
import com.dhpcs.liquidity.MonopolyGame.IdentityWithBalance;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;
import com.dhpcs.liquidity.models.MemberId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import scala.collection.Iterator;

public class IdentitiesFragment extends Fragment implements OnPageChangeListener {

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

        public void sort(Comparator<Identity> comparator) {
            Collections.sort(identities, comparator);
        }

    }

    private IdentitiesFragmentStatePagerAdapter identitiesFragmentStatePagerAdapter;
    private ViewPager viewPagerIdentities;

    private Listener listener;

    public IdentityWithBalance getIdentity(int page) {
        if (identitiesFragmentStatePagerAdapter.getCount() == 0) {
            return null;
        } else {
            return identitiesFragmentStatePagerAdapter.get(page);
        }
    }

    public int getPage(IdentityWithBalance identity) {
        if (identitiesFragmentStatePagerAdapter.getCount() == 0) {
            return 0;
        } else {
            return identitiesFragmentStatePagerAdapter.getPosition(identity);
        }
    }

    public int getSelectedPage() {
        return viewPagerIdentities.getCurrentItem();
    }

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        identitiesFragmentStatePagerAdapter = new IdentitiesFragmentStatePagerAdapter(
                getFragmentManager()
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_identities, container, false);

        viewPagerIdentities = (ViewPager) view.findViewById(R.id.viewpager_identities);
        viewPagerIdentities.setAdapter(identitiesFragmentStatePagerAdapter);
        viewPagerIdentities.addOnPageChangeListener(this);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewPagerIdentities.removeOnPageChangeListener(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    // TODO: Add placeholder fragment with instruction to create identity
    public void onIdentitiesChanged(scala.collection.immutable.Map<MemberId, IdentityWithBalance>
                                            identities) {
        IdentityWithBalance selectedIdentity = getIdentity(getSelectedPage());
        identitiesFragmentStatePagerAdapter.clear();
        Iterator<IdentityWithBalance> iterator = identities.valuesIterator();
        while (iterator.hasNext()) {
            identitiesFragmentStatePagerAdapter.add(iterator.next());
        }
        identitiesFragmentStatePagerAdapter.sort(MonopolyGameActivity.identityComparator);
        identitiesFragmentStatePagerAdapter.notifyDataSetChanged();
        if (selectedIdentity != null && identities.contains(selectedIdentity.memberId())) {
            viewPagerIdentities.setCurrentItem(
                    identitiesFragmentStatePagerAdapter.getPosition(
                            identities.apply(selectedIdentity.memberId())
                    ),
                    false
            );
        }
    }

    @Override
    public void onPageSelected(int position) {
        if (listener != null) {
            listener.onIdentityPageSelected(position);
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    public void setSelectedPage(int page) {
        viewPagerIdentities.setCurrentItem(page);
    }

}
