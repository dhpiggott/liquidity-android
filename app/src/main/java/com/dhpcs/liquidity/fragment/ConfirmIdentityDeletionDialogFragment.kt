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
        private const val ARG_IDENTITY_NAME = "identity_name"

        fun newInstance(identity: BoardGame.Companion.Identity
        ): ConfirmIdentityDeletionDialogFragment {
            val confirmIdentityDeletionDialogFragment = ConfirmIdentityDeletionDialogFragment()
            val args = Bundle()
            args.putString(ARG_IDENTITY_ID, identity.memberId)
            args.putString(ARG_IDENTITY_NAME, identity.name)
            confirmIdentityDeletionDialogFragment.arguments = args
            return confirmIdentityDeletionDialogFragment
        }

    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)
        val identityId = arguments!!.getString(ARG_IDENTITY_ID)!!
        val identityName = arguments!!.getString(ARG_IDENTITY_NAME)!!
        return AlertDialog.Builder(requireContext())
                .setTitle(
                        getString(
                                R.string.delete_identity_title_format_string,
                                LiquidityApplication.formatNullable(requireContext(), identityName)
                        )
                )
                .setMessage(R.string.delete_identity_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    val error = getString(
                            R.string.delete_identity_error_format_string,
                            LiquidityApplication.formatNullable(requireContext(), identityName)
                    )
                    model.execCommand(
                            model.boardGame.deleteIdentity(identityId)
                    ) { error }
                }
                .create()
    }

}
