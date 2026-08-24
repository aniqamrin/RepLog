package com.replog.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

data class BarEntry(
    val label: String?,
    val value: Float,
    val highlight: Boolean = false,
    val caption: String? = null
)

@Composable
fun LineChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(800),
        label = "line"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    if (values.size < 2) {
        Box(modifier.height(120.dp).fillMaxWidth())
        return
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val w = size.width
            val h = size.height
            val padV = h * 0.12f
            val minV = values.min()
            val maxV = values.max()
            val range = max((maxV - minV).toFloat(), 0.001f)

            fun x(i: Int) = if (values.size == 1) w / 2 else w * i / (values.size - 1)
            fun y(v: Double): Float =
                h - padV - ((v - minV).toFloat() / range) * (h - 2 * padV)

            drawLine(
                color = trackColor,
                start = Offset(0f, y(maxV)),
                end = Offset(w, y(maxV)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
            )
            drawLine(
                color = trackColor,
                start = Offset(0f, y(minV)),
                end = Offset(w, y(minV)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
            )

            val visibleCount = max(2, (values.size * progress).toInt().coerceAtLeast(2))
            val pts = (0 until visibleCount).map { i ->
                Offset(x(i), y(values[i]))
            }

            if (pts.size >= 2) {
                val linePath = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) {
                        val prev = pts[i - 1]
                        val cur = pts[i]
                        val midX = (prev.x + cur.x) / 2
                        cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                    }
                }
                val fillPath = Path().apply {
                    addPath(linePath)
                    lineTo(pts.last().x, h)
                    lineTo(pts.first().x, h)
                    close()
                }
                drawPath(
                    fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                        startY = 0f,
                        endY = h
                    )
                )
                drawPath(
                    linePath,
                    color = lineColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = pts.last())
                drawCircle(
                    color = Color.White,
                    radius = 1.8.dp.toPx(),
                    center = pts.last()
                )
            }
        }
        MinMaxRow(min = minOf(values.min(), values.max()), max = values.max())
    }
}

@Composable
private fun MinMaxRow(min: Double, max: Double) {
    val fmt = com.replog.app.util.Format::oneDecimal
    Row(Modifier.fillMaxWidth()) {
        Text(
            "low ${fmt(min)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            "high ${fmt(max)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BarChart(
    entries: List<BarEntry>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    dimColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(700),
        label = "bar"
    )

    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            if (entries.isEmpty()) return@Canvas
            val n = entries.size
            val slot = size.width / n
            val barW = min(slot * 0.55f, 26.dp.toPx())
            val maxV = max(entries.maxOf { it.value }, 1f)
            entries.forEachIndexed { i, e ->
                val bh = (e.value / maxV) * (size.height * progress)
                val left = slot * i + (slot - barW) / 2
                drawRoundRect(
                    color = if (e.highlight) barColor else dimColor,
                    topLeft = Offset(left, size.height - bh),
                    size = androidx.compose.ui.geometry.Size(barW, bh),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
            }
        }
        if (entries.any { !it.label.isNullOrBlank() }) {
            Row(Modifier.fillMaxWidth()) {
                entries.forEach { e ->
                    Box(Modifier.weight(1f)) {
                        Text(
                            e.label.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
