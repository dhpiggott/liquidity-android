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
import com.dhpcs.liquidity.activity.MainActivity.Companion.liveData
import kotlinx.android.synthetic.main.fragment_identities.*

class IdentitiesFragment : Fragment() {

    companion object {

        private class IdentitiesFragmentStatePagerAdapter
        internal constructor(fragmentManager: FragmentManager
        ) : FragmentStatePagerAdapter(fragmentManager) {

            internal val identities = arrayListOf<BoardGame.Companion.Identity>()

            override fun getCount(): Int = identities.size
            
            override fun getItemPosition(item: Any): Int = POSITION_NONE

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

        val identitiesFragmentStatePagerAdapter = IdentitiesFragmentStatePagerAdapter(
                fragmentManager!!
        )

        val pageChangeListener = object : ViewPager.SimpleOnPageChangeListener() {

            override fun onPageSelected(position: Int) {
                if (identitiesFragmentStatePagerAdapter.identities.isNotEmpty()) {
                    model.selectedIdentity(
                            MainActivity.Companion.Optional.Some(
                                    identitiesFragmentStatePagerAdapter.identities[position]
                            )
                    )
                }
            }

        }

        textview_empty.setOnClickListener {
            CreateIdentityDialogFragment.newInstance().show(
                    fragmentManager,
                    CreateIdentityDialogFragment.TAG
            )
        }
        viewpager_identities.adapter = identitiesFragmentStatePagerAdapter
        viewpager_identities.addOnPageChangeListener(pageChangeListener)

        model.boardGame.liveData { it.identitiesObservable }.observe(this, Observer {
            identitiesFragmentStatePagerAdapter.identities.clear()
            identitiesFragmentStatePagerAdapter.identities.addAll(
                    it.values.sortedWith(LiquidityApplication.identityComparator(requireContext()))
            )
            identitiesFragmentStatePagerAdapter.notifyDataSetChanged()
            if (identitiesFragmentStatePagerAdapter.identities.isEmpty()) {
                textview_empty.visibility = View.VISIBLE
                viewpager_identities.visibility = View.GONE
                model.selectedIdentity(
                        MainActivity.Companion.Optional.None
                )
            } else {
                textview_empty.visibility = View.GONE
                viewpager_identities.visibility = View.VISIBLE
                model.selectedIdentity(
                        MainActivity.Companion.Optional.Some(
                                identitiesFragmentStatePagerAdapter
                                        .identities[viewpager_identities.currentItem]
                        )
                )
            }
        })
        model.boardGame.liveData { it.addedIdentitiesObservable }.observe(this, Observer {
            viewpager_identities.currentItem = identitiesFragmentStatePagerAdapter
                    .identities.indexOf(it)
        })
        model.boardGame.liveData { it.identityRequiredObservable }.observe(this, Observer {
            if (fragmentManager!!.findFragmentByTag(CreateIdentityDialogFragment.TAG) == null) {
                CreateIdentityDialogFragment.newInstance().show(
                        fragmentManager!!,
                        CreateIdentityDialogFragment.TAG
                )
            }
        })
    }

}
