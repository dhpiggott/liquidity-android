package com.dhpcs.liquidity.fragment

import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.view.*
import android.widget.AdapterView
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.navigation.fragment.findNavController
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.activity.MainActivity
import com.dhpcs.liquidity.provider.LiquidityContract
import kotlinx.android.synthetic.main.fragment_games.*
import org.joda.time.Instant

class GamesFragment : Fragment(),
        AdapterView.OnItemClickListener,
        LoaderManager.LoaderCallbacks<Cursor> {

    companion object {

        private const val GAMES_LOADER = 0
        private const val REFRESH_INTERVAL: Long = 60000

    }

    private val refreshHandler = Handler()
    private val refreshRunnable = Runnable {
        LoaderManager.getInstance(this).restartLoader(GAMES_LOADER, null, this)
    }

    private lateinit var gamesAdapter: SimpleCursorAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        LoaderManager.getInstance(this).initLoader(GAMES_LOADER, null, this)

        gamesAdapter = SimpleCursorAdapter(
                activity,
                R.layout.linearlayout_game, null,
                arrayOf(LiquidityContract.Games.NAME,
                        LiquidityContract.Games.CREATED,
                        LiquidityContract.Games.EXPIRES
                ),
                intArrayOf(R.id.textview_name, R.id.textview_created, R.id.textview_expires),
                0
        )

        gamesAdapter.setViewBinder { view, cursor, columnIndex ->
            val bound = when (columnIndex) {
                cursor.getColumnIndexOrThrow(LiquidityContract.Games.CREATED) -> {
                    val createdTimeMillis = cursor.getLong(columnIndex)
                    val currentTimeMillis = System.currentTimeMillis()
                    (view as TextView).text = requireContext().getString(
                            R.string.game_created_format_string,
                            LiquidityApplication.getRelativeTimeSpanString(
                                    requireContext(),
                                    Instant(createdTimeMillis),
                                    Instant(if (currentTimeMillis < createdTimeMillis) {
                                        createdTimeMillis
                                    } else {
                                        currentTimeMillis
                                    }),
                                    REFRESH_INTERVAL
                            )
                    )
                    true
                }
                cursor.getColumnIndexOrThrow(LiquidityContract.Games.EXPIRES) -> {
                    val expiresTimeMillis = cursor.getLong(columnIndex)
                    val currentTimeMillis = System.currentTimeMillis()
                    (view as TextView).text = requireContext().getString(
                            R.string.game_expires_format_string,
                            LiquidityApplication.getRelativeTimeSpanString(
                                    requireContext(),
                                    Instant(expiresTimeMillis),
                                    Instant(if (currentTimeMillis >= expiresTimeMillis) {
                                        expiresTimeMillis
                                    } else {
                                        currentTimeMillis
                                    }),
                                    REFRESH_INTERVAL
                            )
                    )
                    true
                }
                else ->
                    false
            }
            bound
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.games_toolbar, menu)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        return CursorLoader(
                requireContext(),
                LiquidityContract.Games.CONTENT_URI,
                arrayOf(LiquidityContract.Games.ID,
                        LiquidityContract.Games.ZONE_ID,
                        LiquidityContract.Games.CREATED,
                        LiquidityContract.Games.EXPIRES,
                        LiquidityContract.Games.NAME
                ),
                "${LiquidityContract.Games.EXPIRES} > ?",
                arrayOf(java.lang.Long.toString(System.currentTimeMillis())),
                "${LiquidityContract.Games.CREATED} DESC"
        )
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_games, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        listview_games.adapter = gamesAdapter
        listview_games.emptyView = view.findViewById(R.id.textview_empty)
        listview_games.onItemClickListener = this
        floatingactionbutton_add_game.setOnClickListener {
            AddGameBottomSheetDialogFragment.newInstance().show(
                    fragmentManager,
                    AddGameBottomSheetDialogFragment.TAG
            )
        }
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val cursor = parent.getItemAtPosition(position) as Cursor

        val model = ViewModelProviders.of(requireActivity())
                .get(MainActivity.Companion.BoardGameModel::class.java)
        model.boardGame.zoneId = cursor.getString(cursor.getColumnIndexOrThrow(
                LiquidityContract.Games.ZONE_ID
        ))
        findNavController().navigate(R.id.action_games_fragment_to_board_game_graph)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
        gamesAdapter.changeCursor(data)
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL)
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        gamesAdapter.changeCursor(null)
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onDestroy()
    }

}
