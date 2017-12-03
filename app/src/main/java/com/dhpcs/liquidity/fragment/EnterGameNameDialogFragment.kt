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
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.ws.protocol.`ZoneCommand$`

class EnterGameNameDialogFragment : AppCompatDialogFragment() {

    companion object {

        interface Listener {

            fun onGameNameEntered(name: String)

        }

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
                R.layout.fragment_enter_game_name_dialog, null
        )

        val name = arguments!!.getString(ARG_NAME)

        val textInputLayoutGameName =
                view.findViewById<TextInputLayout>(R.id.textinputlayout_game_name)
        val textInputEditTextGameName =
                view.findViewById<TextInputEditText>(R.id.textinputedittext_game_name)

        val alertDialog = AlertDialog.Builder(activity!!)
                .setTitle(R.string.enter_game_name)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    listener?.onGameNameEntered(textInputEditTextGameName.text.toString())
                }
                .create()

        textInputLayoutGameName.counterMaxLength = `ZoneCommand$`.`MODULE$`.MaximumTagLength()
        textInputEditTextGameName.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) = validateInput(s)

        })

        textInputEditTextGameName.setText(name)

        alertDialog.setOnShowListener {
            buttonPositive = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
            validateInput(textInputEditTextGameName.text)
        }

        val window = alertDialog.window!!
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        return alertDialog
    }

    private fun validateInput(gameName: CharSequence) {
        buttonPositive?.isEnabled = BoardGame.Companion.isGameNameValid(gameName)
    }

}
