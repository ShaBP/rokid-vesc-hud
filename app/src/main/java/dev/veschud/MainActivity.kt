package dev.veschud

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import dev.veschud.data.TelemetrySource
import dev.veschud.data.VescBleSource
import dev.veschud.model.Telemetry
import dev.veschud.ui.HudView

/** Minimal lifecycle and runtime-permission host for the custom glasses HUD. */
class MainActivity : Activity(), TelemetrySource.Listener {
    private lateinit var hud: HudView
    private lateinit var source: TelemetrySource
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
    override fun onTelemetry(value: Telemetry) = runOnUiThread { hud.setTelemetry(value) }
    override fun onError(message: String) = runOnUiThread { hud.setState(message.uppercase()) }
}
