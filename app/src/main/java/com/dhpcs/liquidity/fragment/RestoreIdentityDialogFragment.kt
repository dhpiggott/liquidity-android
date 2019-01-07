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
import androidx.lifecycle.ViewModelProviders
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import com.dhpcs.liquidity.view.Identicon

class RestoreIdentityDialogFragment : AppCompatDialogFragment() {

    companion object {

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
                val name = LiquidityApplication.formatNullable(context, identity.name)
                val balance = LiquidityApplication.formatCurrencyValue(
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

        fun newInstance(): RestoreIdentityDialogFragment = RestoreIdentityDialogFragment()

    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)
        val identities = model.boardGame.hiddenIdentities
        val identitiesAdapter = IdentitiesAdapter(requireContext())
        identitiesAdapter.addAll(identities.values)
        identitiesAdapter.sort(LiquidityApplication.identityComparator(requireContext()))

        return AlertDialog.Builder(requireContext())
                .setTitle(R.string.choose_identity_to_restore)
                .setAdapter(identitiesAdapter) { _, which ->
                    model.execCommand(
                            model.boardGame.restoreIdentity(identitiesAdapter.getItem(which)!!)
                    ) {
                        getString(
                                R.string.restore_identity_error_format_string,
                                LiquidityApplication.formatNullable(
                                        requireContext(),
                                        identitiesAdapter.getItem(which)!!.name
                                )
                        )
                    }
                }
                .create()
    }

}
