package dev.veschud.config

import dev.veschud.model.CurvePoint

/**
 * Low-current cell-voltage to remaining-capacity tables.
 *
 * Points must be ordered from lowest to highest voltage. Values between points are linearly
 * interpolated. These curves are used only while the board is stopped and lightly loaded;
 * while riding, [dev.veschud.domain.HybridBatteryEstimator] counts net watt-hours instead.
 *
 * To add a cell:
 * 1. Obtain a low-current (ideally 0.1C-0.2C), room-temperature discharge curve.
 * 2. Convert discharged capacity to remaining percent: 100 * (1 - discharged / total).
 * 3. Add enough points to reproduce knees in the curve, especially near full and empty.
 * 4. Reference the new list from a profile in [BoardProfiles].
 */
object BatteryCurves {
    /**
     * Samsung INR21700-50S, approximately 0.2C at 23-25 C.
     * Sources: Samsung cell specification and https://www.chongdiantou.com/archives/376212.html
     */
    val SAMSUNG_50S = listOf(
        CurvePoint(3.00, 0.0), CurvePoint(3.30, 2.0), CurvePoint(3.40, 6.0),
        CurvePoint(3.45, 10.0), CurvePoint(3.50, 16.0), CurvePoint(3.55, 23.0),
        CurvePoint(3.60, 31.0), CurvePoint(3.65, 40.0), CurvePoint(3.70, 49.0),
        CurvePoint(3.75, 58.0), CurvePoint(3.80, 66.0), CurvePoint(3.85, 73.0),
        CurvePoint(3.90, 79.0), CurvePoint(3.95, 84.0), CurvePoint(4.00, 88.0),
        CurvePoint(4.06, 92.0), CurvePoint(4.10, 94.0), CurvePoint(4.15, 97.0),
        CurvePoint(4.20, 100.0)
    )

    /**
     * Reliance INR21700-RS50, approximately 0.2C at 25 C.
     * Sources: Reliance A0 specification and
     * https://www.chongdiantou.com/archives/1751532652055.html
     */
    val RELIANCE_RS50 = listOf(
        CurvePoint(3.00, 0.0), CurvePoint(3.25, 1.0), CurvePoint(3.30, 2.0),
        CurvePoint(3.35, 4.0), CurvePoint(3.40, 7.0), CurvePoint(3.45, 11.0),
        CurvePoint(3.50, 17.0), CurvePoint(3.55, 24.0), CurvePoint(3.60, 33.0),
        CurvePoint(3.65, 42.0), CurvePoint(3.70, 51.0), CurvePoint(3.75, 59.0),
        CurvePoint(3.80, 67.0), CurvePoint(3.85, 74.0), CurvePoint(3.90, 80.0),
        CurvePoint(3.95, 85.0), CurvePoint(4.00, 89.0), CurvePoint(4.05, 92.0),
        CurvePoint(4.10, 95.0), CurvePoint(4.15, 98.0), CurvePoint(4.20, 100.0)
    )
}
