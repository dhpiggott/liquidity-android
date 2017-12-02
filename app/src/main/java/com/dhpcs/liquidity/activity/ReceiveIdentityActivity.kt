package com.dhpcs.liquidity.activity

import android.app.Activity
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.WindowManager
import android.widget.ImageView
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.boardgame.BoardGame.IdentityWithBalance
import com.dhpcs.liquidity.model.PublicKey
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

        val publicKey = intent.getSerializableExtra(EXTRA_PUBLIC_KEY) as PublicKey

        val imageViewQrCode = findViewById<ImageView>(R.id.imageview_qr_code)!!
        imageViewQrCode.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            imageViewQrCode.setImageBitmap(
                    (QRCode.from(publicKey.value().base64())
                            .withSize(right - left, bottom - top) as QRCode)
                            .bitmap()
            )
        }
    }

    override fun onIdentityReceived(identity: IdentityWithBalance) {
        setResult(Activity.RESULT_OK)
        finish()
    }

}
