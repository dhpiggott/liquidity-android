package com.dhpcs.liquidity.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.BoardGameActivity
import java.util.*

class IdentitiesFragment : Fragment() {

    companion object {

        interface Listener {

            fun onIdentityPageSelected(page: Int)

            fun onNoIdentitiesTextClicked()

        }

        private const val STATE_SELECTED_IDENTITY = "selected_identity"

        private class IdentitiesFragmentStatePagerAdapter
        internal constructor(fragmentManager: FragmentManager
        ) : FragmentStatePagerAdapter(fragmentManager) {

            private val identities = ArrayList<BoardGame.Companion.Identity>()

            override fun getCount(): Int = identities.size

            override fun getItem(position: Int): Fragment {
                return IdentityFragment.newInstance(identities[position])
            }

            override fun getItemPosition(item: Any): Int = PagerAdapter.POSITION_NONE

            internal fun add(identity: BoardGame.Companion.Identity) {
                identities.add(identity)
            }

            internal fun clear() = identities.clear()

            internal operator fun get(position: Int): BoardGame.Companion.Identity {
                return identities[position]
            }

            internal fun getPosition(identity: BoardGame.Companion.Identity): Int {
                return identities.indexOf(identity)
            }

            internal fun sort(comparator: Comparator<BoardGame.Companion.Identity>) {
                Collections.sort(identities, comparator)
            }

        }

    }

    private val pageChangeListener = object : ViewPager.SimpleOnPageChangeListener() {

        override fun onPageSelected(position: Int) {
            selectedIdentity = getIdentity(position)
            listener?.onIdentityPageSelected(position)
        }

    }

    private var identitiesFragmentStatePagerAdapter: IdentitiesFragmentStatePagerAdapter? = null

    private var textViewEmpty: TextView? = null
    private var viewPagerIdentities: ViewPager? = null

    private var selectedIdentity: BoardGame.Companion.Identity? = null

    private var listener: Listener? = null

    var selectedPage: Int
        get() = viewPagerIdentities!!.currentItem
        set(page) {
            viewPagerIdentities!!.currentItem = page
        }

    fun getIdentity(page: Int): BoardGame.Companion.Identity? {
        return if (identitiesFragmentStatePagerAdapter!!.count == 0) {
            null
        } else {
            identitiesFragmentStatePagerAdapter!![page]
        }
    }

    fun getPage(identity: BoardGame.Companion.Identity): Int {
        return if (identitiesFragmentStatePagerAdapter!!.count == 0) {
            0
        } else {
            identitiesFragmentStatePagerAdapter!!.getPosition(identity)
        }
    }

    fun onIdentitiesUpdated(identities: Map<String, BoardGame.Companion.Identity>) {
        identitiesFragmentStatePagerAdapter!!.clear()
        identities.values.forEach {
            identitiesFragmentStatePagerAdapter!!.add(it)
        }
        identitiesFragmentStatePagerAdapter!!.sort(
                BoardGameActivity.identityComparator(requireContext())
        )
        identitiesFragmentStatePagerAdapter!!.notifyDataSetChanged()
        textViewEmpty!!.visibility = if (identitiesFragmentStatePagerAdapter!!.count == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
        viewPagerIdentities!!.visibility = if (identitiesFragmentStatePagerAdapter!!.count == 0) {
            View.GONE
        } else {
            View.VISIBLE
        }

        if (selectedIdentity != null && identities.contains(selectedIdentity!!.memberId)) {
            viewPagerIdentities!!.setCurrentItem(
                    identitiesFragmentStatePagerAdapter!!.getPosition(
                            identities[selectedIdentity!!.memberId]!!
                    ),
                    false
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        identitiesFragmentStatePagerAdapter = IdentitiesFragmentStatePagerAdapter(
                fragmentManager!!
        )

        selectedIdentity = savedInstanceState?.getSerializable(STATE_SELECTED_IDENTITY) as
                BoardGame.Companion.Identity?
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_identities, container, false)

        textViewEmpty = view.findViewById(R.id.textview_empty)
        viewPagerIdentities = view.findViewById(R.id.viewpager_identities)

        textViewEmpty!!.setOnClickListener {
            listener?.onNoIdentitiesTextClicked()
        }
        viewPagerIdentities!!.adapter = identitiesFragmentStatePagerAdapter
        viewPagerIdentities!!.addOnPageChangeListener(pageChangeListener)

        return view
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        listener = context as Listener?
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewPagerIdentities!!.removeOnPageChangeListener(pageChangeListener)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(STATE_SELECTED_IDENTITY, selectedIdentity)
    }

}
