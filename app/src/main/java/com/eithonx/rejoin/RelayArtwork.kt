package com.eithonx.rejoin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

internal enum class RelayScene { CONNECTED, WAITING, LOGIN }

private val ink = Color(0xFF202C3A)
private val blue = Color(0xFFC5DCF2)
private val pale = Color(0xFFE9F0F1)
private val peach = Color(0xFFF3B686)
private val guide = Color(0xFF526A80)
private val shade = Color(0xFF819AB4)
private val track = Color(0xFF7D95AC)

@Composable
internal fun RelayArtwork(scene: RelayScene, modifier: Modifier = Modifier) {
    val joined by animateFloatAsState(
        targetValue = if (scene == RelayScene.WAITING) 0f else 1f,
        animationSpec = tween(durationMillis = 320),
        label = "Wi-Fi route"
    )
    Canvas(modifier) {
        val factor = minOf(size.width / 184f, size.height / 124f)
        translate((size.width - 184f * factor) / 2f, (size.height - 124f * factor) / 2f) {
            scale(factor, factor, Offset.Zero) {
                drawField()
                drawWire(joined)
                drawRouter()
                drawHandset(scene)
            }
        }
    }
}

private fun DrawScope.drawField() {
    for (column in 0..11) {
        for (row in 0..7) {
            drawCircle(guide.copy(alpha = if ((column + row) % 4 == 0) 0.57f else 0.28f),
                radius = 0.85f, center = Offset(12f + column * 14f, 12f + row * 14f))
        }
    }
    val horizon = Path().apply {
        moveTo(12f, 64f)
        cubicTo(46f, 25f, 97f, 112f, 172f, 58f)
    }
    drawPath(horizon, guide.copy(alpha = 0.30f), style = Stroke(1f))
    sparkle(89f, 25f, 4f, blue)
    sparkle(165f, 43f, 5.5f, peach)
    sparkle(21f, 35f, 3f, pale)
    drawCircle(guide, 2f, Offset(120f, 106f))
}

private fun DrawScope.drawWire(joined: Float) {
    val wire = Path().apply {
        moveTo(51f, 95f)
        cubicTo(77f, 109f, 102f, 106f, 99f, 86f)
        cubicTo(96f, 67f, 113f, 67f, 135f, 80f)
    }
    drawPath(wire, ink, style = Stroke(width = 8f, cap = StrokeCap.Round))
    drawPath(wire, track, style = Stroke(width = 3.4f, cap = StrokeCap.Round))
    val measure = PathMeasure().apply { setPath(wire, false) }
    val length = measure.length
    val middle = length / 2f
    val opening = (1f - joined) * 8f
    val route = Path()
    measure.getSegment(0f, middle - opening, route)
    measure.getSegment(middle + opening, length, route)
    if (opening > 0.5f) {
        val gap = Path()
        measure.getSegment(middle - opening, middle + opening, gap)
        drawPath(gap, ink, style = Stroke(width = 4.8f, cap = StrokeCap.Round))
    }
    drawPath(route, peach, style = Stroke(width = 3.4f, cap = StrokeCap.Round))
    drawCircle(pale, 3.1f, center = measure.getPosition(middle - opening))
    if (opening > 0.5f) drawCircle(pale, 3.1f, center = measure.getPosition(middle + opening))
}

private fun DrawScope.drawRouter() {
    rotate(-9f, pivot = Offset(45f, 78f)) {
        drawRoundRect(ink, Offset(17f, 70f), Size(65f, 29f), CornerRadius(9f))
        drawRoundRect(shade, Offset(15f, 66f), Size(64f, 28f), CornerRadius(8f))
        drawRoundRect(blue, Offset(15f, 62f), Size(64f, 23f), CornerRadius(8f))
        drawRoundRect(pale, Offset(20f, 65f), Size(54f, 1.4f), CornerRadius(0.7f))
        drawLine(ink, Offset(29f, 63f), Offset(29f, 49f), strokeWidth = 3.6f, cap = StrokeCap.Round)
        drawLine(ink, Offset(66f, 63f), Offset(66f, 49f), strokeWidth = 3.6f, cap = StrokeCap.Round)
        drawLine(blue, Offset(29f, 60f), Offset(29f, 47f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        drawLine(blue, Offset(66f, 60f), Offset(66f, 47f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        drawCircle(ink, 1.9f, Offset(59f, 75f))
        drawCircle(ink, 1.9f, Offset(66f, 75f))
        drawCircle(peach, 2.3f, Offset(38f, 75f))
    }
}

private fun DrawScope.drawHandset(scene: RelayScene) {
    rotate(10f, pivot = Offset(135f, 56f)) {
        drawRoundRect(ink, Offset(120f, 27f), Size(38f, 64f), CornerRadius(9f))
        drawRoundRect(shade, Offset(118f, 24f), Size(38f, 64f), CornerRadius(9f))
        drawRoundRect(pale, Offset(117f, 22f), Size(36f, 62f), CornerRadius(9f))
        drawRoundRect(blue, Offset(121f, 29f), Size(28f, 44f), CornerRadius(5f))
        drawLine(ink, Offset(131f, 25.5f), Offset(139f, 25.5f), strokeWidth = 1.4f, cap = StrokeCap.Round)
        drawLine(ink, Offset(132f, 78f), Offset(138f, 78f), strokeWidth = 1.4f, cap = StrokeCap.Round)
        if (scene == RelayScene.LOGIN) {
            drawRoundRect(ink, Offset(128f, 47f), Size(14f, 11f), CornerRadius(2.6f))
            drawArc(ink, 180f, 180f, false, Offset(131f, 40f), Size(8f, 12f), style = Stroke(1.8f))
            drawCircle(pale, 1.3f, Offset(135f, 52f))
        } else {
            drawCircle(ink, 8.8f, Offset(135f, 51f))
            if (scene == RelayScene.CONNECTED) {
                val check = Path().apply {
                    moveTo(131f, 51f)
                    lineTo(134f, 54f)
                    lineTo(139f, 48f)
                }
                drawPath(check, pale, style = Stroke(width = 1.8f, cap = StrokeCap.Round))
            } else {
                drawLine(pale, Offset(132f, 51f), Offset(138f, 51f),
                    strokeWidth = 1.7f, cap = StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.sparkle(x: Float, y: Float, radius: Float, color: Color) {
    drawPath(Path().apply {
        moveTo(x, y - radius)
        lineTo(x + radius * 0.32f, y - radius * 0.32f)
        lineTo(x + radius, y)
        lineTo(x + radius * 0.32f, y + radius * 0.32f)
        lineTo(x, y + radius)
        lineTo(x - radius * 0.32f, y + radius * 0.32f)
        lineTo(x - radius, y)
        lineTo(x - radius * 0.32f, y - radius * 0.32f)
        close()
    }, color)
}
