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

    private static final String ARG_ZONE_ID = "zoneId";

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

        ImageView imageViewQrCode = (ImageView) view.findViewById(R.id.imageview_qr_code);
        imageViewQrCode.setImageBitmap(
                ((QRCode) QRCode.from(
                        zoneId.id().toString()
                        // TODO
                ).withSize(1000, 1000))
                        .bitmap()
        );

        return view;
    }

}
