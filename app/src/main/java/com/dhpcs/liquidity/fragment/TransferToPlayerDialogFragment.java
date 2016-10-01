package com.dhpcs.liquidity.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.dhpcs.liquidity.boardgame.BoardGame.Identity;
import com.dhpcs.liquidity.boardgame.BoardGame.IdentityWithBalance;
import com.dhpcs.liquidity.boardgame.BoardGame.Player;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activity.BoardGameActivity;
import com.dhpcs.liquidity.model.AccountId;
import com.dhpcs.liquidity.model.MemberId;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import scala.Option;
import scala.collection.JavaConversions;
import scala.util.Either;

public class TransferToPlayerDialogFragment extends DialogFragment {

    public interface Listener {

        void onTransferValueEntered(Identity from, List<Player> to, BigDecimal transferValue);

    }

    public static final String TAG = "transfer_to_player_dialog_fragment";

    private static final String ARG_IDENTITIES = "identities";
    private static final String ARG_PLAYERS = "players";
    private static final String ARG_CURRENCY = "currency";
    private static final String ARG_FROM = "from";
    private static final String ARG_TO = "to";

    private static final String EXTRA_TO_LIST = "to_list";

    private static class PlayersAdapter extends ArrayAdapter<Player> {

        public PlayersAdapter(Context context, List<Player> players) {
            super(context, android.R.layout.simple_spinner_item, players);
            setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
            );
        }

        private View bindView(TextView textView, Player player) {
            textView.setText(
                    BoardGameActivity.formatNullable(getContext(), player.member().name())
            );
            return textView;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return bindView(
                    (TextView) super.getDropDownView(position, convertView, parent),
                    getItem(position)
            );
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return bindView(
                    (TextView) super.getView(position, convertView, parent),
                    getItem(position)
            );
        }

    }

    private static class IdentitiesAdapter extends ArrayAdapter<IdentityWithBalance> {

        public IdentitiesAdapter(Context context, List<IdentityWithBalance> identities) {
            super(context, android.R.layout.simple_spinner_item, identities);
            setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
            );
        }

        private View bindView(TextView textView, IdentityWithBalance identity) {
            textView.setText(
                    identity.isBanker() ?
                            BoardGameActivity.formatNullable(
                                    getContext(),
                                    identity.member().name()
                            )
                            :
                            getContext().getString(
                                    R.string.identity_format_string,
                                    BoardGameActivity.formatNullable(
                                            getContext(),
                                            identity.member().name()
                                    ),
                                    BoardGameActivity.formatCurrencyValue(
                                            getContext(),
                                            identity.balanceWithCurrency()._2(),
                                            identity.balanceWithCurrency()._1()
                                    )
                            )
            );
            return textView;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return bindView(
                    (TextView) super.getDropDownView(position, convertView, parent),
                    getItem(position)
            );
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return bindView(
                    (TextView) super.getView(position, convertView, parent),
                    getItem(position)
            );
        }

    }

    public static TransferToPlayerDialogFragment newInstance(
            scala.collection.Iterable<IdentityWithBalance> identities,
            scala.collection.Iterable<? extends Player> players,
            Option<Either<String, Currency>> currency,
            IdentityWithBalance from,
            Player to) {
        TransferToPlayerDialogFragment transferToPlayerDialogFragment =
                new TransferToPlayerDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(
                ARG_IDENTITIES,
                new ArrayList<>(
                        JavaConversions.bufferAsJavaList(
                                identities.<Identity>toBuffer()
                        )
                )
        );
        args.putSerializable(
                ARG_PLAYERS,
                new ArrayList<>(
                        JavaConversions.bufferAsJavaList(
                                players.<Player>toBuffer()
                        )
                )
        );
        args.putSerializable(ARG_CURRENCY, currency);
        args.putSerializable(ARG_FROM, from);
        args.putSerializable(ARG_TO, to);
        transferToPlayerDialogFragment.setArguments(args);
        return transferToPlayerDialogFragment;
    }

    private Listener listener;

    private Option<Either<String, Currency>> currency;
    private IdentityWithBalance from;
    private Player to;
    private ArrayList<Player> toList;

    private ArrayAdapter<IdentityWithBalance> identitiesSpinnerAdapter;
    private ArrayAdapter<Player> playersSpinnerAdapter;

    private TextView textViewValueError;
    private TextView textViewFromError;
    private Button buttonPositive;

    private BigDecimal value;

    private scala.collection.immutable.Map<MemberId, IdentityWithBalance> identities;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        listener = (Listener) activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        @SuppressWarnings("unchecked") List<IdentityWithBalance> identities =
                (List<IdentityWithBalance>) getArguments().getSerializable(ARG_IDENTITIES);
        @SuppressWarnings("unchecked") List<Player> players =
                (List<Player>) getArguments().getSerializable(ARG_PLAYERS);
        @SuppressWarnings("unchecked") Option<Either<String, Currency>> currency =
                (Option<Either<String, Currency>>) getArguments().getSerializable(ARG_CURRENCY);
        this.currency = currency;
        from = (IdentityWithBalance) getArguments().getSerializable(ARG_FROM);
        to = (Player) getArguments().getSerializable(ARG_TO);
        @SuppressWarnings("unchecked") ArrayList<Player> toList =
                to != null ? null : (savedInstanceState != null
                        ?
                        (ArrayList<Player>) savedInstanceState.getSerializable(EXTRA_TO_LIST)
                        :
                        new ArrayList<Player>());
        this.toList = toList;

        Comparator<Player> playerComparator = BoardGameActivity.playerComparator(getActivity());
        identitiesSpinnerAdapter = new IdentitiesAdapter(getActivity(), identities);
        identitiesSpinnerAdapter.sort(playerComparator);
        playersSpinnerAdapter = new PlayersAdapter(getActivity(), players);
        playersSpinnerAdapter.sort(playerComparator);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        @SuppressLint("InflateParams") View view = getActivity().getLayoutInflater().inflate(
                R.layout.fragment_transfer_to_player_dialog,
                null
        );

        TextView textViewCurrency = (TextView) view.findViewById(R.id.textview_currency);
        final EditText editTextValue = (EditText) view.findViewById(R.id.edittext_value);
        final TextView editTextScaledValue = (TextView)
                view.findViewById(R.id.textview_scaled_value);
        textViewValueError = (TextView) view.findViewById(R.id.textview_value_error);
        Spinner spinnerFrom = (Spinner) view.findViewById(R.id.spinner_from);
        textViewFromError = (TextView) view.findViewById(R.id.textview_from_error);
        LinearLayout linearLayoutTo = (LinearLayout) view.findViewById(R.id.linearlayout_to);
        Spinner spinnerTo = (Spinner) view.findViewById(R.id.spinner_to);

        final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                .setTitle(
                        getString(
                                R.string.enter_transfer_details
                        )
                )
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.ok,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                if (listener != null) {
                                    listener.onTransferValueEntered(
                                            from,
                                            to != null ? Collections.singletonList(to) : toList,
                                            value
                                    );
                                }
                            }

                        }
                )
                .create();

        editTextValue.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void afterTextChanged(Editable s) {

                DecimalFormat numberFormat = (DecimalFormat) NumberFormat.getNumberInstance();

                try {
                    value = new BigDecimal(
                            s.toString().replace(
                                    String.valueOf(
                                            numberFormat.getDecimalFormatSymbols()
                                                    .getGroupingSeparator()
                                    ),
                                    ""
                            )
                    );
                } catch (IllegalArgumentException e) {
                    value = null;
                }

                if (value == null) {

                    editTextValue.removeTextChangedListener(this);
                    editTextValue.setText(null);
                    editTextValue.addTextChangedListener(this);
                    editTextScaledValue.setText(null);

                } else {

                    StringBuilder trailingZeros = new StringBuilder();
                    int currentDecimalSeparatorIndex = s.toString().indexOf(
                            numberFormat.getDecimalFormatSymbols().getDecimalSeparator()
                    );
                    if (currentDecimalSeparatorIndex != -1) {
                        for (int i = currentDecimalSeparatorIndex + 1; i < s.length(); i++) {
                            if (s.charAt(i)
                                    == numberFormat.getDecimalFormatSymbols().getZeroDigit()) {
                                trailingZeros.append(
                                        numberFormat.getDecimalFormatSymbols().getZeroDigit()
                                );
                            } else {
                                trailingZeros.setLength(0);
                            }
                        }
                        numberFormat.setDecimalSeparatorAlwaysShown(true);
                    }

                    int currentSelection = editTextValue.getSelectionStart();
                    int currentLength = editTextValue.getText().length();

                    numberFormat.setMaximumFractionDigits(value.scale());
                    numberFormat.setMinimumFractionDigits(0);

                    editTextValue.removeTextChangedListener(this);
                    editTextValue.setText(numberFormat.format(value) + trailingZeros);
                    editTextValue.addTextChangedListener(this);

                    int updatedLength = editTextValue.getText().length();
                    int updatedSelection = currentSelection + updatedLength - currentLength;

                    if (updatedSelection < 0 || updatedSelection > updatedLength) {
                        editTextValue.setSelection(0);
                    } else {
                        editTextValue.setSelection(updatedSelection);
                    }

                    if (value.scaleByPowerOfTen(-3).abs().compareTo(BigDecimal.ONE) < 0) {
                        editTextScaledValue.setText(null);
                    } else {
                        editTextScaledValue.setText(
                                getString(
                                        R.string.transfer_to_player_scaled_value_format_string,
                                        BoardGameActivity.formatCurrencyValue(
                                                getActivity(),
                                                currency,
                                                value
                                        )
                                )
                        );
                    }

                }

                if (buttonPositive != null) {
                    validateInput();
                }

            }

        });
        spinnerFrom.setAdapter(identitiesSpinnerAdapter);
        spinnerFrom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent,
                                       View view,
                                       int position,
                                       long id) {
                from = identitiesSpinnerAdapter.getItem(position);
                if (buttonPositive != null) {
                    validateInput();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }

        });
        spinnerTo.setAdapter(playersSpinnerAdapter);
        spinnerTo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent,
                                       View view,
                                       int position,
                                       long id) {
                to = playersSpinnerAdapter.getItem(position);
                if (buttonPositive != null) {
                    validateInput();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }

        });

        textViewCurrency.setText(
                BoardGameActivity.formatCurrency(
                        getActivity(),
                        currency
                )
        );

        spinnerFrom.setSelection(identitiesSpinnerAdapter.getPosition(from));

        if (to != null) {
            spinnerTo.setSelection(playersSpinnerAdapter.getPosition(to));
        } else {
            spinnerTo.setVisibility(View.GONE);
            @SuppressWarnings("unchecked") List<Player> players =
                    (List<Player>) getArguments().getSerializable(ARG_PLAYERS);
            assert players != null;
            for (final Player player : players) {
                final CheckedTextView checkedTextViewPlayer = (CheckedTextView)
                        getActivity().getLayoutInflater().inflate(
                                android.R.layout.simple_list_item_multiple_choice,
                                linearLayoutTo,
                                false
                        );
                linearLayoutTo.addView(checkedTextViewPlayer);
                checkedTextViewPlayer.setText(
                        BoardGameActivity.formatNullable(getActivity(), player.member().name())
                );
                checkedTextViewPlayer.setChecked(toList.contains(player));
                checkedTextViewPlayer.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        checkedTextViewPlayer.toggle();
                        if (checkedTextViewPlayer.isChecked()) {
                            toList.add(player);
                        } else {
                            toList.remove(player);
                        }
                        validateInput();
                    }

                });
            }
        }

        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(DialogInterface dialog) {
                buttonPositive = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                try {
                    value = new BigDecimal(editTextValue.getText().toString());
                } catch (IllegalArgumentException e) {
                    value = null;
                }
                validateInput();
            }

        });

        alertDialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        );

        return alertDialog;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public void onIdentitiesUpdated(
            scala.collection.immutable.Map<MemberId, IdentityWithBalance> identities) {
        this.identities = identities;
        identitiesSpinnerAdapter.clear();
        identitiesSpinnerAdapter.addAll(JavaConversions.asJavaCollection(identities.values()));
        if (buttonPositive != null) {
            validateInput();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(EXTRA_TO_LIST, toList);
    }

    private void validateInput() {
        boolean isValid = true;
        scala.math.BigDecimal currentBalance = identities == null ?
                from.balanceWithCurrency()._1() :
                identities.apply(from.member().id()).balanceWithCurrency()._1();
        if (value == null) {
            textViewValueError.setText(null);
            isValid = false;
        } else {
            BigDecimal requiredBalance = from.isBanker()
                    ?
                    null
                    :
                    value.multiply(new BigDecimal(to != null ? 1 : toList.size()));
            if (requiredBalance != null
                    && currentBalance.bigDecimal().compareTo(requiredBalance) < 0) {
                textViewValueError.setText(
                        getString(
                                R.string.transfer_value_invalid_format_string,
                                BoardGameActivity.formatCurrencyValue(
                                        getActivity(),
                                        currency,
                                        currentBalance
                                ),
                                BoardGameActivity.formatCurrencyValue(
                                        getActivity(),
                                        currency,
                                        requiredBalance
                                )
                        )
                );
                isValid = false;
            } else {
                textViewValueError.setText(null);
            }
        }
        Set<AccountId> toAccountIds;
        if (to != null) {
            toAccountIds = Collections.singleton(to.account().id());
        } else {
            toAccountIds = new HashSet<>(toList.size());
            for (Player to : toList) {
                toAccountIds.add(to.account().id());
            }
        }
        if (toAccountIds.contains(from.account().id())) {
            textViewFromError.setText(
                    getString(
                            R.string.transfer_from_invalid_format_string,
                            BoardGameActivity.formatNullable(getActivity(), from.member().name())
                    )
            );
            isValid = false;
        } else {
            textViewFromError.setText(null);
        }
        buttonPositive.setEnabled(isValid);
    }

}
