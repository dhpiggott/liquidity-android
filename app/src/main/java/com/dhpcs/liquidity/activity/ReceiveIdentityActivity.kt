package com.dhpcs.liquidity.activity

import android.app.Activity
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.WindowManager
import android.widget.ImageView
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.R
import com.google.protobuf.ByteString
import net.glxn.qrgen.android.QRCode

class ReceiveIdentityActivity : BoardGameChildActivity() {

    companion object {

        const val EXTRA_PUBLIC_KEY = "public_key"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_receive_identity)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        setSupportActionBar(toolbar)
        val actionBar = supportActionBar!!
        actionBar.setDisplayHomeAsUpEnabled(true)

        val publicKey = intent.getSerializableExtra(EXTRA_PUBLIC_KEY) as ByteString

        val imageViewQrCode = findViewById<ImageView>(R.id.imageview_qr_code)!!
        imageViewQrCode.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            imageViewQrCode.setImageBitmap(
                    (QRCode.from(okio.ByteString.of(*publicKey.toByteArray()).base64())
                            .withSize(right - left, bottom - top) as QRCode)
                            .bitmap()
            )
        }
    }

    override fun onIdentityReceived(identity: BoardGame.Companion.IdentityWithBalance) {
        setResult(Activity.RESULT_OK)
        finish()
    }

}
