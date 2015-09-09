package com.dhpcs.liquidity.fragment;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.package$;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CreateGameDialogFragment extends DialogFragment {

    public interface Listener {

        void onGameDetailsEntered(String name, Currency currency);

    }

    public static final String TAG = "create_game_dialog_fragment";

    private static final int MAXIMUM_NAME_LENGTH = package$.MODULE$.MaxStringLength();

    private static class CurrenciesAdapter extends ArrayAdapter<Currency> {

        public CurrenciesAdapter(Context context, int resource, List<Currency> currencies) {
            super(context, resource, currencies);
        }

        @TargetApi(Build.VERSION_CODES.KITKAT)
        private View bindView(TextView textView, Currency currency) {
            String displayName = Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT
                    ?
                    null
                    :
                    currency.getDisplayName();
            String symbolAndOrName;
            if (currency.getSymbol().equals(currency.getCurrencyCode())) {
                symbolAndOrName = displayName == null ? null : displayName;
            } else {
                symbolAndOrName = displayName == null
                        ?
                        currency.getSymbol()
                        :
                        getContext()
                                .getString(
                                        R.string.game_currency_symbol_and_name_format_string,
                                        currency.getSymbol(),
                                        displayName
                                );
            }
            if (symbolAndOrName == null) {
                textView.setText(currency.getCurrencyCode());
            } else {
                textView.setText(
                        getContext().getString(
                                R.string.game_currency_format_string,
                                currency.getCurrencyCode(),
                                symbolAndOrName
                        )
                );
            }
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

    public static CreateGameDialogFragment newInstance() {
        return new CreateGameDialogFragment();
    }

    private Listener listener;

    private Button buttonPositive;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        listener = (Listener) activity;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        @SuppressLint("InflateParams") View view = getActivity().getLayoutInflater().inflate(
                R.layout.fragment_create_game_dialog,
                null
        );

        Set<Currency> currencies = new HashSet<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            for (Locale locale : Locale.getAvailableLocales()) {
                try {
                    currencies.add(Currency.getInstance(locale));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } else {
            currencies.addAll(Currency.getAvailableCurrencies());
        }
        final ArrayAdapter<Currency> currenciesSpinnerAdapter = new CurrenciesAdapter(
                getActivity(),
                android.R.layout.simple_spinner_item,
                new ArrayList<>(currencies)
        );
        currenciesSpinnerAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );
        currenciesSpinnerAdapter.sort(new Comparator<Currency>() {

            @Override
            public int compare(Currency lhs, Currency rhs) {
                return lhs.getCurrencyCode().compareTo(rhs.getCurrencyCode());
            }

        });

        final EditText editTextGameName = (EditText) view.findViewById(R.id.edittext_game_name);
        final TextView textViewGameNameCharacterCount = (TextView)
                view.findViewById(R.id.textview_game_name_character_count);
        final Spinner spinnerCurrency = (Spinner) view.findViewById(R.id.spinner_game_currency);

        final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                .setTitle(
                        getString(
                                R.string.enter_game_details
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
                                    listener.onGameDetailsEntered(
                                            editTextGameName.getText().toString(),
                                            currenciesSpinnerAdapter.getItem(
                                                    spinnerCurrency.getSelectedItemPosition()
                                            )
                                    );
                                }
                            }

                        }
                )
                .create();

        editTextGameName.addTextChangedListener(new TextWatcher() {

            {
                updateCharacterCount(editTextGameName.getText());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateCharacterCount(s);
                if (buttonPositive != null) {
                    validateInput(s);
                }
            }

            private void updateCharacterCount(Editable s) {
                textViewGameNameCharacterCount.setText(
                        getString(
                                R.string.character_count_format_string,
                                s.length(),
                                MAXIMUM_NAME_LENGTH
                        )
                );
            }

        });
        spinnerCurrency.setAdapter(currenciesSpinnerAdapter);

        spinnerCurrency.setSelection(
                currenciesSpinnerAdapter.getPosition(
                        Currency.getInstance(Locale.getDefault())
                )
        );

        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {

            @Override
            public void onShow(DialogInterface dialog) {
                buttonPositive = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                validateInput(editTextGameName.getText());
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

    private void validateInput(CharSequence gameName) {
        buttonPositive.setEnabled(
                !TextUtils.isEmpty(gameName) && gameName.length() <= MAXIMUM_NAME_LENGTH
        );
    }

}
