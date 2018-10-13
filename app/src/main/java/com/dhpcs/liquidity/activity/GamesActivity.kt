package com.dhpcs.liquidity.activity

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.fragment.AddGameBottomSheetDialogFragment
import com.dhpcs.liquidity.fragment.CreateGameDialogFragment
import com.dhpcs.liquidity.fragment.GamesFragment
import com.google.zxing.integration.android.IntentIntegrator
import java.util.*

class GamesActivity :
        AppCompatActivity(),
        AddGameBottomSheetDialogFragment.Companion.Listener,
        CreateGameDialogFragment.Companion.Listener,
        GamesFragment.Companion.Listener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_games)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        setSupportActionBar(toolbar)

        val floatingActionButtonAddGame = findViewById<View>(R.id.floatingactionbutton_add_game)!!
        floatingActionButtonAddGame.setOnClickListener {
            AddGameBottomSheetDialogFragment.newInstance().show(
                    supportFragmentManager,
                    AddGameBottomSheetDialogFragment.TAG
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.games_toolbar, menu)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        when {
            result != null -> {
                val contents = result.contents
                if (contents != null) {
                    startActivity(
                            Intent(this, BoardGameActivity::class.java)
                                    .putExtra(BoardGameActivity.EXTRA_ZONE_ID, contents)
                    )
                }
            }
            else ->
                super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_preferences -> {
                startActivity(Intent(this, PreferencesActivity::class.java))
                return true
            }
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNewGameClicked() {
        CreateGameDialogFragment.newInstance().show(
                supportFragmentManager,
                CreateGameDialogFragment.TAG
        )
    }

    override fun onGameDetailsEntered(name: String, currency: Currency) {
        startActivity(
                Intent(this, BoardGameActivity::class.java)
                        .putExtra(BoardGameActivity.EXTRA_CURRENCY, currency)
                        .putExtra(BoardGameActivity.EXTRA_GAME_NAME, name)
        )
    }

    override fun onJoinGameClicked() {
        IntentIntegrator(this)
                .setCaptureActivity(JoinGameActivity::class.java)
                .setDesiredBarcodeFormats(setOf("QR_CODE"))
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                .initiateScan()
    }

    override fun onGameClicked(gameId: Long, zoneId: String, gameName: String?) {
        startActivity(
                Intent(this, BoardGameActivity::class.java)
                        .putExtra(BoardGameActivity.EXTRA_GAME_ID, gameId)
                        .putExtra(BoardGameActivity.EXTRA_ZONE_ID, zoneId)
                        .putExtra(BoardGameActivity.EXTRA_GAME_NAME, gameName)
        )
    }

}
