package com.dhpcs.liquidity.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat

import com.dhpcs.liquidity.R

class PreferencesFragment : Fragment() {

    companion object {

        class FromResourcePreferencesFragment : PreferenceFragmentCompat() {

            override fun onCreatePreferences(bundle: Bundle?, s: String?) {
                addPreferencesFromResource(R.xml.preferences)
            }

        }

    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_preferences, container, false)
    }

}
