package com.dhpcs.liquidity.fragment;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.activity.BoardGameActivity;
import com.dhpcs.liquidity.boardgame.BoardGame.Identity;
import com.dhpcs.liquidity.boardgame.BoardGame.IdentityWithBalance;
import com.dhpcs.liquidity.model.MemberId;
import com.dhpcs.liquidity.model.ZoneId;
import com.dhpcs.liquidity.view.Identicon;

import java.util.ArrayList;

import scala.collection.JavaConversions;

public class RestoreIdentityDialogFragment extends AppCompatDialogFragment {

    public interface Listener {

        void onIdentityRestorationRequested(Identity identity);

    }

    public static final String TAG = "restore_identity_dialog_fragment";

    private static class IdentitiesAdapter extends ArrayAdapter<IdentityWithBalance> {

        IdentitiesAdapter(Context context) {
            super(context,
                    R.layout.linearlayout_identity,
                    R.id.textview_name);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            IdentityWithBalance identity = getItem(position);

            Identicon identiconId = view.findViewById(R.id.identicon_id);
            TextView textViewName = view.findViewById(R.id.textview_name);
            TextView textViewBalance = view.findViewById(R.id.textview_balance);

            assert identity != null;
            ZoneId zoneId = identity.zoneId();
            MemberId memberId = identity.member().id();
            String name = BoardGameActivity.formatNullable(
                    getContext(),
                    identity.member().name()
            );
            String balance = BoardGameActivity.formatCurrencyValue(
                    getContext(),
                    identity.balanceWithCurrency()._2(),
                    identity.balanceWithCurrency()._1()
            );

            identiconId.show(zoneId, memberId);
            textViewName.setText(name);
            textViewBalance.setText(balance);

            return view;
        }

    }

    private static final String ARG_IDENTITIES = "identities";

    public static RestoreIdentityDialogFragment newInstance(
            scala.collection.Iterable<IdentityWithBalance> identities) {
        RestoreIdentityDialogFragment restoreIdentityDialogFragment =
                new RestoreIdentityDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(
                ARG_IDENTITIES,
                new ArrayList<>(
                        JavaConversions.bufferAsJavaList(
                                identities.toBuffer()
                        )
                )
        );
        restoreIdentityDialogFragment.setArguments(args);
        return restoreIdentityDialogFragment;
    }

    private Listener listener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        listener = (Listener) context;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final ArrayAdapter<IdentityWithBalance> identitiesAdapter = new IdentitiesAdapter(
                getActivity()
        );
        ArrayList<IdentityWithBalance> identities =
                (ArrayList<IdentityWithBalance>) getArguments().getSerializable(ARG_IDENTITIES);
        assert identities != null;
        identitiesAdapter.addAll(identities);
        identitiesAdapter.sort(BoardGameActivity.playerComparator(getActivity()));
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.choose_identity_to_restore)
                .setAdapter(identitiesAdapter, (dialog, which) -> {
                    if (listener != null) {
                        listener.onIdentityRestorationRequested(
                                identitiesAdapter.getItem(which)
                        );
                    }
                })
                .create();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

}
