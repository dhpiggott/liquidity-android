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

        private const val ARG_IDENTITY = "identity"

        fun newInstance(identity: BoardGame.Companion.Identity
        ): ConfirmIdentityDeletionDialogFragment {
            val confirmIdentityDeletionDialogFragment = ConfirmIdentityDeletionDialogFragment()
            val args = Bundle()
            args.putParcelable(ARG_IDENTITY, identity)
            confirmIdentityDeletionDialogFragment.arguments = args
            return confirmIdentityDeletionDialogFragment
        }

    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val identity = arguments!!.getParcelable<BoardGame.Companion.Identity>(ARG_IDENTITY)!!

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)
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
