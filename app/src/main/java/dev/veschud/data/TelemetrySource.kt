package dev.veschud.data

import dev.veschud.model.Telemetry

/**
 * Transport boundary consumed by the activity.
 *
 * Keeping BLE behind this interface lets a future phone companion provide the same telemetry
 * without coupling the HUD or domain calculations to a particular connection method.
 */
interface TelemetrySource {
    fun start(listener: Listener)
    fun stop()
    interface Listener {
        fun onState(state: String)
        fun onTelemetry(value: Telemetry)
        fun onError(message: String)
    }
}
