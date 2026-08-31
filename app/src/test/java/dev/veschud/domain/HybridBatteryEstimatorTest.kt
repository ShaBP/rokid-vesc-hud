package dev.veschud.domain

import dev.veschud.config.BoardProfiles
import dev.veschud.model.RawVescValues
import org.junit.Assert.assertEquals
import org.junit.Test

class HybridBatteryEstimatorTest {
    @Test fun mapperConvertsSignedVescDutyRatioToAbsolutePercent() {
        val raw = values(voltage = 81.2, wh = 0.0).copy(duty = -0.427)
        assertEquals(42.7, TelemetryMapper(BoardProfiles.ADV2).map(raw, 0).dutyPercent, 0.001)
    }

    @Test fun adv2Samsung50sMapsObservedVoltageToFloatControlReading() {
        val estimator = HybridBatteryEstimator(BoardProfiles.ADV2)
        assertEquals(92, estimator.estimate(values(voltage = 81.2, wh = 0.0), 0.0, 0))
    }

    @Test fun atomRelianceRs50UsesCellSpecificLowCurrentCurve() {
        val estimator = HybridBatteryEstimator(BoardProfiles.ATOM)
        assertEquals(92, estimator.estimate(values(voltage = 89.1, wh = 0.0), 0.0, 0))
        assertEquals(51, HybridBatteryEstimator(BoardProfiles.ATOM)
            .estimate(values(voltage = 81.4, wh = 0.0), 0.0, 0))
    }

    @Test fun adv2SubtractsNetWhFromIdleAnchorWhileMoving() {
        val estimator = HybridBatteryEstimator(BoardProfiles.ADV2)
        estimator.estimate(values(voltage = 84.0, wh = 10.0), speedKph = 0.0, nowMs = 0)
        assertEquals(100, estimator.estimate(values(voltage = 84.0, wh = 10.0), 0.0, 2_000))
        assertEquals(90, estimator.estimate(values(voltage = 76.0, wh = 82.0), 20.0, 3_000))
    }

    @Test fun regenIsIncludedInNetEnergy() {
        val estimator = HybridBatteryEstimator(BoardProfiles.ATOM)
        estimator.estimate(values(voltage = 92.4, wh = 20.0, whCharged = 0.0), 0.0, 0)
        estimator.estimate(values(voltage = 92.4, wh = 20.0, whCharged = 0.0), 0.0, 2_000)
        // 40.7 Wh consumed and 4.07 Wh regenerated = 9% net usage of a 407 Wh pack.
        assertEquals(91, estimator.estimate(values(80.0, 60.7, 4.07), 15.0, 3_000))
    }

    private fun values(voltage: Double, wh: Double, whCharged: Double = 0.0) = RawVescValues(
        0.0, 0.0, 0.0, 0.0, 0.0, 0, voltage, 0.0, 0.0,
        wh, whCharged, 0, 0, 0
    )
}
