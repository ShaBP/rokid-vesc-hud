package dev.veschud.ui

import android.content.Context
import android.graphics.*
import android.view.View
import dev.veschud.config.DisplayConfig
import dev.veschud.config.MeasurementSystem
import dev.veschud.model.Telemetry
import java.util.Locale

/**
 * Dependency-free 480x640 reference canvas for Rokid's monochrome green MicroLED display.
 * Coordinates scale to the actual view while retaining the original portrait composition.
 */
class HudView(context: Context) : View(context) {
    // An additional 25% reduction from the previous 0.75 scale.
    private val textScale = 0.5625f
    private val green = Color.rgb(0, 255, 90)
    private val dim = Color.rgb(0, 135, 48)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.BOLD) }
    private var telemetry = Telemetry()
    private var state = "STARTING"
    private var rideMaxKph = 0.0
    private var boardMaxKph = 0.0
    fun setTelemetry(value: Telemetry) { telemetry = value; invalidate() }
    fun setState(value: String) { state = value; invalidate() }
    fun setSpeedRecords(rideKph: Double, boardKph: Double) {
        rideMaxKph = rideKph
        boardMaxKph = boardKph
        invalidate()
    }
    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        val sx = width / 480f; val sy = height / 640f
        c.save(); c.scale(sx, sy)
        val imperial = DisplayConfig.measurementSystem == MeasurementSystem.IMPERIAL
        val speed = convertDistance(telemetry.speedKph, imperial)
        val rideMax = convertDistance(rideMaxKph, imperial)
        val boardMax = convertDistance(boardMaxKph, imperial)
        val trip = convertDistance(telemetry.tripKm, imperial)
        val distanceUnit = if (imperial) "mi" else "km"
        val speedUnit = if (imperial) "mph" else "km/h"

        text(c, "DUTY", 70f, 157f, 18f, Paint.Align.CENTER, dim)
        // Intentionally identical to the two maximum-speed number sizes.
        text(c, String.format(Locale.US, "%.0f%%", telemetry.dutyPercent), 70f, 190f, 42f, Paint.Align.CENTER, green)
        text(c, String.format(Locale.US, "%.0f", speed), 224f, 218f, 126f, Paint.Align.CENTER, green)
        // Kept lower than the speed glyphs so the unit cannot overlap two- or three-digit values.
        text(c, speedUnit, 224f, 282f, 28f, Paint.Align.CENTER, dim)
        text(c, "RIDE MAX", 382f, 157f, 18f, Paint.Align.CENTER, dim)
        text(c, recordText(rideMax), 382f, 190f, 42f, Paint.Align.CENTER, green)
        text(c, "BOARD MAX", 382f, 225f, 18f, Paint.Align.CENTER, dim)
        text(c, recordText(boardMax), 382f, 258f, 42f, Paint.Align.CENTER, green)

        line(c, 42f, 298f, 438f, 298f)
        text(c, "BAT", 48f, 342f, 24f, Paint.Align.LEFT, dim)
        text(c, "${telemetry.batteryPercent}%", 432f, 342f, 42f, Paint.Align.RIGHT, green)
        text(c, "TRIP", 48f, 394f, 24f, Paint.Align.LEFT, dim)
        text(c, String.format(Locale.US, "%.1f %s", trip, distanceUnit), 432f, 394f, 36f, Paint.Align.RIGHT, green)
        text(c, "RANGE", 48f, 446f, 24f, Paint.Align.LEFT, dim)
        val range = telemetry.rangeKm?.let {
            String.format(Locale.US, "%.0f %s", convertDistance(it, imperial), distanceUnit)
        } ?: "--"
        text(c, range, 432f, 446f, 36f, Paint.Align.RIGHT, green)

        val fetTemp = convertTemperature(telemetry.fetTempC, imperial)
        val motorTemp = convertTemperature(telemetry.motorTempC, imperial)
        val tempUnit = if (imperial) "F" else "C"
        text(c, String.format(Locale.US, "FET %.0f°%s  MOT %.0f°%s   %.0f W", fetTemp, tempUnit, motorTemp, tempUnit, telemetry.powerW), 240f, 488f, 20f, Paint.Align.CENTER, dim)
        text(c, if (telemetry.faultCode == 0) state else "FAULT ${telemetry.faultCode}", 240f, 522f, 18f, Paint.Align.CENTER, if (telemetry.faultCode == 0) dim else green)
        c.restore()
    }
    private fun recordText(value: Double) = if (value > 0.0) String.format(Locale.US, "%.0f", value) else "--"
    private fun convertDistance(metric: Double, imperial: Boolean) = if (imperial) metric * KM_TO_MILES else metric
    private fun convertTemperature(celsius: Double, imperial: Boolean) = if (imperial) celsius * 9.0 / 5.0 + 32.0 else celsius
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, align: Paint.Align, color: Int) {
        paint.textSize = size * textScale; paint.textAlign = align; paint.color = color; paint.style = Paint.Style.FILL; c.drawText(s, x, y, paint)
    }
    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) { paint.color = dim; paint.strokeWidth = 2f; c.drawLine(x1,y1,x2,y2,paint) }

    companion object { private const val KM_TO_MILES = 0.621371 }
}
