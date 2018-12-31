package com.dhpcs.liquidity.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlinx.android.synthetic.main.fragment_join_game.*

class JoinGameFragment : Fragment() {

    companion object {

        private const val REQUEST_CODE_GRANT_CAMERA_PERMISSION = 0

    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_join_game, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)

        zxing_barcode_scanner.barcodeView.decoderFactory = DefaultDecoderFactory(
                setOf(BarcodeFormat.QR_CODE)
        )
        zxing_barcode_scanner.setStatusText(getString(R.string.join_game_instruction))
        zxing_barcode_scanner.decodeSingle(object : BarcodeCallback {

            override fun barcodeResult(result: BarcodeResult?) {
                zxing_barcode_scanner.pause()
                model.boardGame.zoneId = result!!.text
                findNavController().navigate(R.id.action_join_game_fragment_to_board_game_graph)
            }

            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (PermissionChecker.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CODE_GRANT_CAMERA_PERMISSION
            )
        } else {
            zxing_barcode_scanner.resume()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_GRANT_CAMERA_PERMISSION ->
                if (grantResults.isEmpty() ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    findNavController().popBackStack()
                }
            else ->
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onPause() {
        super.onPause()
        zxing_barcode_scanner.pauseAndWait()
    }

}
