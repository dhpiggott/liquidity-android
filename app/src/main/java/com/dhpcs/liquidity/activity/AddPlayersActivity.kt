package com.dhpcs.liquidity.activity

import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.dhpcs.liquidity.R
import net.glxn.qrgen.android.QRCode

class AddPlayersActivity : BoardGameChildActivity() {

    companion object {

        const val EXTRA_GAME_NAME = "game_name"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_add_players)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        setSupportActionBar(toolbar)
        val actionBar = supportActionBar!!
        actionBar.setDisplayHomeAsUpEnabled(true)

        val zoneId = intent
                .getBundleExtra(BoardGameChildActivity.EXTRA_ZONE_ID_HOLDER)
                .getString(BoardGameChildActivity.EXTRA_ZONE_ID)
        val gameName = intent.getStringExtra(EXTRA_GAME_NAME)

        val textViewGameName = findViewById<TextView>(R.id.textview_game_name)!!
        textViewGameName.text = getString(
                R.string.add_players_game_name_format_string,
                BoardGameActivity.formatNullable(this, gameName)
        )
        val imageViewQrCode = findViewById<ImageView>(R.id.imageview_qr_code)!!
        imageViewQrCode.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            imageViewQrCode.setImageBitmap(
                    (QRCode.from(zoneId)
                            .withSize(right - left, bottom - top) as QRCode)
                            .bitmap()
            )
        }
    }

}
