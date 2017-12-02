package com.dhpcs.liquidity.activity

import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.model.ZoneId
import net.glxn.qrgen.android.QRCode
import scala.Option

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
                .getSerializable(BoardGameChildActivity.EXTRA_ZONE_ID) as ZoneId
        val gameName = intent.getSerializableExtra(EXTRA_GAME_NAME) as Option<String>

        val textViewGameName = findViewById<TextView>(R.id.textview_game_name)!!
        textViewGameName.text = getString(
                R.string.add_players_game_name_format_string,
                BoardGameActivity.formatNullable(this, gameName)
        )
        val imageViewQrCode = findViewById<ImageView>(R.id.imageview_qr_code)!!
        imageViewQrCode.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            imageViewQrCode.setImageBitmap(
                    (QRCode.from(zoneId.id())
                            .withSize(right - left, bottom - top) as QRCode)
                            .bitmap()
            )
        }
    }

}
