package jm.yardmoney.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jm.yardmoney.core.*

@Composable
internal fun AllocationDonut(shares: List<Int>, amounts: List<Long>) {
    val labels = listOf("Needs", "Wants", "Savings")
    val colors =
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
        )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val ink = MaterialTheme.colorScheme.onSurface.toArgb()
    val density = LocalDensity.current
    val labelSize = with(density) { 14.sp.toPx() }
    val outerLabels = density.fontScale <= 1.3f && shares.none { it in 1..799 }
    val description =
        "Budget allocation. " +
            labels.indices.joinToString(". ") {
                labels[it] +
                    " " +
                    InputFormat.percent(shares[it]) +
                    ", " +
                    Money.format(amounts[it])
            }
    val motion = LocalMotion.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val reveal by
        animateFloatAsState(
            if (appeared) 1f else 0f,
            animationSpec = motion.floatSpec(),
            label = "Allocation donut",
        )
    Box(Modifier.fillMaxWidth().height(236.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }) {
            val stroke = 24.dp.toPx()
            val radius = minOf(size.height / 2 - 38.dp.toPx(), size.width / 2 - 42.dp.toPx())
            val center = Offset(size.width / 2, size.height / 2)
            val pos = center - Offset(radius, radius)
            val dimensions = Size(radius * 2, radius * 2)
            drawArc(track, 0f, 360f, false, pos, dimensions, style = Stroke(stroke))
            val paint =
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = ink
                    textSize = labelSize
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface =
                        android.graphics.Typeface.create(
                            "sans-serif-medium",
                            android.graphics.Typeface.NORMAL,
                        )
                }
            var start = -90f
            shares.forEachIndexed { i, share ->
                val sweep = share / 10000f * 360f
                if (sweep > 0) {
                    val gap = minOf(3f, sweep * .12f)
                    drawArc(
                        colors[i],
                        start + gap / 2,
                        (sweep - gap) * reveal,
                        false,
                        pos,
                        dimensions,
                        style = Stroke(stroke),
                    )
                    if (outerLabels) {
                        val angle = Math.toRadians((start + sweep / 2).toDouble())
                        val r = radius + 27.dp.toPx()
                        drawContext.canvas.nativeCanvas.drawText(
                            InputFormat.percent(share),
                            center.x + kotlin.math.cos(angle).toFloat() * r,
                            center.y + kotlin.math.sin(angle).toFloat() * r -
                                (paint.ascent() + paint.descent()) / 2,
                            paint,
                        )
                    }
                }
                start += sweep
            }
        }
        Text(
            "Budget\nplan",
            style = MaterialTheme.typography.labelLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
    labels.forEachIndexed { i, label ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(10.dp)
                    .background(colors[i], androidx.compose.foundation.shape.CircleShape)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (!outerLabels || shares[i] == 0)
                        label + " · " + InputFormat.percent(shares[i])
                    else label,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    Money.format(amounts[i]).replace(",", ",\u200B"),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
    Text(
        "Budget targets from received income. Recorded spending is shown separately below.",
        style = MaterialTheme.typography.bodySmall,
    )
}
