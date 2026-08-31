package dev.veschud.model

data class RawVescValues(
    val fetTempC: Double,
    val motorTempC: Double,
    val motorCurrentA: Double,
    val inputCurrentA: Double,
    val duty: Double,
    val electricalRpm: Int,
    val inputVoltageV: Double,
    val ampHours: Double,
    val ampHoursCharged: Double,
    val wattHours: Double,
    val wattHoursCharged: Double,
    val tachometer: Int,
    val tachometerAbs: Int,
    val faultCode: Int
)

data class Telemetry(
    val speedKph: Double = 0.0,
    /** Absolute motor-controller duty cycle, expressed as 0–100 percent. */
    val dutyPercent: Double = 0.0,
    val batteryPercent: Int = 0,
    val tripKm: Double = 0.0,
    val rangeKm: Double? = null,
    val fetTempC: Double = 0.0,
    val motorTempC: Double = 0.0,
    val powerW: Double = 0.0,
    val faultCode: Int = 0,
    val updatedAtMs: Long = 0L
)

/** Stable identity used to keep persistent rider data separate for each physical controller. */
data class BoardIdentity(
    val profileId: String,
    val displayName: String,
    val bleAddress: String,
    val canId: Int? = null
) {
    val storageKey: String = buildString {
        append(profileId).append(':').append(bleAddress)
        canId?.let { append(":can:").append(it) }
    }
}

/** Physical and electrical values used to translate raw VESC values into rider-facing data. */
data class BoardConfig(
    val wheelDiameterM: Double = 0.27, // loaded tyre diameter; calibrate against GPS
    val motorPolePairs: Int = 15,
    val batterySeriesCells: Int = 20,
    val cellEmptyV: Double = 3.0,
    val cellFullV: Double = 4.20,
    val usableBatteryWh: Double = 340.0,
    val packVoltageCorrectionV: Double = 0.0
)

data class VescIdentity(
    val firmwareMajor: Int,
    val firmwareMinor: Int,
    val hardwareName: String
)

data class CurvePoint(val voltsPerCell: Double, val percent: Double)

/** A detectable board plus the calibration used after it has been identified. */
data class BoardProfile(
    val id: String,
    val displayName: String,
    val hardwareTokens: Set<String>,
    val config: BoardConfig,
    val idleCurve: List<CurvePoint>,
    /** Optional fallback used only when this voltage cannot belong to any lower-voltage profile. */
    val unambiguousPackVoltageAboveV: Double? = null
)
