package com.dhpcs.liquidity.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.lifecycle.ViewModelProviders
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class EnterIdentityNameDialogFragment : AppCompatDialogFragment() {

    companion object {

        const val TAG = "enter_identity_name_dialog_fragment"

        private const val ARG_IDENTITY_ID = "identity_id"
        private const val ARG_IDENTITY_NAME = "identity_name"

        fun newInstance(identity: BoardGame.Companion.Identity): EnterIdentityNameDialogFragment {
            val enterIdentityNameDialogFragment = EnterIdentityNameDialogFragment()
            val args = Bundle()
            args.putString(ARG_IDENTITY_ID, identity.memberId)
            args.putString(ARG_IDENTITY_NAME, identity.name)
            enterIdentityNameDialogFragment.arguments = args
            return enterIdentityNameDialogFragment
        }

    }

    private var buttonPositive: Button? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        @SuppressLint("InflateParams") val view = requireActivity().layoutInflater.inflate(
                R.layout.fragment_enter_identity_name_dialog, null
        )

        val textInputLayoutIdentityName = view
                .findViewById<TextInputLayout>(R.id.textinputlayout_identity_name)
        val textInputEditTextIdentityName = view
                .findViewById<TextInputEditText>(R.id.textinputedittext_identity_name)

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)
        val identityId = arguments!!.getString(ARG_IDENTITY_ID)!!
        val identityName = arguments!!.getString(ARG_IDENTITY_NAME)!!
        val alertDialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.enter_identity_name)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val error = getString(
                            R.string.change_identity_name_error_format_string,
                            textInputEditTextIdentityName.text.toString()
                    )
                    model.execCommand(
                            model.boardGame.changeIdentityName(
                                    identityId,
                                    textInputEditTextIdentityName.text.toString()
                            )
                    ) { error }
                }
                .create()

        fun validateInput(identityName: CharSequence) {
            buttonPositive?.isEnabled = model.boardGame.isIdentityNameValid(identityName)
        }

        textInputLayoutIdentityName.counterMaxLength = BoardGame.MAXIMUM_TAG_LENGTH
        textInputEditTextIdentityName.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) = validateInput(s)

        })

        textInputEditTextIdentityName.setText(
                LiquidityApplication.formatNullable(requireContext(), identityName)
        )

        alertDialog.setOnShowListener {
            buttonPositive = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
            validateInput(textInputEditTextIdentityName.text!!)
        }

        alertDialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        return alertDialog
    }

}
