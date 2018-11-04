package com.dhpcs.liquidity.fragment

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.BoardGameActivity
import com.dhpcs.liquidity.view.Identicon
import java.util.*

class RestoreIdentityDialogFragment : AppCompatDialogFragment() {

    companion object {

        interface Listener {

            fun onIdentityRestorationRequested(identity: BoardGame.Companion.Identity)

        }

        private class IdentitiesAdapter internal constructor(context: Context
        ) : ArrayAdapter<BoardGame.Companion.Identity>(context,
                R.layout.linearlayout_identity,
                R.id.textview_name
        ) {

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)

                val identity = getItem(position)

                val identiconId = view.findViewById<Identicon>(R.id.identicon_id)
                val textViewName = view.findViewById<TextView>(R.id.textview_name)
                val textViewBalance = view.findViewById<TextView>(R.id.textview_balance)

                val zoneId = identity!!.zoneId
                val memberId = identity.memberId
                val name = BoardGameActivity.formatNullable(context, identity.name)
                val balance = BoardGameActivity.formatCurrencyValue(
                        context,
                        identity.currency,
                        identity.balance
                )

                identiconId.show(zoneId, memberId)
                textViewName.text = name
                textViewBalance.text = balance

                return view
            }

        }

        const val TAG = "restore_identity_dialog_fragment"

        private const val ARG_IDENTITIES = "identities"

        fun newInstance(
                identities: ArrayList<BoardGame.Companion.Identity>
        ): RestoreIdentityDialogFragment {
            val restoreIdentityDialogFragment = RestoreIdentityDialogFragment()
            val args = Bundle()
            args.putSerializable(ARG_IDENTITIES, identities)
            restoreIdentityDialogFragment.arguments = args
            return restoreIdentityDialogFragment
        }

    }

    private var listener: Listener? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        listener = context as Listener?
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val identitiesAdapter = IdentitiesAdapter(requireContext())
        val identities = arguments!!.getSerializable(ARG_IDENTITIES) as
                ArrayList<BoardGame.Companion.Identity>
        identitiesAdapter.addAll(identities)
        identitiesAdapter.sort(BoardGameActivity.identityComparator(requireContext()))
        return AlertDialog.Builder(requireContext())
                .setTitle(R.string.choose_identity_to_restore)
                .setAdapter(identitiesAdapter) { _, which ->
                    listener?.onIdentityRestorationRequested(identitiesAdapter.getItem(which))
                }
                .create()
    }

}
