package com.dhpcs.liquidity.fragment

import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v4.app.LoaderManager
import android.support.v4.content.CursorLoader
import android.support.v4.content.Loader
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import com.dhpcs.liquidity.LiquidityApplication
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.provider.LiquidityContract
import org.joda.time.Instant

class GamesFragment : Fragment(),
        AdapterView.OnItemClickListener,
        LoaderManager.LoaderCallbacks<Cursor> {

    companion object {

        interface Listener {

            fun onGameClicked(gameId: Long, zoneId: String, gameName: String?)

        }

        private const val GAMES_LOADER = 0
        private const val REFRESH_INTERVAL: Long = 60000

    }

    private val refreshHandler = Handler()
    private val refreshRunnable = Runnable {
        loaderManager.restartLoader(GAMES_LOADER, null, this)
    }

    private var gamesAdapter: SimpleCursorAdapter? = null

    private var listener: Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loaderManager.initLoader(GAMES_LOADER, null, this)

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

        gamesAdapter!!.setViewBinder { view, cursor, columnIndex ->
            val bound = when (columnIndex) {
                cursor.getColumnIndexOrThrow(LiquidityContract.Games.CREATED) -> {
                    val createdTimeMillis = cursor.getLong(columnIndex)
                    val currentTimeMillis = System.currentTimeMillis()
                    (view as TextView).text = requireActivity().getString(
                            R.string.game_created_format_string,
                            LiquidityApplication.getRelativeTimeSpanString(
                                    requireActivity(),
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
                    (view as TextView).text = requireActivity().getString(
                            R.string.game_expires_format_string,
                            LiquidityApplication.getRelativeTimeSpanString(
                                    requireActivity(),
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

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        return CursorLoader(
                requireActivity(),
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
        val view = inflater.inflate(R.layout.fragment_games, container, false)

        val listViewGames = view.findViewById<ListView>(R.id.listview_games)
        listViewGames.adapter = gamesAdapter
        listViewGames.emptyView = view.findViewById(R.id.textview_empty)
        listViewGames.onItemClickListener = this

        return view
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        listener = context as Listener?
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val cursor = parent.getItemAtPosition(position) as Cursor
        listener?.onGameClicked(
                id,
                cursor.getString(cursor.getColumnIndexOrThrow(
                        LiquidityContract.Games.ZONE_ID
                )),
                cursor.getString(cursor.getColumnIndexOrThrow(
                        LiquidityContract.Games.NAME
                ))
        )
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
        gamesAdapter!!.changeCursor(data)
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL)
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        gamesAdapter!!.changeCursor(null)
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onDestroy()
    }

}
