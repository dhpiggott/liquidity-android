package com.dhpcs.liquidity.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.lifecycle.ViewModelProviders
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class CreateIdentityDialogFragment : AppCompatDialogFragment() {

    companion object {

        const val TAG = "create_identity_dialog_fragment"

        fun newInstance(): CreateIdentityDialogFragment = CreateIdentityDialogFragment()

    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        @SuppressLint("InflateParams") val view = requireActivity().layoutInflater.inflate(
                R.layout.fragment_create_identity_dialog, null
        )

        val textInputLayoutIdentityName = view
                .findViewById<TextInputLayout>(R.id.textinputlayout_identity_name)
        val textInputEditTextIdentityName = view
                .findViewById<TextInputEditText>(R.id.textinputedittext_identity_name)

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)
        val alertDialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.enter_identity_name)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val error = getString(
                            R.string.create_identity_error_format_string,
                            textInputEditTextIdentityName.text.toString()
                    )
                    model.execCommand(
                            model.boardGame
                                    .createIdentity(textInputEditTextIdentityName.text.toString())
                                    .map { Unit }
                    ) { error }
                }
                .create()

        textInputLayoutIdentityName.counterMaxLength = BoardGame.MAXIMUM_TAG_LENGTH

        alertDialog.setOnShowListener {
            val buttonPositive = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)

            fun validateInput(identityName: CharSequence) {
                buttonPositive.isEnabled = model.boardGame.isIdentityNameValid(identityName)
            }

            textInputEditTextIdentityName.addTextChangedListener(object : TextWatcher {

                override fun beforeTextChanged(
                        s: CharSequence, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable) = validateInput(s)

            })

            validateInput(textInputEditTextIdentityName.text!!)
        }

        alertDialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        return alertDialog
    }

}
