package com.dhpcs.liquidity.fragment;

import android.os.Bundle;
import android.support.v14.preference.PreferenceFragment;

import com.dhpcs.liquidity.R;

public class PreferencesFragment extends PreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.preferences);
    }

}
