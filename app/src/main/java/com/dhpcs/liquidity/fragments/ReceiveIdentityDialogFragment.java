package com.dhpcs.liquidity.fragments;

import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.dhpcs.liquidity.R;
import com.dhpcs.liquidity.models.PublicKey;
import com.google.common.io.BaseEncoding;

import net.glxn.qrgen.android.QRCode;

public class ReceiveIdentityDialogFragment extends DialogFragment {

    private static final String ARG_PUBLIC_KEY = "public_key";

    public static ReceiveIdentityDialogFragment newInstance(PublicKey publicKey) {
        ReceiveIdentityDialogFragment receiveIdentityDialogFragment = new ReceiveIdentityDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_PUBLIC_KEY, publicKey);
        receiveIdentityDialogFragment.setArguments(args);
        return receiveIdentityDialogFragment;
    }

    private PublicKey publicKey;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Light);
        publicKey = (PublicKey) getArguments().getSerializable(ARG_PUBLIC_KEY);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_receive_identity_dialog, container, false);

        ImageView imageViewQrCode = (ImageView) view.findViewById(R.id.imageview_qr_code);
        imageViewQrCode.setImageBitmap(
                ((QRCode) QRCode.from(
                        BaseEncoding.base64().encode(publicKey.value())
                        // TODO
                ).withSize(1000, 1000))
                        .bitmap()
        );

        return view;
    }

}
