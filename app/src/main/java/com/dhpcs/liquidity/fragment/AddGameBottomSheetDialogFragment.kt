package com.dhpcs.liquidity.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController

import com.dhpcs.liquidity.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.fragment_add_game_bottom_sheet_dialog.*

class AddGameBottomSheetDialogFragment : BottomSheetDialogFragment() {

    companion object {

        const val TAG = "add_game_bottom_sheet_dialog_fragment"

        fun newInstance(): AddGameBottomSheetDialogFragment = AddGameBottomSheetDialogFragment()

    }

    @SuppressLint("InflateParams")
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return requireActivity().layoutInflater.inflate(
                R.layout.fragment_add_game_bottom_sheet_dialog,
                null
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textview_new_game.setOnClickListener {
            CreateGameDialogFragment.newInstance().show(
                    fragmentManager,
                    CreateGameDialogFragment.TAG
            )
            dismiss()
        }
        textview_join_game.setOnClickListener {
            findNavController().navigate(R.id.action_games_fragment_to_join_game_fragment)
            dismiss()
        }
    }

}
