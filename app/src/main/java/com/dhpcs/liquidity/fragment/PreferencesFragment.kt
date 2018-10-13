package com.dhpcs.liquidity.fragment

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

import com.dhpcs.liquidity.R

class PreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences)
    }

}
