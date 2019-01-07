package com.dhpcs.liquidity.fragment

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.lifecycle.ViewModelProviders
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity

class ConfirmIdentityDeletionDialogFragment : AppCompatDialogFragment() {

    companion object {

        const val TAG = "confirm_identity_deletion_dialog_fragment"

        private const val ARG_IDENTITY_ID = "identity_id"

        fun newInstance(identity: BoardGame.Companion.Identity
        ): ConfirmIdentityDeletionDialogFragment {
            val confirmIdentityDeletionDialogFragment = ConfirmIdentityDeletionDialogFragment()
            val args = Bundle()
            args.putString(ARG_IDENTITY_ID, identity.memberId)
            confirmIdentityDeletionDialogFragment.arguments = args
            return confirmIdentityDeletionDialogFragment
        }

    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)
        val identity = model.boardGame.identities[arguments!!.getString(ARG_IDENTITY_ID)!!]!!
        return AlertDialog.Builder(requireContext())
                .setTitle(
                        getString(
                                R.string.delete_identity_title_format_string,
                                LiquidityApplication.formatNullable(requireContext(), identity.name)
                        )
                )
                .setMessage(R.string.delete_identity_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    model.execCommand(
                            model.boardGame.deleteIdentity(identity)
                    ) {
                        getString(
                                R.string.delete_identity_error_format_string,
                                LiquidityApplication.formatNullable(requireContext(), identity.name)
                        )
                    }
                }
                .create()
    }

}
