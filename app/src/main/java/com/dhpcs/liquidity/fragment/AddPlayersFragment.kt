package com.dhpcs.liquidity.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import kotlinx.android.synthetic.main.fragment_add_players.*
import net.glxn.qrgen.android.QRCode

class AddPlayersFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_add_players, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)

        textview_game_name.text = getString(
                R.string.add_players_game_name_format_string,
                LiquidityApplication.formatNullable(requireContext(), model.boardGame.gameName)
        )
        imageview_qr_code.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            imageview_qr_code.setImageBitmap(
                    (QRCode.from(model.boardGame.zoneId)
                            .withSize(right - left, bottom - top) as QRCode)
                            .bitmap()
            )
        }
    }

}
