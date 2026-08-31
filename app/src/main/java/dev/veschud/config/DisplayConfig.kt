package dev.veschud.config

/** Units used by the rider-facing HUD. Domain calculations always remain metric. */
enum class MeasurementSystem { METRIC, IMPERIAL }

/**
 * User-editable display settings.
 *
 * Change [measurementSystem] to [MeasurementSystem.IMPERIAL] to display mph, miles, and °F.
 */
object DisplayConfig {
    val measurementSystem: MeasurementSystem = MeasurementSystem.METRIC
}
