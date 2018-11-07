package com.dhpcs.liquidity.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dhpcs.liquidity.BoardGame
import io.reactivex.disposables.Disposable

abstract class BoardGameChildActivity :
        AppCompatActivity(),
        BoardGame.Companion.GameActionListener {

    companion object {

        const val EXTRA_ZONE_ID_HOLDER = "zone_id_holder"
        const val EXTRA_ZONE_ID = "zone_id"

    }

    private var joinRequestToken: BoardGame.Companion.JoinRequestToken? = null
    private var joinStateDisposable: Disposable? = null

    protected var boardGame: BoardGame? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val zoneId = intent
                .getBundleExtra(EXTRA_ZONE_ID_HOLDER)
                .getString(EXTRA_ZONE_ID) as String

        joinRequestToken =
                lastCustomNonConfigurationInstance as BoardGame.Companion.JoinRequestToken?

        if (joinRequestToken == null) joinRequestToken = BoardGame.Companion.JoinRequestToken()

        boardGame = BoardGame.getInstance(zoneId)

        boardGame!!.registerListener(this)
        joinStateDisposable = boardGame!!.joinStateObservable.subscribe {
            if (it != BoardGame.Companion.JoinState.JOINED) finish()
        }
    }

    override fun onStart() {
        super.onStart()
        boardGame!!.requestJoin(joinRequestToken!!)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) boardGame!!.unrequestJoin(joinRequestToken!!)
    }

    override fun onRetainCustomNonConfigurationInstance(): BoardGame.Companion.JoinRequestToken? {
        return joinRequestToken
    }

    override fun onDestroy() {
        super.onDestroy()
        boardGame!!.unregisterListener(this)
        joinStateDisposable?.dispose()
        joinStateDisposable = null
    }

    override fun onCreateGameError(name: String?) {}
    override fun onJoinGameError() {}
    override fun onIdentityRequired() {}

}
