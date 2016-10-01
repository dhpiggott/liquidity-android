package com.dhpcs.liquidity.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.support.design.widget.BottomSheetDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dhpcs.liquidity.R;

public class AddGameBottomSheetDialogFragment extends BottomSheetDialogFragment {

    public interface Listener {

        void onNewGameClicked();

        void onJoinGameClicked();

    }

    public static final String TAG = "add_game_bottom_sheet_dialog_fragment";

    public static AddGameBottomSheetDialogFragment newInstance() {
        return new AddGameBottomSheetDialogFragment();
    }

    private Listener listener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        listener = (Listener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        @SuppressLint("InflateParams") View view = getActivity().getLayoutInflater().inflate(
                R.layout.fragment_add_game_bottom_sheet_dialog,
                null
        );

        view.findViewById(R.id.textview_new_game).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onNewGameClicked();
                }
                dismiss();
            }

        });
        view.findViewById(R.id.textview_join_game).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onJoinGameClicked();
                }
                dismiss();
            }

        });

        return view;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

}
