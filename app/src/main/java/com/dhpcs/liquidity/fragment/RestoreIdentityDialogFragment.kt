package com.dhpcs.liquidity.fragment

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatDialogFragment
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.BoardGameActivity
import com.dhpcs.liquidity.boardgame.BoardGame.Identity
import com.dhpcs.liquidity.boardgame.BoardGame.IdentityWithBalance
import com.dhpcs.liquidity.view.Identicon
import scala.collection.JavaConversions
import java.util.*

class RestoreIdentityDialogFragment : AppCompatDialogFragment() {

    companion object {

        interface Listener {

            fun onIdentityRestorationRequested(identity: Identity)

        }

        private class IdentitiesAdapter internal constructor(context: Context
        ) : ArrayAdapter<IdentityWithBalance>(context,
                R.layout.linearlayout_identity,
                R.id.textview_name
        ) {

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)

                val identity = getItem(position)

                val identiconId = view.findViewById<Identicon>(R.id.identicon_id)
                val textViewName = view.findViewById<TextView>(R.id.textview_name)
                val textViewBalance = view.findViewById<TextView>(R.id.textview_balance)

                val zoneId = identity!!.zoneId()
                val memberId = identity.member().id()
                val name = BoardGameActivity.formatNullable(
                        context,
                        identity.member().name()
                )
                val balance = BoardGameActivity.formatCurrencyValue(
                        context,
                        identity.balanceWithCurrency()._2(),
                        identity.balanceWithCurrency()._1()
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
                identities: scala.collection.Iterable<IdentityWithBalance>
        ): RestoreIdentityDialogFragment {
            val restoreIdentityDialogFragment = RestoreIdentityDialogFragment()
            val args = Bundle()
            args.putSerializable(
                    ARG_IDENTITIES,
                    ArrayList(JavaConversions.bufferAsJavaList(identities.toBuffer<Any>()))
            )
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
        val identitiesAdapter = IdentitiesAdapter(activity!!)
        val identities = arguments!!.getSerializable(ARG_IDENTITIES) as ArrayList<IdentityWithBalance>
        identitiesAdapter.addAll(identities)
        identitiesAdapter.sort(BoardGameActivity.playerComparator(activity))
        return AlertDialog.Builder(activity!!)
                .setTitle(R.string.choose_identity_to_restore)
                .setAdapter(identitiesAdapter) { _, which ->
                    listener?.onIdentityRestorationRequested(identitiesAdapter.getItem(which))
                }
                .create()
    }

}
