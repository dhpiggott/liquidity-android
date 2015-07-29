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
import android.widget.TextView;

import com.dhpcs.liquidity.MonopolyGame.Identity;
import com.dhpcs.liquidity.MonopolyGame.IdentityWithBalance;
import com.dhpcs.liquidity.MonopolyGame.Player;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activities.MonopolyGameActivity;
import com.dhpcs.liquidity.models.MemberId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import scala.collection.Iterator;

public class IdentitiesFragment extends Fragment {

    public interface Listener {

        void onIdentityPageSelected(int page);

        void onNoIdentitiesTextClicked();

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

        public void sort(Comparator<Player> comparator) {
            Collections.sort(identities, comparator);
        }

    }

    private static final String STATE_SELECTED_IDENTITY = "selected_identity";

    private final ViewPager.OnPageChangeListener pageChangeListener =
            new ViewPager.SimpleOnPageChangeListener() {

                @Override
                public void onPageSelected(int position) {
                    selectedIdentity = getIdentity(position);
                    if (listener != null) {
                        listener.onIdentityPageSelected(position);
                    }
                }


            };

    private IdentitiesFragmentStatePagerAdapter identitiesFragmentStatePagerAdapter;

    private TextView textViewEmpty;
    private ViewPager viewPagerIdentities;

    private Identity selectedIdentity;

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
        listener = (Listener) activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        identitiesFragmentStatePagerAdapter = new IdentitiesFragmentStatePagerAdapter(
                getFragmentManager()
        );

        if (savedInstanceState != null) {
            selectedIdentity =
                    (Identity) savedInstanceState.getSerializable(STATE_SELECTED_IDENTITY);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_identities, container, false);

        textViewEmpty = (TextView) view.findViewById(R.id.textview_empty);
        viewPagerIdentities = (ViewPager) view.findViewById(R.id.viewpager_identities);

        textViewEmpty.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onNoIdentitiesTextClicked();
                }
            }

        });
        viewPagerIdentities.setAdapter(identitiesFragmentStatePagerAdapter);
        viewPagerIdentities.addOnPageChangeListener(pageChangeListener);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewPagerIdentities.removeOnPageChangeListener(pageChangeListener);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public void onIdentitiesUpdated(
            scala.collection.immutable.Map<MemberId, IdentityWithBalance> identities) {
        identitiesFragmentStatePagerAdapter.clear();
        Iterator<IdentityWithBalance> iterator = identities.valuesIterator();
        while (iterator.hasNext()) {
            identitiesFragmentStatePagerAdapter.add(iterator.next());
        }
        identitiesFragmentStatePagerAdapter.sort(MonopolyGameActivity.playerComparator);
        identitiesFragmentStatePagerAdapter.notifyDataSetChanged();
        textViewEmpty.setVisibility(
                identitiesFragmentStatePagerAdapter.getCount() == 0 ? View.VISIBLE : View.GONE
        );
        viewPagerIdentities.setVisibility(
                identitiesFragmentStatePagerAdapter.getCount() == 0 ? View.GONE : View.VISIBLE
        );
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
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_SELECTED_IDENTITY, selectedIdentity);
    }

    public void setSelectedPage(int page) {
        viewPagerIdentities.setCurrentItem(page);
    }

}
