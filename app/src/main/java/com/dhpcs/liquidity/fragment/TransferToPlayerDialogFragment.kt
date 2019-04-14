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
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.lifecycle.ViewModelProviders
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat

class TransferToPlayerDialogFragment : AppCompatDialogFragment() {

    companion object {

        const val TAG = "transfer_to_player_dialog_fragment"

        private const val ARG_FROM_IDENTITY_ID = "from_identity_id"
        private const val ARG_TO_PLAYER_ID = "to_player_id"

        fun newInstance(
                from: BoardGame.Companion.Identity,
                to: BoardGame.Companion.Player?): TransferToPlayerDialogFragment {
            val transferToPlayerDialogFragment = TransferToPlayerDialogFragment()
            val args = Bundle()
            args.putString(ARG_FROM_IDENTITY_ID, from.memberId)
            args.putString(ARG_TO_PLAYER_ID, to?.memberId)
            transferToPlayerDialogFragment.arguments = args
            return transferToPlayerDialogFragment
        }

        private class PlayersAdapter
        internal constructor(context: Context,
                             val players: List<BoardGame.Companion.Player>
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
                textView.text = LiquidityApplication.formatNullable(context, player!!.name)
                return textView
            }

        }

        private class IdentitiesAdapter
        internal constructor(context: Context,
                             identities: List<BoardGame.Companion.Identity>
        ) : ArrayAdapter<BoardGame.Companion.Identity>(
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
                                 identity: BoardGame.Companion.Identity?
            ): View {
                textView.text = if (identity!!.isBanker) {
                    LiquidityApplication.formatNullable(context, identity.name)
                } else {
                    context.getString(
                            R.string.identity_format_string,
                            LiquidityApplication.formatNullable(context, identity.name),
                            LiquidityApplication.formatCurrencyValue(
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

    private var currency: String? = null

    private lateinit var from: BoardGame.Companion.Identity
    private lateinit var to: ArrayList<BoardGame.Companion.Player>

    private lateinit var identities: Map<String, BoardGame.Companion.Identity>
    private lateinit var identitiesSpinnerAdapter: IdentitiesAdapter

    private lateinit var playersSpinnerAdapter: PlayersAdapter

    private var buttonPositive: Button? = null

    private var value: BigDecimal? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)

        currency = model.boardGame.currency

        val fromIdentityId = arguments!!.getString(ARG_FROM_IDENTITY_ID)!!
        val toPlayerId = arguments!!.getString(ARG_TO_PLAYER_ID)

        from = model.boardGame.identities.getValue(fromIdentityId)

        to = if (toPlayerId != null) {
            arrayListOf(model.boardGame.players.getValue(toPlayerId))
        } else {
            arrayListOf()
        }

        identities = model.boardGame.identities
        identitiesSpinnerAdapter = IdentitiesAdapter(
                requireContext(),
                identities.values
                        .sortedWith(LiquidityApplication.identityComparator(requireContext()))
        )

        playersSpinnerAdapter = PlayersAdapter(
                requireContext(),
                model.boardGame.players.values
                        .sortedWith(LiquidityApplication.playerComparator(requireContext()))
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        @SuppressLint("InflateParams") val view = requireActivity().layoutInflater.inflate(
                R.layout.fragment_transfer_to_player_dialog, null
        )

        val textViewCurrency = view.findViewById<TextView>(R.id.textview_currency)
        val editTextValue = view.findViewById<EditText>(R.id.edittext_value)
        val editTextScaledValue = view.findViewById<TextView>(R.id.textview_scaled_value)
        val textViewValueError = view.findViewById<TextView>(R.id.textview_value_error)
        val spinnerFrom = view.findViewById<Spinner>(R.id.spinner_from)
        val textViewFromError = view.findViewById<TextView>(R.id.textview_from_error)
        val linearLayoutTo = view.findViewById<LinearLayout>(R.id.linearlayout_to)
        val spinnerTo = view.findViewById<Spinner>(R.id.spinner_to)

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)
        val alertDialog = AlertDialog.Builder(requireContext())
                .setTitle(getString(
                        R.string.enter_transfer_details
                ))
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    to.forEach { to ->
                        val error = getString(
                                R.string.transfer_to_player_error_format_string, to.name
                        )
                        model.execCommand(
                                model.boardGame.transferToPlayer(from, to, value!!)
                        ) { error }
                    }
                }
                .create()

        fun validateInput() {
            val currentBalance = identities.getValue(from.memberId).balance
            val isValueValid = if (value == null) {
                textViewValueError.text = null
                false
            } else {
                val requiredBalance = if (from.isBanker) {
                    null
                } else {
                    value!!.multiply(BigDecimal(to.size))
                }
                if (requiredBalance != null && currentBalance < requiredBalance) {
                    textViewValueError.text = getString(
                            R.string.transfer_value_invalid_format_string,
                            LiquidityApplication.formatCurrencyValue(
                                    requireContext(),
                                    currency!!,
                                    currentBalance
                            ),
                            LiquidityApplication.formatCurrencyValue(
                                    requireContext(),
                                    currency!!,
                                    requiredBalance
                            )
                    )
                    false
                } else {
                    textViewValueError.text = null
                    true
                }
            }
            val toAccountIds = to.map { it.accountId }.toSet()
            val isFromValid = if (toAccountIds.contains(from.accountId)) {
                textViewFromError.text = getString(
                        R.string.transfer_from_invalid_format_string,
                        LiquidityApplication.formatNullable(requireContext(), from.name)
                )
                false
            } else {
                textViewFromError.text = null
                true
            }
            buttonPositive!!.isEnabled = isValueValid && isFromValid
        }

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
                                LiquidityApplication.formatCurrencyValue(
                                        requireContext(),
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
                from = identitiesSpinnerAdapter.getItem(position)!!
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
                to = arrayListOf(playersSpinnerAdapter.getItem(position)!!)
                if (buttonPositive != null) validateInput()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}

        }

        textViewCurrency.text = LiquidityApplication.formatCurrency(requireContext(), currency!!)

        spinnerFrom.setSelection(identitiesSpinnerAdapter.getPosition(from))

        if (to.isNotEmpty()) {
            spinnerTo.setSelection(playersSpinnerAdapter.getPosition(to.first()))
        } else {
            spinnerTo.visibility = View.GONE
            for (player in playersSpinnerAdapter.players) {
                val checkedTextViewPlayer = requireActivity().layoutInflater.inflate(
                        android.R.layout.simple_list_item_multiple_choice,
                        linearLayoutTo,
                        false
                ) as CheckedTextView
                linearLayoutTo.addView(checkedTextViewPlayer)
                checkedTextViewPlayer.text =
                        LiquidityApplication.formatNullable(requireContext(), player.name)
                checkedTextViewPlayer.isChecked = to.contains(player)
                checkedTextViewPlayer.setOnClickListener {
                    checkedTextViewPlayer.toggle()
                    if (checkedTextViewPlayer.isChecked) {
                        to.add(player)
                    } else {
                        to.remove(player)
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

}
