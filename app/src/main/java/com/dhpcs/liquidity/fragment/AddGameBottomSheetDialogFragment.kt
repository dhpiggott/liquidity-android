package com.dhpcs.liquidity.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.support.design.widget.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.dhpcs.liquidity.R

class AddGameBottomSheetDialogFragment : BottomSheetDialogFragment() {

    companion object {

        interface Listener {

            fun onNewGameClicked()

            fun onJoinGameClicked()

        }

        const val TAG = "add_game_bottom_sheet_dialog_fragment"

        fun newInstance(): AddGameBottomSheetDialogFragment = AddGameBottomSheetDialogFragment()

    }

    private var listener: Listener? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        listener = context as Listener?
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        @SuppressLint("InflateParams") val view = activity!!.layoutInflater.inflate(
                R.layout.fragment_add_game_bottom_sheet_dialog, null
        )

        view.findViewById<View>(R.id.textview_new_game).setOnClickListener {
            listener?.onNewGameClicked()
            dismiss()
        }
        view.findViewById<View>(R.id.textview_join_game).setOnClickListener {
            listener?.onJoinGameClicked()
            dismiss()
        }

        return view
    }

}
