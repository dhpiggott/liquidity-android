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
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class EnterGameNameDialogFragment : AppCompatDialogFragment() {

    companion object {

        const val TAG = "enter_game_name_dialog_fragment"

        private const val ARG_NAME = "name"

        fun newInstance(name: String): EnterGameNameDialogFragment {
            val enterGameNameDialogFragment = EnterGameNameDialogFragment()
            val args = Bundle()
            args.putString(ARG_NAME, name)
            enterGameNameDialogFragment.arguments = args
            return enterGameNameDialogFragment
        }

    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        @SuppressLint("InflateParams") val view = requireActivity().layoutInflater.inflate(
                R.layout.fragment_enter_game_name_dialog, null
        )

        val textInputLayoutGameName =
                view.findViewById<TextInputLayout>(R.id.textinputlayout_game_name)
        val textInputEditTextGameName =
                view.findViewById<TextInputEditText>(R.id.textinputedittext_game_name)

        lateinit var buttonPositive: Button

        val name = arguments!!.getString(ARG_NAME)

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)
        val alertDialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.enter_game_name)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val error = getString(R.string.change_game_name_error_format_string, name)
                    model.execCommand(
                            model.boardGame
                                    .changeGameName(textInputEditTextGameName.text.toString())
                    ) { error }
                }
                .create()

        fun validateInput(gameName: CharSequence) {
            buttonPositive.isEnabled = BoardGame.isGameNameValid(gameName)
        }

        textInputLayoutGameName.counterMaxLength = BoardGame.MAXIMUM_TAG_LENGTH
        textInputEditTextGameName.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) = validateInput(s)

        })

        textInputEditTextGameName.setText(name)

        alertDialog.setOnShowListener {
            buttonPositive = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
            validateInput(textInputEditTextGameName.text!!)
        }

        val window = alertDialog.window!!
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        return alertDialog
    }

}
