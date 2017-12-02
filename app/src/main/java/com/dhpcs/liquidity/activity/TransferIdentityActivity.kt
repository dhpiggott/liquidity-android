package com.dhpcs.liquidity.activity

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v7.widget.Toolbar
import android.view.KeyEvent
import android.widget.LinearLayout
import com.dhpcs.liquidity.R
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import scala.Option

class TransferIdentityActivity : BoardGameChildActivity() {

    companion object {

        const val EXTRA_IDENTITY_NAME_HOLDER = "identity_name_holder"
        const val EXTRA_IDENTITY_NAME = "identity_name"

        private const val REQUEST_CODE_GRANT_CAMERA_PERMISSION = 0

    }

    private var linearLayoutTransferIdentity: LinearLayout? = null

    private var capture: CaptureManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transfer_identity)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        setSupportActionBar(toolbar)
        val actionBar = supportActionBar!!
        actionBar.setDisplayHomeAsUpEnabled(true)

        val identityName = intent
                .getBundleExtra(EXTRA_IDENTITY_NAME_HOLDER)
                .getSerializable(EXTRA_IDENTITY_NAME) as Option<String>

        linearLayoutTransferIdentity = findViewById(R.id.linearlayout_transfer_identity)

        val barcodeScannerView = findViewById<DecoratedBarcodeView>(R.id.zxing_barcode_scanner)!!
        barcodeScannerView.setStatusText(
                getString(
                        R.string.transfer_identity_identity_name_format_string,
                        BoardGameActivity.formatNullable(this, identityName)
                )
        )

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
                            linearLayoutTransferIdentity!!,
                            R.string.camera_permission_rationale,
                            Snackbar.LENGTH_INDEFINITE
                    ).setAction(R.string.settings) {
                        try {
                            startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(Uri.parse("package:" + packageName))
                            )
                        } catch (ignored1: ActivityNotFoundException) {
                            try {
                                startActivity(
                                        Intent(Settings.ACTION_APPLICATION_SETTINGS)
                                )
                            } catch (ignored2: ActivityNotFoundException) {
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
                    linearLayoutTransferIdentity!!,
                    R.string.camera_permission_rationale,
                    Snackbar.LENGTH_INDEFINITE
            ).setAction(R.string.ok) {
                ActivityCompat.requestPermissions(
                        this@TransferIdentityActivity,
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
