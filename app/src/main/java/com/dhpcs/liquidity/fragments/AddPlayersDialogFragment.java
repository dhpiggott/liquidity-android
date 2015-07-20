package com.dhpcs.liquidity.fragments;

import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.ZoneId;

import net.glxn.qrgen.android.QRCode;

public class AddPlayersDialogFragment extends DialogFragment {

    private static final String ARG_ZONE_ID = "zone_id";

    public static AddPlayersDialogFragment newInstance(ZoneId zoneId) {
        AddPlayersDialogFragment addPlayersDialogFragment = new AddPlayersDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_ZONE_ID, zoneId);
        addPlayersDialogFragment.setArguments(args);
        return addPlayersDialogFragment;
    }

    private ZoneId zoneId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Light);
        zoneId = (ZoneId) getArguments().getSerializable(ARG_ZONE_ID);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_players_dialog, container, false);

        final ImageView imageViewQrCode = (ImageView) view.findViewById(R.id.imageview_qr_code);
        imageViewQrCode.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {

            @Override
            public void onLayoutChange(View v,
                                       int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                imageViewQrCode.setImageBitmap(
                        ((QRCode) QRCode.from(zoneId.id().toString())
                                .withSize(right - left, bottom - top))
                                .bitmap()
                );
            }

        });

        return view;
    }

}
