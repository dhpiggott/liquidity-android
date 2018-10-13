package com.dhpcs.liquidity.activity

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import com.dhpcs.liquidity.R
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.google.android.material.snackbar.Snackbar

class JoinGameActivity : AppCompatActivity() {

    companion object {

        private const val REQUEST_CODE_GRANT_CAMERA_PERMISSION = 0

    }

    private var linearLayoutJoinGame: LinearLayout? = null

    private var capture: CaptureManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_game)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        setSupportActionBar(toolbar)
        val actionBar = supportActionBar!!
        actionBar.setDisplayHomeAsUpEnabled(true)

        linearLayoutJoinGame = findViewById(R.id.linearlayout_join_game)

        val barcodeScannerView = findViewById<DecoratedBarcodeView>(R.id.zxing_barcode_scanner)!!
        barcodeScannerView.setStatusText(getString(R.string.join_game_instruction))

        capture = CaptureManager(this, barcodeScannerView)
        capture!!.initializeFromIntent(intent, savedInstanceState)
        capture!!.decode()
    }

    override fun onStart() {
        super.onStart()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {
            capture!!.onResume()
        }
    }

    /*
     * We handle these events so they don't launch the Camera app.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_FOCUS, KeyEvent.KEYCODE_CAMERA -> true
        else -> super.onKeyDown(keyCode, event)
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_GRANT_CAMERA_PERMISSION ->
                if (grantResults.size != 1 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Snackbar.make(
                            linearLayoutJoinGame!!,
                            R.string.camera_permission_rationale,
                            Snackbar.LENGTH_INDEFINITE
                    ).setAction(R.string.settings) {
                        try {
                            startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(Uri.parse("package:" + packageName))
                            )
                        } catch (_: ActivityNotFoundException) {
                            try {
                                startActivity(
                                        Intent(Settings.ACTION_APPLICATION_SETTINGS)
                                )
                            } catch (_: ActivityNotFoundException) {
                                startActivity(
                                        Intent(Settings.ACTION_SETTINGS)
                                )
                            }
                        }
                    }.show()
                }
            else ->
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onPause() {
        super.onPause()
        capture!!.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        capture!!.onSaveInstanceState(outState!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        capture!!.onDestroy()
    }

    private fun requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.CAMERA
                )) {
            Snackbar.make(
                    linearLayoutJoinGame!!,
                    R.string.camera_permission_rationale,
                    Snackbar.LENGTH_INDEFINITE
            ).setAction(R.string.ok) {
                ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.CAMERA),
                        REQUEST_CODE_GRANT_CAMERA_PERMISSION
                )
            }.show()
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CODE_GRANT_CAMERA_PERMISSION
            )
        }
    }

}
