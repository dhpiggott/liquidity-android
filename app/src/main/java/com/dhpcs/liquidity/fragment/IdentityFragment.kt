package com.dhpcs.liquidity.fragment

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.BoardGameActivity
import com.dhpcs.liquidity.boardgame.BoardGame.IdentityWithBalance
import com.dhpcs.liquidity.view.Identicon

class IdentityFragment : Fragment() {

    companion object {

        private const val ARG_IDENTITY = "identity"

        fun newInstance(identity: IdentityWithBalance): IdentityFragment {
            val identityFragment = IdentityFragment()
            val args = Bundle()
            args.putSerializable(ARG_IDENTITY, identity)
            identityFragment.arguments = args
            return identityFragment
        }

    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_identity, container, false)

        val identity = arguments!!.getSerializable(ARG_IDENTITY) as IdentityWithBalance

        val identiconId = view.findViewById<Identicon>(R.id.identicon_id)
        val textViewName = view.findViewById<TextView>(R.id.textview_name)
        val textViewBalance = view.findViewById<TextView>(R.id.textview_balance)

        val zoneId = identity.zoneId()
        val memberId = identity.member().id()
        val name = BoardGameActivity.formatNullable(activity!!, identity.member().name())
        val balance = BoardGameActivity.formatCurrencyValue(
                activity!!,
                identity.balanceWithCurrency()._2(),
                identity.balanceWithCurrency()._1()
        )

        identiconId.show(zoneId, memberId)
        textViewName.text = name
        textViewBalance.text = balance

        return view
    }

}
