package com.dhpcs.liquidity.fragment

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import com.dhpcs.liquidity.activity.MainActivity.Companion.liveData
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState
import kotlinx.android.synthetic.main.fragment_board_game.*
import java.util.*

class BoardGameFragment : Fragment() {

    private var model: MainActivity.Companion.BoardGameModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_board_game, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        slidinguppanellayout!!.addPanelSlideListener(
                object : SlidingUpPanelLayout.PanelSlideListener {

                    override fun onPanelSlide(view: View, v: Float) {}

                    override fun onPanelStateChanged(panel: View,
                                                     previousState: PanelState,
                                                     newState: PanelState
                    ) {
                        when (newState) {
                            SlidingUpPanelLayout.PanelState.EXPANDED ->
                                requireActivity().setTitle(R.string.transfers)
                            SlidingUpPanelLayout.PanelState.COLLAPSED ->
                                if (model!!.boardGame.joinState ==
                                        BoardGame.Companion.JoinState.JOINED) {
                                    requireActivity().title = LiquidityApplication.formatNullable(
                                            requireContext(),
                                            model!!.boardGame.gameName
                                    )
                                }
                            SlidingUpPanelLayout.PanelState.ANCHORED,
                            SlidingUpPanelLayout.PanelState.HIDDEN,
                            SlidingUpPanelLayout.PanelState.DRAGGING -> {
                            }
                        }
                        requireActivity().invalidateOptionsMenu()
                    }

                }
        )

        model!!.boardGame.liveData { it.joinStateObservable() }.observe(this, Observer {
            when (it!!) {
                BoardGame.Companion.JoinState.QUIT -> {

                    slidinguppanellayout.visibility = View.GONE

                    progressbar_state.visibility = View.VISIBLE
                    textview_state.visibility = View.VISIBLE
                    textview_state.setText(R.string.join_state_quit)

                }
                BoardGame.Companion.JoinState.FAILED -> {

                    Toast.makeText(
                            requireContext(),
                            R.string.join_state_failed,
                            Toast.LENGTH_LONG
                    ).show()
                    findNavController().popBackStack()

                }
                BoardGame.Companion.JoinState.ERROR -> {

                    Toast.makeText(
                            requireContext(),
                            R.string.join_state_error,
                            Toast.LENGTH_LONG
                    ).show()
                    findNavController().popBackStack()

                }
                BoardGame.Companion.JoinState.CREATING -> {

                    slidinguppanellayout!!.visibility = View.GONE

                    progressbar_state.visibility = View.VISIBLE
                    textview_state.visibility = View.VISIBLE
                    textview_state.setText(R.string.join_state_creating)

                }
                BoardGame.Companion.JoinState.JOINING -> {

                    slidinguppanellayout!!.visibility = View.GONE

                    progressbar_state.visibility = View.VISIBLE
                    textview_state.visibility = View.VISIBLE
                    textview_state.setText(R.string.join_state_joining)

                }
                BoardGame.Companion.JoinState.JOINED -> {

                    textview_state.text = null
                    textview_state.visibility = View.GONE
                    progressbar_state.visibility = View.GONE

                    slidinguppanellayout!!.visibility = View.VISIBLE

                }
            }
            requireActivity().invalidateOptionsMenu()
        })
        model!!.boardGame.liveData { it.gameNameObservable }.observe(this, Observer {
            if (findNavController().currentDestination!!.id == R.id.board_game_fragment) {
                requireActivity().title = it
            }
        })
        model!!.selectedIdentityLiveData.observe(this, Observer {
            requireActivity().invalidateOptionsMenu()
        })
        model!!.createGameErrorLiveData.observe(this, Observer {
            if (it != MainActivity.Companion.Optional.None) {
                Toast.makeText(
                        requireContext(),
                        (it as? MainActivity.Companion.Optional.Some)?.value,
                        Toast.LENGTH_LONG
                ).show()
                findNavController().popBackStack()
                model!!.createGameError(MainActivity.Companion.Optional.None)
            }
        })
        model!!.commandErrorsLiveData.observe(this, Observer {
            if (it != MainActivity.Companion.Optional.None) {
                Toast.makeText(
                        requireContext(),
                        (it as? MainActivity.Companion.Optional.Some)?.value,
                        Toast.LENGTH_LONG
                ).show()
                model!!.commandError(MainActivity.Companion.Optional.None)
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.board_game_toolbar, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val isJoined = model!!.boardGame.joinState == BoardGame.Companion.JoinState.JOINED
        val selectedIdentity = model!!.selectedIdentity
        val isPanelCollapsed = slidinguppanellayout.panelState == PanelState.COLLAPSED
        menu.findItem(R.id.add_players_fragment).isVisible =
                isJoined
        menu.findItem(R.id.action_group_transfer).isVisible =
                isJoined &&
                selectedIdentity is MainActivity.Companion.Optional.Some &&
                isPanelCollapsed
        menu.findItem(R.id.action_change_game_name).isVisible =
                isJoined
        menu.findItem(R.id.action_change_identity_name).isVisible =
                isJoined &&
                (selectedIdentity as? MainActivity.Companion.Optional.Some)?.value?.isBanker ==
                false &&
                isPanelCollapsed
        menu.findItem(R.id.action_create_identity).isVisible =
                isJoined &&
                isPanelCollapsed
        menu.findItem(R.id.action_restore_identity).isVisible =
                isJoined &&
                isPanelCollapsed && model!!.boardGame.hiddenIdentities.isNotEmpty()
        menu.findItem(R.id.action_delete_identity).isVisible =
                isJoined &&
                selectedIdentity is MainActivity.Companion.Optional.Some &&
                isPanelCollapsed
        menu.findItem(R.id.receive_identity_fragment).isVisible =
                isJoined &&
                isPanelCollapsed
        menu.findItem(R.id.action_transfer_identity).isVisible =
                isJoined &&
                selectedIdentity is MainActivity.Companion.Optional.Some &&
                isPanelCollapsed
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_group_transfer -> {
                val identity = model!!.selectedIdentity
                when (identity) {
                    is MainActivity.Companion.Optional.None -> {
                    }
                    is MainActivity.Companion.Optional.Some -> {
                        TransferToPlayerDialogFragment.newInstance(
                                ArrayList(model!!.boardGame.identities.values),
                                ArrayList(model!!.boardGame.players.values),
                                model!!.boardGame.currency,
                                identity.value,
                                null
                        ).show(childFragmentManager, TransferToPlayerDialogFragment.TAG)
                    }
                }
                true
            }
            R.id.action_change_game_name -> {
                EnterGameNameDialogFragment.newInstance(model!!.boardGame.gameName).show(
                        childFragmentManager,
                        EnterGameNameDialogFragment.TAG
                )
                true
            }
            R.id.action_change_identity_name -> {
                val identity = model!!.selectedIdentity
                when (identity) {
                    is MainActivity.Companion.Optional.None -> {
                    }
                    is MainActivity.Companion.Optional.Some -> {
                        EnterIdentityNameDialogFragment.newInstance(identity.value).show(
                                childFragmentManager,
                                EnterIdentityNameDialogFragment.TAG
                        )
                    }
                }
                true
            }
            R.id.action_create_identity -> {
                CreateIdentityDialogFragment.newInstance().show(
                        childFragmentManager,
                        CreateIdentityDialogFragment.TAG
                )
                true
            }
            R.id.action_restore_identity -> {
                RestoreIdentityDialogFragment.newInstance(
                        ArrayList(model!!.boardGame.hiddenIdentities.values)
                ).show(childFragmentManager, RestoreIdentityDialogFragment.TAG)
                true
            }
            R.id.action_delete_identity -> {
                val identity = model!!.selectedIdentity
                when (identity) {
                    is MainActivity.Companion.Optional.None -> {
                    }
                    is MainActivity.Companion.Optional.Some -> {
                        ConfirmIdentityDeletionDialogFragment.newInstance(identity.value).show(
                                childFragmentManager,
                                ConfirmIdentityDeletionDialogFragment.TAG
                        )
                    }
                }
                true
            }
            R.id.action_transfer_identity -> {
                val identity = model!!.selectedIdentity
                when (identity) {
                    is MainActivity.Companion.Optional.None -> {
                    }
                    is MainActivity.Companion.Optional.Some -> {
                        findNavController().navigate(
                                BoardGameFragmentDirections
                                        .ActionBoardGameFragmentToTransferIdentityFragment(
                                                identity.value
                                        )
                        )
                    }
                }
                true
            }
            else ->
                super.onOptionsItemSelected(item)
        }
    }

}
