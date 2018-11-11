package com.dhpcs.liquidity.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.view.Identicon
import kotlinx.android.synthetic.main.fragment_identity.*

class IdentityFragment : Fragment() {

    companion object {

        private const val ARG_IDENTITY = "identity"

        fun newInstance(identity: BoardGame.Companion.Identity): IdentityFragment {
            val identityFragment = IdentityFragment()
            val args = Bundle()
            args.putParcelable(ARG_IDENTITY, identity)
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
        val identity = arguments!!.getParcelable<BoardGame.Companion.Identity>(ARG_IDENTITY)!!

        val zoneId = identity.zoneId
        val memberId = identity.memberId
        val name = LiquidityApplication.formatNullable(requireContext(), identity.name)
        val balance = LiquidityApplication.formatCurrencyValue(
                requireContext(),
                identity.currency,
                identity.balance
        )

        view.findViewById<Identicon>(R.id.identicon_id).show(zoneId, memberId)
        textview_name.text = name
        textview_balance.text = balance
    }

}
