package dev.veschud.domain

import dev.veschud.config.BoardProfiles
import dev.veschud.model.*
import kotlin.math.PI
import kotlin.math.roundToInt

class TelemetryMapper(initialProfile: BoardProfile = BoardProfiles.ADV2) {
    private var profile = initialProfile
    private var startTacho: Int? = null
    private var startNetWh: Double? = null
    private var smoothedWhPerKm: Double? = null
    private val batteryEstimator = HybridBatteryEstimator(initialProfile)

    fun setProfile(value: BoardProfile) {
        if (profile == value) return
        profile = value
        batteryEstimator.setProfile(value)
    }

    fun map(v: RawVescValues, nowMs: Long = System.currentTimeMillis()): Telemetry {
        if (startTacho == null) startTacho = v.tachometer
        if (startNetWh == null) startNetWh = v.wattHours - v.wattHoursCharged
        val config = profile.config
        val motorRevsPerMinute = v.electricalRpm.toDouble() / config.motorPolePairs
        val speed = motorRevsPerMinute * PI * config.wheelDiameterM * 60.0 / 1000.0
        val trip = kotlin.math.abs(v.tachometer - startTacho!!) /
            (config.motorPolePairs * 6.0) * PI * config.wheelDiameterM / 1000.0
        val usedWh = (v.wattHours - v.wattHoursCharged - startNetWh!!).coerceAtLeast(0.0)
        if (trip >= 0.5 && usedWh > 0.0) {
            val sample = usedWh / trip
            smoothedWhPerKm = smoothedWhPerKm?.let { it * .85 + sample * .15 } ?: sample
        }
        val battery = batteryEstimator.estimate(v, kotlin.math.abs(speed), nowMs)
        val remainingWh = config.usableBatteryWh * battery / 100.0
        val range = smoothedWhPerKm?.takeIf { it in 3.0..80.0 }?.let { remainingWh / it }
        val dutyPercent = (kotlin.math.abs(v.duty) * 100.0).coerceIn(0.0, 100.0)
        return Telemetry(kotlin.math.abs(speed), dutyPercent, battery, trip, range, v.fetTempC,
            v.motorTempC, v.inputVoltageV * v.inputCurrentA, v.faultCode, nowMs)
    }

    fun resetTrip() { startTacho = null; startNetWh = null; smoothedWhPerKm = null }
}

/**
 * Float Control-style hybrid SOC:
 * 1. While stationary, anchor SOC to a low-current cell discharge curve.
 * 2. While moving, subtract net controller Wh from that anchor, avoiding voltage-sag jumps.
 * 3. Re-anchor after the board has been stationary and lightly loaded for 1.5 seconds.
 */
class HybridBatteryEstimator(initialProfile: BoardProfile) {
    private var profile = initialProfile
    private var anchorPercent: Double? = null
    private var anchorNetWh: Double? = null
    private var idleSinceMs: Long? = null
    private var filteredIdlePackV: Double? = null

    fun setProfile(value: BoardProfile) {
        profile = value
        anchorPercent = null
        anchorNetWh = null
        idleSinceMs = null
        filteredIdlePackV = null
    }

    fun estimate(v: RawVescValues, speedKph: Double, nowMs: Long): Int {
        val config = profile.config
        val netWh = v.wattHours - v.wattHoursCharged
        val correctedPackV = v.inputVoltageV + config.packVoltageCorrectionV
        val lightlyLoadedAndStopped = speedKph < 0.8 && kotlin.math.abs(v.inputCurrentA) < 2.0

        if (lightlyLoadedAndStopped) {
            if (idleSinceMs == null) idleSinceMs = nowMs
            filteredIdlePackV = filteredIdlePackV?.let { it * 0.85 + correctedPackV * 0.15 } ?: correctedPackV
            if (nowMs - idleSinceMs!! >= 1_500) {
                anchorPercent = interpolateCurve(filteredIdlePackV!! / config.batterySeriesCells)
                anchorNetWh = netWh
            }
        } else {
            idleSinceMs = null
            filteredIdlePackV = null
        }

        if (anchorPercent == null || anchorNetWh == null) {
            anchorPercent = interpolateCurve(correctedPackV / config.batterySeriesCells)
            anchorNetWh = netWh
        }

        val consumedSinceAnchorWh = netWh - anchorNetWh!!
        val result = anchorPercent!! - consumedSinceAnchorWh / config.usableBatteryWh * 100.0
        return result.roundToInt().coerceIn(0, 100)
    }

    private fun interpolateCurve(voltsPerCell: Double): Double {
        val curve = profile.idleCurve
        if (voltsPerCell <= curve.first().voltsPerCell) return curve.first().percent
        if (voltsPerCell >= curve.last().voltsPerCell) return curve.last().percent
        val upperIndex = curve.indexOfFirst { voltsPerCell <= it.voltsPerCell }
        val low = curve[upperIndex - 1]
        val high = curve[upperIndex]
        val fraction = (voltsPerCell - low.voltsPerCell) / (high.voltsPerCell - low.voltsPerCell)
        return low.percent + fraction * (high.percent - low.percent)
    }
}
