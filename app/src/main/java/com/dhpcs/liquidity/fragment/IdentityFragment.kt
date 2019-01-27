package com.dhpcs.liquidity.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import com.dhpcs.liquidity.activity.MainActivity.Companion.liveData
import com.dhpcs.liquidity.view.Identicon
import kotlinx.android.synthetic.main.fragment_identity.*

class IdentityFragment : Fragment() {

    companion object {

        private const val ARG_IDENTITY_ID = "identity_id"

        fun newInstance(identity: BoardGame.Companion.Identity): IdentityFragment {
            val identityFragment = IdentityFragment()
            val args = Bundle()
            args.putString(ARG_IDENTITY_ID, identity.memberId)
            identityFragment.arguments = args
            return identityFragment
        }

    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_identity, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)
        val identityId = arguments!!.getString(ARG_IDENTITY_ID)!!

        model.boardGame.liveData {
            it.identitiesObservable.filter { identities ->
                identities.values.any { identity -> identity.memberId == identityId }
            }.map { identities -> identities.getValue(identityId) }
        }.observe(this, Observer {
            val zoneId = it.zoneId
            val memberId = it.memberId
            val name = LiquidityApplication.formatNullable(requireContext(), it.name)
            val balance = LiquidityApplication.formatCurrencyValue(
                    requireContext(),
                    it.currency,
                    it.balance
            )

            view.findViewById<Identicon>(R.id.identicon_id).show(zoneId, memberId)
            textview_name.text = name
            textview_balance.text = balance
        })
    }

}
