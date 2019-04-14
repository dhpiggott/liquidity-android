package com.dhpcs.liquidity.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.viewpager.widget.ViewPager
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import com.dhpcs.liquidity.activity.MainActivity.Companion.observableLiveData
import kotlinx.android.synthetic.main.fragment_identities.*

class IdentitiesFragment : Fragment() {

    companion object {

        private class IdentitiesFragmentStatePagerAdapter
        internal constructor(
                fragmentManager: FragmentManager,
                val identities: List<BoardGame.Companion.Identity>
        ) : FragmentStatePagerAdapter(fragmentManager) {

            override fun getCount(): Int = identities.size

            override fun getItem(position: Int): Fragment {
                return IdentityFragment.newInstance(identities[position])
            }

        }

    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_identities, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)

        textview_empty.setOnClickListener {
            CreateIdentityDialogFragment.newInstance().show(
                    fragmentManager,
                    CreateIdentityDialogFragment.TAG
            )
        }

        val pageChangeListener = object : ViewPager.SimpleOnPageChangeListener() {

            override fun onPageSelected(position: Int) {
                val adapter = viewpager_identities.adapter as? IdentitiesFragmentStatePagerAdapter
                if (adapter != null) {
                    model.selectedIdentity(
                            MainActivity.Companion.Optional.Some(adapter.identities[position])
                    )
                }
            }

        }
        viewpager_identities.addOnPageChangeListener(pageChangeListener)

        model.boardGame.observableLiveData {
            it.identitiesObservable
        }.observe(this, Observer {
            if (it.isEmpty()) {
                textview_empty.visibility = View.VISIBLE
                viewpager_identities.visibility = View.GONE
                viewpager_identities.adapter = null
                model.selectedIdentity(
                        MainActivity.Companion.Optional.None
                )
            } else {
                textview_empty.visibility = View.GONE
                viewpager_identities.visibility = View.VISIBLE
                val identitiesFragmentStatePagerAdapter = IdentitiesFragmentStatePagerAdapter(
                        fragmentManager!!,
                        it.values.sortedWith(
                                LiquidityApplication.identityComparator(requireContext())
                        )
                )
                viewpager_identities.adapter = identitiesFragmentStatePagerAdapter
                when (val selectedIdentity = model.selectedIdentity) {
                    is MainActivity.Companion.Optional.None -> {
                        model.selectedIdentity(
                                MainActivity.Companion.Optional.Some(
                                        identitiesFragmentStatePagerAdapter.identities.first()
                                )
                        )
                    }
                    is MainActivity.Companion.Optional.Some -> {
                        val index = identitiesFragmentStatePagerAdapter.identities.indexOf(
                                selectedIdentity.value
                        )
                        viewpager_identities.currentItem = index
                        if (index == -1) {
                            // Because setCurrentItem doesn't dispatch to onPageSelected in this
                            // .case
                            model.selectedIdentity(
                                    MainActivity.Companion.Optional.Some(
                                            identitiesFragmentStatePagerAdapter.identities.first()
                                    )
                            )
                        }
                    }
                }
            }
        })
        model.boardGame.observableLiveData {
            it.addedIdentitiesObservable
        }.observe(this, Observer {
            val adapter = viewpager_identities.adapter as? IdentitiesFragmentStatePagerAdapter
            if (adapter != null) {
                viewpager_identities.currentItem = adapter.identities.indexOf(it)
            }
        })
        model.boardGame.observableLiveData {
            it.identityRequiredObservable
        }.observe(this, Observer {
            if (fragmentManager!!.findFragmentByTag(CreateIdentityDialogFragment.TAG) == null) {
                CreateIdentityDialogFragment.newInstance().show(
                        fragmentManager!!,
                        CreateIdentityDialogFragment.TAG
                )
            }
        })
    }

}
