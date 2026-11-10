package org.campus.autoconnect

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * A functional status symbol, not a decorative image.
 * The outline and glyph change only when connection state changes.
 */
class ConnectionMark(context: Context) : View(context) {
    enum class Kind { IDLE, ONLINE, ATTENTION, WORKING }
    var kind: Kind = Kind.IDLE
        set(value) { field = value; contentDescription = value.name; invalidate() }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = RejoinUi.dp(context, 88)
        setMeasuredDimension(resolveSize(s, widthMeasureSpec), resolveSize(s, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val min = minOf(width, height).toFloat()
        val scale = min / 88f
        canvas.save()
        canvas.translate((width - min) / 2f, (height - min) / 2f)
        canvas.scale(scale, scale)
        val active = when (kind) {
            Kind.ONLINE -> RejoinUi.jade
            Kind.ATTENTION -> RejoinUi.amber
            Kind.WORKING -> RejoinUi.mint
            Kind.IDLE -> RejoinUi.muted
        }
        fill.color = RejoinUi.raised
        canvas.drawCircle(44f, 44f, 39f, fill)

        stroke.color = RejoinUi.line
        stroke.strokeWidth = 1.2f
        canvas.drawCircle(44f, 44f, 38f, stroke)

        stroke.color = active
        stroke.strokeWidth = 3f
        canvas.drawArc(6f, 6f, 82f, 82f, 220f, 96f, false, stroke)
        canvas.drawArc(6f, 6f, 82f, 82f, 24f, 50f, false, stroke)

        stroke.strokeWidth = 5.4f
        val glyph = Path()
        when (kind) {
            Kind.ONLINE -> {
                glyph.moveTo(27f, 44f)
                glyph.lineTo(39f, 55f)
                glyph.lineTo(61f, 32f)
                canvas.drawPath(glyph, stroke)
            }
            Kind.ATTENTION -> {
                canvas.drawLine(44f, 28f, 44f, 49f, stroke)
                fill.color = active
                canvas.drawCircle(44f, 60f, 3.3f, fill)
            }
            Kind.WORKING -> {
                stroke.strokeWidth = 3.8f
                canvas.drawArc(28f, 28f, 60f, 60f, -70f, 250f, false, stroke)
            }
            Kind.IDLE -> {
                stroke.strokeWidth = 3.5f
                canvas.drawArc(30f, 31f, 58f, 59f, 225f, 90f, false, stroke)
                fill.color = active
                canvas.drawCircle(44f, 52f, 3.3f, fill)
            }
        }
        canvas.restore()
    }
}
