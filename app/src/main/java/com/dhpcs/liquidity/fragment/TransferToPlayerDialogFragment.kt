package com.dhpcs.liquidity.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatDialogFragment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.BoardGameActivity
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*

class TransferToPlayerDialogFragment : AppCompatDialogFragment() {

    companion object {

        interface Listener {

            fun onTransferValueEntered(from: BoardGame.Companion.Identity,
                                       to: Collection<BoardGame.Companion.Player>,
                                       transferValue: BigDecimal
            )

        }

        const val TAG = "transfer_to_player_dialog_fragment"

        private const val ARG_IDENTITIES = "identities"
        private const val ARG_PLAYERS = "players"
        private const val ARG_CURRENCY = "currency"
        private const val ARG_FROM = "from"
        private const val ARG_TO = "to"

        private const val EXTRA_TO_LIST = "to_list"

        fun newInstance(
                identities: ArrayList<BoardGame.Companion.IdentityWithBalance>,
                players: ArrayList<out BoardGame.Companion.Player>,
                currency: String?,
                from: BoardGame.Companion.IdentityWithBalance,
                to: BoardGame.Companion.Player?): TransferToPlayerDialogFragment {
            val transferToPlayerDialogFragment = TransferToPlayerDialogFragment()
            val args = Bundle()
            args.putSerializable(ARG_IDENTITIES, identities)
            args.putSerializable(ARG_PLAYERS, players)
            args.putString(ARG_CURRENCY, currency)
            args.putSerializable(ARG_FROM, from)
            args.putSerializable(ARG_TO, to)
            transferToPlayerDialogFragment.arguments = args
            return transferToPlayerDialogFragment
        }

        private class PlayersAdapter internal constructor(context: Context,
                                                          players: List<BoardGame.Companion.Player>
        ) : ArrayAdapter<BoardGame.Companion.Player>(
                context,
                android.R.layout.simple_spinner_item,
                players
        ) {

            init {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return bindView(
                        super.getView(position, convertView, parent) as TextView,
                        getItem(position)
                )
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

            private fun bindView(textView: TextView, player: BoardGame.Companion.Player?): View {
                textView.text = BoardGameActivity.formatNullable(context, player!!.name)
                return textView
            }

        }

        private class IdentitiesAdapter internal constructor(context: Context,
                                                             identities:
                                                             List<BoardGame.Companion
                                                             .IdentityWithBalance>
        ) : ArrayAdapter<BoardGame.Companion.IdentityWithBalance>(
                context,
                android.R.layout.simple_spinner_item,
                identities
        ) {

            init {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return bindView(
                        super.getView(position, convertView, parent) as TextView,
                        getItem(position)
                )
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

            private fun bindView(textView: TextView,
                                 identity: BoardGame.Companion.IdentityWithBalance?
            ): View {
                textView.text = if (identity!!.isBanker) {
                    BoardGameActivity.formatNullable(context, identity.name)
                } else {
                    context.getString(
                            R.string.identity_format_string,
                            BoardGameActivity.formatNullable(context, identity.name),
                            BoardGameActivity.formatCurrencyValue(
                                    context,
                                    identity.currency,
                                    identity.balance
                            )
                    )
                }
                return textView
            }

        }

    }

    private var listener: Listener? = null

    private var currency: String? = null
    private var from: BoardGame.Companion.IdentityWithBalance? = null
    private var to: BoardGame.Companion.Player? = null
    private var toList: ArrayList<BoardGame.Companion.Player>? = null

    private var identitiesSpinnerAdapter: ArrayAdapter<BoardGame.Companion.IdentityWithBalance>? =
            null
    private var playersSpinnerAdapter: ArrayAdapter<BoardGame.Companion.Player>? = null

    private var textViewValueError: TextView? = null
    private var textViewFromError: TextView? = null
    private var buttonPositive: Button? = null

    private var value: BigDecimal? = null

    private var identities: Map<String, BoardGame.Companion.IdentityWithBalance>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val identities = arguments!!.getSerializable(ARG_IDENTITIES) as
                List<BoardGame.Companion.IdentityWithBalance>
        val players = arguments!!.getSerializable(ARG_PLAYERS) as
                List<BoardGame.Companion.Player>
        val currency = arguments!!.getString(ARG_CURRENCY)
        this.currency = currency
        from = arguments!!.getSerializable(ARG_FROM) as BoardGame.Companion.IdentityWithBalance
        to = arguments!!.getSerializable(ARG_TO) as BoardGame.Companion.Player?
        val toList = when {
            to != null ->
                null
            savedInstanceState != null ->
                savedInstanceState.getSerializable(EXTRA_TO_LIST) as
                        ArrayList<BoardGame.Companion.Player>
            else ->
                ArrayList()
        }
        this.toList = toList

        val playerComparator = BoardGameActivity.playerComparator(requireActivity())
        identitiesSpinnerAdapter = IdentitiesAdapter(requireActivity(), identities)
        identitiesSpinnerAdapter!!.sort(playerComparator)
        playersSpinnerAdapter = PlayersAdapter(requireActivity(), players)
        playersSpinnerAdapter!!.sort(playerComparator)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        @SuppressLint("InflateParams") val view = requireActivity().layoutInflater.inflate(
                R.layout.fragment_transfer_to_player_dialog, null
        )

        val textViewCurrency = view.findViewById<TextView>(R.id.textview_currency)
        val editTextValue = view.findViewById<EditText>(R.id.edittext_value)
        val editTextScaledValue = view.findViewById<TextView>(R.id.textview_scaled_value)
        textViewValueError = view.findViewById(R.id.textview_value_error)
        val spinnerFrom = view.findViewById<Spinner>(R.id.spinner_from)
        textViewFromError = view.findViewById(R.id.textview_from_error)
        val linearLayoutTo = view.findViewById<LinearLayout>(R.id.linearlayout_to)
        val spinnerTo = view.findViewById<Spinner>(R.id.spinner_to)

        val alertDialog = AlertDialog.Builder(requireActivity())
                .setTitle(getString(
                        R.string.enter_transfer_details
                ))
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    listener?.onTransferValueEntered(
                            from!!,
                            if (to != null) listOf(to!!) else toList!!,
                            value!!
                    )
                }
                .create()

        editTextValue.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            @SuppressLint("SetTextI18n")
            override fun afterTextChanged(s: Editable) {

                val numberFormat = NumberFormat.getNumberInstance() as DecimalFormat

                value = try {
                    BigDecimal(
                            s.toString().replace(
                                    numberFormat.decimalFormatSymbols.groupingSeparator.toString(),
                                    ""
                            )
                    )
                } catch (_: IllegalArgumentException) {
                    null
                }

                if (value == null) {

                    editTextValue.removeTextChangedListener(this)
                    editTextValue.text = null
                    editTextValue.addTextChangedListener(this)
                    editTextScaledValue.text = null

                } else {

                    val trailingZeros = StringBuilder()
                    val currentDecimalSeparatorIndex = s.toString().indexOf(
                            numberFormat.decimalFormatSymbols.decimalSeparator
                    )
                    if (currentDecimalSeparatorIndex != -1) {
                        for (i in currentDecimalSeparatorIndex + 1 until s.length) {
                            if (s[i] == numberFormat.decimalFormatSymbols.zeroDigit) {
                                trailingZeros.append(
                                        numberFormat.decimalFormatSymbols.zeroDigit
                                )
                            } else {
                                trailingZeros.setLength(0)
                            }
                        }
                        numberFormat.isDecimalSeparatorAlwaysShown = true
                    }

                    val currentSelection = editTextValue.selectionStart
                    val currentLength = editTextValue.text.length

                    numberFormat.maximumFractionDigits = value!!.scale()
                    numberFormat.minimumFractionDigits = 0

                    editTextValue.removeTextChangedListener(this)
                    editTextValue.setText(numberFormat.format(value) + trailingZeros)
                    editTextValue.addTextChangedListener(this)

                    val updatedLength = editTextValue.text.length
                    val updatedSelection = currentSelection + updatedLength - currentLength

                    if (updatedSelection < 0 || updatedSelection > updatedLength) {
                        editTextValue.setSelection(0)
                    } else {
                        editTextValue.setSelection(updatedSelection)
                    }

                    if (value!!.scaleByPowerOfTen(-3).abs() < BigDecimal.ONE) {
                        editTextScaledValue.text = null
                    } else {
                        editTextScaledValue.text = getString(
                                R.string.transfer_to_player_scaled_value_format_string,
                                BoardGameActivity.formatCurrencyValue(
                                        requireActivity(),
                                        currency!!,
                                        value!!
                                )
                        )
                    }

                }

                if (buttonPositive != null) validateInput()

            }

        })
        spinnerFrom.adapter = identitiesSpinnerAdapter
        spinnerFrom.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(parent: AdapterView<*>,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                from = identitiesSpinnerAdapter!!.getItem(position)
                if (buttonPositive != null) validateInput()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}

        }
        spinnerTo.adapter = playersSpinnerAdapter
        spinnerTo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(parent: AdapterView<*>,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                to = playersSpinnerAdapter!!.getItem(position)
                if (buttonPositive != null) validateInput()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}

        }

        textViewCurrency.text = BoardGameActivity.formatCurrency(requireActivity(), currency!!)

        spinnerFrom.setSelection(identitiesSpinnerAdapter!!.getPosition(from))

        if (to != null) {
            spinnerTo.setSelection(playersSpinnerAdapter!!.getPosition(to))
        } else {
            spinnerTo.visibility = View.GONE
            val players = arguments!!.getSerializable(ARG_PLAYERS) as
                    List<BoardGame.Companion.Player>
            for (player in players) {
                val checkedTextViewPlayer = requireActivity().layoutInflater.inflate(
                        android.R.layout.simple_list_item_multiple_choice,
                        linearLayoutTo,
                        false
                ) as CheckedTextView
                linearLayoutTo.addView(checkedTextViewPlayer)
                checkedTextViewPlayer.text =
                        BoardGameActivity.formatNullable(requireActivity(), player.name)
                checkedTextViewPlayer.isChecked = toList!!.contains(player)
                checkedTextViewPlayer.setOnClickListener {
                    checkedTextViewPlayer.toggle()
                    if (checkedTextViewPlayer.isChecked) {
                        toList!!.add(player)
                    } else {
                        toList!!.remove(player)
                    }
                    validateInput()
                }
            }
        }

        alertDialog.setOnShowListener {
            buttonPositive = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
            value = try {
                BigDecimal(editTextValue.text.toString())
            } catch (_: IllegalArgumentException) {
                null
            }

            validateInput()
        }

        val window = alertDialog.window!!
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        return alertDialog
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        listener = context as Listener?
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(EXTRA_TO_LIST, toList)
    }

    fun onIdentitiesUpdated(identities: Map<String, BoardGame.Companion.IdentityWithBalance>) {
        this.identities = identities
        identitiesSpinnerAdapter!!.clear()
        identitiesSpinnerAdapter!!.addAll(identities.values)
        if (buttonPositive != null) validateInput()
    }

    private fun validateInput() {
        val currentBalance = if (identities == null) {
            from!!.balance
        } else {
            identities!![from!!.memberId]!!.balance
        }
        val isValueValid = if (value == null) {
            textViewValueError!!.text = null
            false
        } else {
            val requiredBalance = if (from!!.isBanker) {
                null
            } else {
                value!!.multiply(BigDecimal(if (to != null) 1 else toList!!.size))
            }
            if (requiredBalance != null && currentBalance < requiredBalance) {
                textViewValueError!!.text = getString(
                        R.string.transfer_value_invalid_format_string,
                        BoardGameActivity.formatCurrencyValue(
                                requireActivity(),
                                currency!!,
                                currentBalance
                        ),
                        BoardGameActivity.formatCurrencyValue(
                                requireActivity(),
                                currency!!,
                                requiredBalance
                        )
                )
                false
            } else {
                textViewValueError!!.text = null
                true
            }
        }
        val toAccountIds = if (to != null) {
            setOf(to!!.accountId)
        } else {
            toList!!.map { it.accountId }.toSet()
        }
        val isFromValid = if (toAccountIds.contains(from!!.accountId)) {
            textViewFromError!!.text = getString(
                    R.string.transfer_from_invalid_format_string,
                    BoardGameActivity.formatNullable(requireActivity(), from!!.name)
            )
            false
        } else {
            textViewFromError!!.text = null
            true
        }
        buttonPositive!!.isEnabled = isValueValid && isFromValid
    }

}
