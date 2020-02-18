
package com.example.brainproxy

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import com.github.pwittchen.neurosky.library.NeuroSky
import com.github.pwittchen.neurosky.library.exception.BluetoothNotEnabledException
import com.github.pwittchen.neurosky.library.listener.ExtendedDeviceMessageListener
import com.github.pwittchen.neurosky.library.message.enums.BrainWave
import com.github.pwittchen.neurosky.library.message.enums.Signal
import com.github.pwittchen.neurosky.library.message.enums.State
import com.koushikdutta.async.AsyncServer
import com.koushikdutta.async.http.WebSocket
import com.koushikdutta.async.http.server.AsyncHttpServer
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import kotlinx.android.synthetic.main.activity_main.btn_connect
import kotlinx.android.synthetic.main.activity_main.btn_disconnect
import kotlinx.android.synthetic.main.activity_main.tv_attention
import kotlinx.android.synthetic.main.activity_main.tv_blink
import kotlinx.android.synthetic.main.activity_main.tv_meditation
import kotlinx.android.synthetic.main.activity_main.tv_state
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        const val LOG_TAG = "NeuroSky"
        const val PORT = 8080
    }

    private lateinit var neuroSky: NeuroSky
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var httpServer: AsyncHttpServer
    private var sockets = ArrayList<WebSocket>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Lock CPU
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag").apply {
                acquire()
            }
        }

        // Set up HTTP Server
        httpServer = AsyncHttpServer()
        httpServer.listen(AsyncServer.getDefault(), PORT)

        // Set up websocket
        httpServer.websocket("/", object : AsyncHttpServer.WebSocketRequestCallback {
            override fun onConnected(webSocket: WebSocket?, request: AsyncHttpServerRequest?) {
                if(webSocket == null) {
                    Log.e(LOG_TAG, "Websocket Is Null")
                    return
                }
                // Add the websocket
                sockets.add(webSocket)

                webSocket.setClosedCallback { ex ->
                    if (ex != null) {
                        Log.e(LOG_TAG, "Exception Occured: ", ex)
                    }
                    // Delete the websocket once we are finished
                    sockets.remove(webSocket)
                }
            }
        })

        // Initialize Neurosky
        neuroSky = NeuroSky(object : ExtendedDeviceMessageListener() {
            override fun onStateChange(state: State) {
                handleStateChange(state)
            }

            override fun onSignalChange(signal: Signal) {
                handleSignalChange(signal)
            }

            override fun onBrainWavesChange(brainWaves: Set<BrainWave>) {
                handleBrainWavesChange(brainWaves)
            }
        })

        // Start Listening to buttons
        initButtonListeners()
    }

    private fun initButtonListeners() {
        btn_connect.setOnClickListener {
            try {
                neuroSky.connect()
                neuroSky.startMonitoring()
            } catch (e: BluetoothNotEnabledException) {
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT)
                    .show()
                Log.d(LOG_TAG, e.message!!)
            }
        }

        btn_disconnect.setOnClickListener {
            neuroSky.disconnect()
        }
    }

    private fun handleStateChange(state: State) {
        if (state == State.CONNECTED) {
            neuroSky.startMonitoring()
        }

        tv_state.text = state.toString()
        Log.d(LOG_TAG, state.toString())
    }

    private fun handleSignalChange(signal: Signal) {
        when (signal) {
            Signal.ATTENTION -> tv_attention.text = getFormattedMessage("attention: %d", signal)
            Signal.MEDITATION -> tv_meditation.text = getFormattedMessage("meditation: %d", signal)
            Signal.BLINK -> tv_blink.text = getFormattedMessage("blink: %d", signal)
            else -> Log.d(LOG_TAG, "unhandled signal")
        }
        publishValue(signal.toString(), signal.value)
    }

    private fun getFormattedMessage(
        messageFormat: String,
        signal: Signal
    ): String {
        return String.format(Locale.getDefault(), messageFormat, signal.value)
    }

    private fun publishValue(key: String, value: Int) {
        for (socket in sockets) {
            socket.send("$key: $value")
        }
    }

    private fun handleBrainWavesChange(brainWaves: Set<BrainWave>) {
        for (brainWave in brainWaves) {
            publishValue(brainWave.toString(), brainWave.value)
        }
    }
}
