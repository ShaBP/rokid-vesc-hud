package dev.veschud.ui

import android.content.Context
import android.graphics.*
import android.view.View
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
    fun setTelemetry(value: Telemetry) { telemetry = value; invalidate() }
    fun setState(value: String) { state = value; invalidate() }
    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        val sx = width / 480f; val sy = height / 640f
        c.save(); c.scale(sx, sy)
        text(c, String.format(Locale.US, "%.0f", telemetry.speedKph), 240f, 225f, 126f, Paint.Align.CENTER, green)
        text(c, "km/h", 240f, 270f, 28f, Paint.Align.CENTER, dim)
        line(c, 42f, 300f, 438f, 300f)
        text(c, "BAT", 48f, 354f, 24f, Paint.Align.LEFT, dim)
        text(c, "${telemetry.batteryPercent}%", 432f, 354f, 42f, Paint.Align.RIGHT, green)
        text(c, "TRIP", 48f, 416f, 24f, Paint.Align.LEFT, dim)
        text(c, String.format(Locale.US, "%.1f km", telemetry.tripKm), 432f, 416f, 36f, Paint.Align.RIGHT, green)
        text(c, "RANGE", 48f, 478f, 24f, Paint.Align.LEFT, dim)
        val range = telemetry.rangeKm?.let { String.format(Locale.US, "%.0f km", it) } ?: "--"
        text(c, range, 432f, 478f, 36f, Paint.Align.RIGHT, green)
        text(c, String.format(Locale.US, "FET %.0f°  MOT %.0f°   %.0f W", telemetry.fetTempC, telemetry.motorTempC, telemetry.powerW), 240f, 540f, 20f, Paint.Align.CENTER, dim)
        text(c, if (telemetry.faultCode == 0) state else "FAULT ${telemetry.faultCode}", 240f, 600f, 18f, Paint.Align.CENTER, if (telemetry.faultCode == 0) dim else green)
        c.restore()
    }
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, align: Paint.Align, color: Int) {
        paint.textSize = size * textScale; paint.textAlign = align; paint.color = color; paint.style = Paint.Style.FILL; c.drawText(s, x, y, paint)
    }
    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) { paint.color = dim; paint.strokeWidth = 2f; c.drawLine(x1,y1,x2,y2,paint) }
}
