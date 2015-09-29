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
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.dhpcs.liquidity.BoardGame.Identity;
import com.dhpcs.liquidity.BoardGame.IdentityWithBalance;
import com.dhpcs.liquidity.BoardGame.Player;
import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activity.BoardGameActivity;
import com.dhpcs.liquidity.models.MemberId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;

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
    private Button buttonPositive;

    private BigDecimal value;
    private int scale;
    private BigDecimal scaledValue;

    private scala.collection.immutable.Map<MemberId, IdentityWithBalance> identities;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        listener = (Listener) activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection unchecked
        List<IdentityWithBalance> identities = (List<IdentityWithBalance>)
                getArguments().getSerializable(ARG_IDENTITIES);
        //noinspection unchecked
        List<Player> players = (List<Player>) getArguments().getSerializable(ARG_PLAYERS);
        //noinspection unchecked
        currency = (Option<Either<String, Currency>>) getArguments().getSerializable(ARG_CURRENCY);
        from = (IdentityWithBalance) getArguments().getSerializable(ARG_FROM);
        to = (Player) getArguments().getSerializable(ARG_TO);
        //noinspection unchecked
        toList = to != null ? null : (savedInstanceState != null
                ?
                (ArrayList<Player>) savedInstanceState.getSerializable(EXTRA_TO_LIST)
                :
                new ArrayList<Player>());

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
        textViewValueError = (TextView) view.findViewById(R.id.textview_value_error);
        final TextView textViewMultiplier = (TextView) view.findViewById(R.id.textview_multiplier);
        RadioGroup radioGroupValueMultiplier =
                (RadioGroup) view.findViewById(R.id.radiogroup_value_multiplier);
        Spinner spinnerFrom = (Spinner) view.findViewById(R.id.spinner_from);
        LinearLayout linearLayoutTo = (LinearLayout) view.findViewById(R.id.linearlayout_to);
        Spinner spinnerTo = (Spinner) view.findViewById(R.id.spinner_to);
        View viewTopDivider = view.findViewById(R.id.view_top_divider);
        View viewBottomDivider = view.findViewById(R.id.view_bottom_divider);

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
                                            scaledValue
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

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    value = new BigDecimal(s.toString());
                } catch (IllegalArgumentException e) {
                    value = null;
                }
                if (buttonPositive != null) {
                    validateInput();
                }
            }

        });
        radioGroupValueMultiplier.setOnCheckedChangeListener(
                new RadioGroup.OnCheckedChangeListener() {

                    @Override
                    public void onCheckedChanged(RadioGroup group, int checkedId) {
                        switch (checkedId) {
                            case R.id.radiobutton_value_multiplier_million:
                                textViewMultiplier.setText(R.string.value_multiplier_million);
                                scale = 6;
                                break;
                            case R.id.radiobutton_value_multiplier_thousand:
                                textViewMultiplier.setText(R.string.value_multiplier_thousand);
                                scale = 3;
                                break;
                            case R.id.radiobutton_value_multiplier_none:
                                textViewMultiplier.setText(R.string.value_multiplier_none);
                                scale = 0;
                                break;
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
            viewTopDivider.setVisibility(View.VISIBLE);
            viewBottomDivider.setVisibility(View.VISIBLE);
            //noinspection unchecked
            List<Player> players = (List<Player>) getArguments().getSerializable(ARG_PLAYERS);
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
        scala.math.BigDecimal currentBalance = identities == null ?
                from.balanceWithCurrency()._1() :
                identities.apply(from.member().id()).balanceWithCurrency()._1();
        if (value == null) {
            scaledValue = null;
            textViewValueError.setText(null);
            buttonPositive.setEnabled(false);
        } else {
            scaledValue = value.scaleByPowerOfTen(scale);
            BigDecimal requiredBalance = from.isBanker() ? null :
                    scaledValue.multiply(new BigDecimal(to != null ? 1 : toList.size()));
            if (requiredBalance != null
                    && currentBalance.bigDecimal().compareTo(requiredBalance) < 0) {
                textViewValueError.setText(
                        getString(
                                R.string.transfer_value_error_invalid_format_string,
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
                buttonPositive.setEnabled(false);
            } else {
                textViewValueError.setText(null);
                buttonPositive.setEnabled(true);
            }
        }
    }

}
