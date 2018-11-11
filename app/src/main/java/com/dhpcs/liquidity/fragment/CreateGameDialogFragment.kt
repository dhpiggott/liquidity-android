package com.dhpcs.liquidity.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.*

class CreateGameDialogFragment : AppCompatDialogFragment() {

    companion object {

        private class CurrenciesAdapter internal constructor(context: Context,
                                                             currencies: List<Currency>
        ) : ArrayAdapter<Currency>(context,
                android.R.layout.simple_spinner_item,
                currencies) {

            init {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                sort { lhs, rhs -> lhs.currencyCode.compareTo(rhs.currencyCode) }
            }

            private fun bindView(textView: TextView, currency: Currency): View {
                val symbolAndOrName = if (currency.symbol == currency.currencyCode) {
                    currency.displayName
                } else {
                    context.getString(
                            R.string.game_currency_symbol_and_name_format_string,
                            currency.symbol,
                            currency.displayName
                    )
                }
                textView.text = context.getString(
                        R.string.game_currency_format_string,
                        currency.currencyCode,
                        symbolAndOrName
                )
                return textView
            }

            override fun getDropDownView(position: Int,
                                         convertView: View?,
                                         parent: ViewGroup
            ): View {
                return bindView(
                        super.getDropDownView(position, convertView, parent) as TextView,
                        getItem(position)!!
                )
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return bindView(
                        super.getView(position, convertView, parent) as TextView,
                        getItem(position)!!
                )
            }

        }

        const val TAG = "create_game_dialog_fragment"

        fun newInstance(): CreateGameDialogFragment = CreateGameDialogFragment()

    }

    private var buttonPositive: Button? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        @SuppressLint("InflateParams") val view = requireActivity().layoutInflater.inflate(
                R.layout.fragment_create_game_dialog, null
        )

        val currenciesSpinnerAdapter = CurrenciesAdapter(
                requireContext(),
                ArrayList(Currency.getAvailableCurrencies())
        )

        val textInputLayoutGameName =
                view.findViewById<TextInputLayout>(R.id.textinputlayout_game_name)
        val textInputEditTextGameName =
                view.findViewById<TextInputEditText>(R.id.textinputedittext_game_name)
        val spinnerCurrency = view.findViewById<Spinner>(R.id.spinner_game_currency)

        val alertDialog = AlertDialog.Builder(requireContext())
                .setTitle(getString(
                        R.string.enter_game_details
                ))
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val model = ViewModelProviders.of(requireActivity())
                            .get(MainActivity.Companion.BoardGameModel::class.java)
                    model.execCreateGameCommand(
                            model.boardGame.createGame(
                                    textInputEditTextGameName.text.toString(),
                                    currenciesSpinnerAdapter
                                            .getItem(spinnerCurrency.selectedItemPosition)!!,
                                    getString(R.string.bank_member_name)
                            )
                    ) {
                        getString(R.string.create_game_error)
                    }
                    findNavController().navigate(R.id.action_games_fragment_to_board_game_graph)
                }
                .create()

        textInputLayoutGameName.counterMaxLength = BoardGame.MAXIMUM_TAG_LENGTH
        textInputEditTextGameName.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) = validateInput(s)

        })
        spinnerCurrency.adapter = currenciesSpinnerAdapter

        spinnerCurrency.setSelection(
                currenciesSpinnerAdapter.getPosition(
                        Currency.getInstance(Locale.getDefault())
                )
        )

        alertDialog.setOnShowListener {
            buttonPositive = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
            validateInput(textInputEditTextGameName.text!!)
        }

        val window = alertDialog.window!!
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        return alertDialog
    }

    private fun validateInput(gameName: CharSequence) {
        buttonPositive?.isEnabled = BoardGame.isGameNameValid(gameName)
    }

}
