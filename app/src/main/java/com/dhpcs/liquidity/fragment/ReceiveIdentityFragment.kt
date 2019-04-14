package com.dhpcs.liquidity.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import com.dhpcs.liquidity.activity.MainActivity.Companion.observableLiveData
import kotlinx.android.synthetic.main.fragment_receive_identity.*
import net.glxn.qrgen.android.QRCode

class ReceiveIdentityFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_receive_identity, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)

        val publicKey = model.serverConnection.clientKey
        imageview_qr_code.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            imageview_qr_code.setImageBitmap(
                    (QRCode.from(okio.ByteString.of(*publicKey.toByteArray()).base64())
                            .withSize(right - left, bottom - top) as QRCode)
                            .bitmap()
            )
        }

        model.boardGame.observableLiveData {
            it.addedIdentitiesObservable
        }.observe(this, Observer {
            findNavController().popBackStack()
        })
    }

}
