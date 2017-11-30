package com.dhpcs.liquidity.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.design.widget.TextInputEditText
import android.support.design.widget.TextInputLayout
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatDialogFragment
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowManager
import android.widget.Button
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.BoardGameActivity
import com.dhpcs.liquidity.boardgame.BoardGame.Identity
import com.dhpcs.liquidity.ws.protocol.`ZoneCommand$`

class EnterIdentityNameDialogFragment : AppCompatDialogFragment() {

    companion object {

        interface Listener {

            fun onIdentityNameEntered(identity: Identity, name: String)

        }

        const val TAG = "enter_identity_name_dialog_fragment"

        private const val ARG_IDENTITY = "identity"

        fun newInstance(identity: Identity): EnterIdentityNameDialogFragment {
            val enterIdentityNameDialogFragment = EnterIdentityNameDialogFragment()
            val args = Bundle()
            args.putSerializable(ARG_IDENTITY, identity)
            enterIdentityNameDialogFragment.arguments = args
            return enterIdentityNameDialogFragment
        }

    }

    private var listener: Listener? = null

    private var buttonPositive: Button? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        listener = context as Listener?
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        @SuppressLint("InflateParams") val view = activity!!.layoutInflater.inflate(
                R.layout.fragment_enter_identity_name_dialog, null
        )

        val identity = arguments!!.getSerializable(ARG_IDENTITY) as Identity

        val textInputLayoutIdentityName = view.findViewById<TextInputLayout>(R.id.textinputlayout_identity_name)
        val textInputEditTextIdentityName = view.findViewById<TextInputEditText>(R.id.textinputedittext_identity_name)

        val alertDialog = AlertDialog.Builder(activity!!)
                .setTitle(R.string.enter_identity_name)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    listener?.onIdentityNameEntered(
                            identity,
                            textInputEditTextIdentityName.text.toString()
                    )
                }
                .create()

        textInputLayoutIdentityName.counterMaxLength = `ZoneCommand$`.`MODULE$`.MaximumTagLength()
        textInputEditTextIdentityName.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) = validateInput(s)

        })

        textInputEditTextIdentityName.setText(
                BoardGameActivity.formatNullable(activity, identity.member().name())
        )

        alertDialog.setOnShowListener {
            buttonPositive = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
            validateInput(textInputEditTextIdentityName.text)
        }

        val window = alertDialog.window!!
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        return alertDialog
    }

    private fun validateInput(identityName: CharSequence) {
        buttonPositive?.isEnabled = (activity as BoardGameActivity).isIdentityNameValid(
                identityName
        )
    }

}