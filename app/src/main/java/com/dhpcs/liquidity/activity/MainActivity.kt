package com.dhpcs.liquidity.activity

import android.app.Application
import android.os.Bundle
import android.view.MenuItem
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.dhpcs.liquidity.BoardGame
import com.dhpcs.liquidity.GameDatabase
import com.dhpcs.liquidity.R
import com.dhpcs.liquidity.ServerConnection
import io.reactivex.BackpressureStrategy
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_main.*
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

class MainActivity : AppCompatActivity() {

    companion object {

        @Suppress("unused")
        sealed class Optional<out T> {
            data class Some<out T>(val value: T) : Optional<T>()
            object None : Optional<Nothing>()
        }

        class PublisherLiveData<T>
        internal constructor(
                private val boardGame: BoardGame,
                private val publisher: Publisher<T>
        ) : LiveData<T>() {

            internal var subscriber: LiveDataSubscriber? = null

            override fun onActive() {
                subscriber = LiveDataSubscriber()
                publisher.subscribe(subscriber)
                boardGame.onActive(this)
            }

            override fun onInactive() {
                boardGame.onInactive(this)
                subscriber?.subscription?.cancel()
                subscriber = null
            }

            internal inner class LiveDataSubscriber : Subscriber<T> {

                internal var subscription: Subscription? = null

                override fun onSubscribe(subscription: Subscription) {
                    this.subscription = subscription
                    subscription.request(Long.MAX_VALUE)
                }

                override fun onNext(item: T) {
                    postValue(item)
                }

                override fun onError(ex: Throwable) {
                    subscriber = null
                    throw RuntimeException(
                            "PublisherLiveData does not handle errors. Errors from publishers " +
                                    "should be handled upstream and propagated as state.",
                            ex
                    )
                }

                override fun onComplete() {
                    subscriber = null
                }

            }

        }

        class BoardGameModel(application: Application) : AndroidViewModel(application) {

            val serverConnection = ServerConnection(application.filesDir)

            val boardGame = BoardGame(
                    serverConnection,
                    gameDatabase = GameDatabase(application.contentResolver)
            )

            private val selectedIdentityMutableLiveData =
                    MutableLiveData<Optional<BoardGame.Companion.Identity>>()
            val selectedIdentity get() = selectedIdentityMutableLiveData.value!!
            val selectedIdentityLiveData: LiveData<Optional<BoardGame.Companion.Identity>> =
                    selectedIdentityMutableLiveData

            init {
                selectedIdentity(Optional.None)
            }

            fun selectedIdentity(identity: Optional<BoardGame.Companion.Identity>) {
                selectedIdentityMutableLiveData.value = identity
            }

            var pendingCommands: Map<Single<*>, Disposable> = emptyMap()

            fun execCreateGameCommand(command: Single<String>, error: (Throwable) -> String) {
                val disposable = command.subscribe(
                        {
                            boardGame.zoneId = it
                            pendingCommands = pendingCommands - command
                        },
                        {
                            createGameError(Optional.Some(error(it)))
                        }
                )
                pendingCommands = pendingCommands + Pair(command, disposable)
            }

            private val createGameErrorMutableLiveData = MutableLiveData<Optional<String>>()
            val createGameErrorLiveData: LiveData<Optional<String>> = createGameErrorMutableLiveData

            fun createGameError(error: Optional<String>) {
                createGameErrorMutableLiveData.value = error
            }

            fun execCommand(command: Single<Unit>, error: (Throwable) -> String) {
                val disposable = command.subscribe(
                        {
                            pendingCommands = pendingCommands - command
                        },
                        {
                            commandError(Optional.Some(error(it)))
                        }
                )
                pendingCommands = pendingCommands + Pair(command, disposable)
            }

            private val commandErrorsMutableLiveData = MutableLiveData<Optional<String>>()
            val commandErrorsLiveData: LiveData<Optional<String>> = commandErrorsMutableLiveData

            fun commandError(error: Optional<String>) {
                commandErrorsMutableLiveData.value = error
            }

        }

        fun <T> BoardGame.maybeLiveData(maybe: (BoardGame) -> Maybe<T>): PublisherLiveData<T> {
            return PublisherLiveData(
                    this,
                    maybe(this).toFlowable()
            )
        }

        fun <T> BoardGame.observableLiveData(
                observable: (BoardGame) -> Observable<T>
        ): PublisherLiveData<T> {
            return PublisherLiveData(
                    this,
                    observable(this).toFlowable(BackpressureStrategy.BUFFER)
            )
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val model = ViewModelProviders.of(this)
                .get(MainActivity.Companion.BoardGameModel::class.java)

        setSupportActionBar(toolbar)

        val navController = Navigation.findNavController(this, R.id.fragment_nav_host)
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.board_game_graph -> {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                R.id.board_game_fragment ->
                    title = model.boardGame.gameName
                R.id.games_fragment -> {
                    model.boardGame.zoneId = null
                    model.selectedIdentity(Optional.None)
                    model.pendingCommands.forEach {
                        it.value.dispose()
                    }
                    model.pendingCommands = emptyMap()
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                else -> {
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return NavigationUI.onNavDestinationSelected(
                item,
                Navigation.findNavController(this, R.id.fragment_nav_host)
        ) || super.onOptionsItemSelected(item)
    }

}
