package com.dhpcs.liquidity.fragment

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.BoardGameActivity

class ConfirmIdentityDeletionDialogFragment : AppCompatDialogFragment() {

    companion object {

        interface Listener {

            fun onIdentityDeleteConfirmed(identity: BoardGame.Companion.Identity)

        }

        const val TAG = "confirm_identity_deletion_dialog_fragment"

        private const val ARG_IDENTITY = "identity"

        fun newInstance(identity: BoardGame.Companion.Identity
        ): ConfirmIdentityDeletionDialogFragment {
            val confirmIdentityDeletionDialogFragment = ConfirmIdentityDeletionDialogFragment()
            val args = Bundle()
            args.putSerializable(ARG_IDENTITY, identity)
            confirmIdentityDeletionDialogFragment.arguments = args
            return confirmIdentityDeletionDialogFragment
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
        val identity = arguments!!.getSerializable(ARG_IDENTITY) as BoardGame.Companion.Identity
        return AlertDialog.Builder(requireContext())
                .setTitle(
                        getString(
                                R.string.delete_identity_title_format_string,
                                BoardGameActivity.formatNullable(requireContext(), identity.name)
                        )
                )
                .setMessage(R.string.delete_identity_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    listener?.onIdentityDeleteConfirmed(
                            arguments!!.getSerializable(ARG_IDENTITY) as
                                    BoardGame.Companion.Identity
                    )
                }
                .create()
    }

}
