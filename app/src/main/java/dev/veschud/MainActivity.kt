package dev.veschud

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import dev.veschud.data.TelemetrySource
import dev.veschud.data.VescBleSource
import dev.veschud.model.BoardIdentity
import dev.veschud.model.Telemetry
import dev.veschud.ui.HudView

/** Minimal lifecycle and runtime-permission host for the custom glasses HUD. */
class MainActivity : Activity(), TelemetrySource.Listener {
    private lateinit var hud: HudView
    private lateinit var source: TelemetrySource
    private var activeBoard: BoardIdentity? = null
    private var rideMaxKph = 0.0
    private var boardMaxKph = 0.0
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hud = HudView(this)
        setContentView(hud)
        source = VescBleSource(this)
        requestBleOrStart()
    }
    private fun requestBleOrStart() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) source.start(this) else requestPermissions(missing.toTypedArray(), 7)
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        if (code == 7 && grants.all { it == PackageManager.PERMISSION_GRANTED }) source.start(this)
        else hud.setState("BLE PERMISSION NEEDED")
    }
    override fun onDestroy() { source.stop(); super.onDestroy() }
    override fun onState(state: String) = runOnUiThread { hud.setState(state) }
    override fun onBoardIdentified(identity: BoardIdentity) = runOnUiThread {
        if (activeBoard?.storageKey != identity.storageKey) {
            activeBoard = identity
            rideMaxKph = 0.0
            boardMaxKph = getPreferences(MODE_PRIVATE).getFloat(maxKey(identity), 0f).toDouble()
            hud.setSpeedRecords(rideMaxKph, boardMaxKph)
        }
    }
    override fun onTelemetry(value: Telemetry) = runOnUiThread {
        if (value.speedKph > 20.0 && value.speedKph > rideMaxKph) {
            rideMaxKph = value.speedKph
            if (rideMaxKph > boardMaxKph) {
                boardMaxKph = rideMaxKph
                activeBoard?.let { identity ->
                    getPreferences(MODE_PRIVATE).edit()
                        .putFloat(maxKey(identity), boardMaxKph.toFloat())
                        .apply()
                }
            }
            hud.setSpeedRecords(rideMaxKph, boardMaxKph)
        }
        hud.setTelemetry(value)
    }
    override fun onError(message: String) = runOnUiThread { hud.setState(message.uppercase()) }

    private fun maxKey(identity: BoardIdentity) = "board_max_kph:${identity.storageKey}"
}
