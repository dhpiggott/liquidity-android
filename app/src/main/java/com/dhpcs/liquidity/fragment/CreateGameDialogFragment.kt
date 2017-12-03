package com.dhpcs.liquidity.fragment

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.support.design.widget.TextInputEditText
import android.support.design.widget.TextInputLayout
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatDialogFragment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.ws.protocol.`ZoneCommand$`
import java.util.*

class CreateGameDialogFragment : AppCompatDialogFragment() {

    companion object {

        interface Listener {

            fun onGameDetailsEntered(name: String, currency: Currency)

        }

        private class CurrenciesAdapter internal constructor(context: Context,
                                                             currencies: List<Currency>
        ) : ArrayAdapter<Currency>(context,
                android.R.layout.simple_spinner_item,
                currencies) {

            init {
                setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item
                )
                sort { lhs, rhs -> lhs.currencyCode.compareTo(rhs.currencyCode) }
            }

            @TargetApi(Build.VERSION_CODES.KITKAT)
            private fun bindView(textView: TextView, currency: Currency?): View {
                val displayName = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                    null
                } else {
                    currency!!.displayName
                }
                val symbolAndOrName = if (currency!!.symbol == currency.currencyCode) {
                    displayName
                } else {
                    if (displayName == null) {
                        currency.symbol
                    } else {
                        context.getString(
                                R.string.game_currency_symbol_and_name_format_string,
                                currency.symbol,
                                displayName
                        )
                    }
                }
                textView.text = if (symbolAndOrName == null) {
                    currency.currencyCode
                } else {
                    context.getString(
                            R.string.game_currency_format_string,
                            currency.currencyCode,
                            symbolAndOrName
                    )
                }
                return textView
            }

            override fun getDropDownView(position: Int,
                                         convertView: View?,
                                         parent: ViewGroup
            ): View {
                return bindView(
                        super.getDropDownView(position, convertView, parent) as TextView,
                        getItem(position)
                )
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return bindView(
                        super.getView(position, convertView, parent) as TextView,
                        getItem(position)
                )
            }

        }

        const val TAG = "create_game_dialog_fragment"

        fun newInstance(): CreateGameDialogFragment = CreateGameDialogFragment()

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
                R.layout.fragment_create_game_dialog, null
        )

        val currencies = HashSet<Currency>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            for (locale in Locale.getAvailableLocales()) {
                try {
                    currencies.add(Currency.getInstance(locale))
                } catch (ignored: IllegalArgumentException) {
                }
            }
        } else {
            currencies.addAll(Currency.getAvailableCurrencies())
        }
        val currenciesSpinnerAdapter = CurrenciesAdapter(activity!!, ArrayList(currencies))

        val textInputLayoutGameName =
                view.findViewById<TextInputLayout>(R.id.textinputlayout_game_name)
        val textInputEditTextGameName =
                view.findViewById<TextInputEditText>(R.id.textinputedittext_game_name)
        val spinnerCurrency = view.findViewById<Spinner>(R.id.spinner_game_currency)

        val alertDialog = AlertDialog.Builder(activity!!)
                .setTitle(getString(
                        R.string.enter_game_details
                ))
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    listener?.onGameDetailsEntered(
                            textInputEditTextGameName.text.toString(),
                            currenciesSpinnerAdapter.getItem(spinnerCurrency.selectedItemPosition)
                    )
                }
                .create()

        textInputLayoutGameName.counterMaxLength = `ZoneCommand$`.`MODULE$`.MaximumTagLength()
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
