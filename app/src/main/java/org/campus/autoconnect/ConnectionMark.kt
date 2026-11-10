package org.campus.autoconnect

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** A small signal diagram with a distinct glyph for each connection state. */
class ConnectionMark(context: Context) : View(context) {
    enum class Kind { IDLE, ONLINE, ATTENTION, ERROR, WORKING }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    var kind: Kind = Kind.IDLE
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val scale = minOf(width / 82f, height / 74f)
        canvas.save()
        canvas.translate((width - 82f * scale) / 2, (height - 74f * scale) / 2)
        canvas.scale(scale, scale)

        paint.color = RejoinUi.raised
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(0f, 0f, 82f, 74f, 18f, 18f, paint)
        paint.color = RejoinUi.line
        for (y in 11..66 step 8) {
            for (x in 13..70 step 8) canvas.drawCircle(x.toFloat(), y.toFloat(), .65f, paint)
        }

        val signal = when (kind) {
            Kind.ONLINE -> RejoinUi.success
            Kind.WORKING -> RejoinUi.accent
            Kind.ERROR -> RejoinUi.rose
            Kind.ATTENTION -> RejoinUi.amber
            Kind.IDLE -> RejoinUi.muted
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.6f
        paint.color = signal
        for (radius in 14..32 step 9) {
            canvas.drawArc(39f - radius, 49f - radius, 39f + radius, 49f + radius,
                228f, 84f, false, paint)
        }
        paint.style = Paint.Style.FILL
        canvas.drawCircle(39f, 49f, 3f, paint)

        if (kind != Kind.IDLE) {
            paint.color = signal
            canvas.drawCircle(62f, 54f, 12f, paint)
            paint.color = RejoinUi.canvas
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            when (kind) {
                Kind.ONLINE -> {
                    path.reset()
                    path.moveTo(57.5f, 54f)
                    path.lineTo(61f, 57.5f)
                    path.lineTo(67f, 51f)
                    canvas.drawPath(path, paint)
                }
                Kind.ATTENTION -> {
                    canvas.drawLine(62f, 49f, 62f, 54.5f, paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(62f, 58.5f, 1.1f, paint)
                }
                Kind.ERROR -> {
                    canvas.drawLine(58.5f, 50.5f, 65.5f, 57.5f, paint)
                    canvas.drawLine(65.5f, 50.5f, 58.5f, 57.5f, paint)
                }
                Kind.WORKING -> canvas.drawArc(57f, 49f, 67f, 59f, -70f, 260f, false, paint)
                Kind.IDLE -> Unit
            }
        }
        canvas.restore()
    }
}
