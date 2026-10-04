package jm.yardmoney.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import jm.yardmoney.core.Money

// Native Compose Text retains TalkBack/reflow; Shape/Canvas provide paper edges and dashed rules.
// Android's documented drawing APIs cover this receipt with zero added AARs (min SDK 26).
// https://developer.android.com/develop/ui/compose/graphics/draw/overview
// Compose is actively maintained (latest stable 1.12.1, 2026-09-09); this app keeps its
// locked Apache-2.0 Compose 1.9.4 / Kotlin 2.3.20 toolchain. Official release notes track
// open layout/input issues; this rendering uses the established Text/Canvas/Shape APIs.
// https://developer.android.com/jetpack/androidx/releases/compose-ui
// PdfBox-Android 2.0.27.0 (2023-01-02; last commit 2023-12-29; 113 open issues checked
// 2026-10-03) is Apache-2.0, Java/API19, callable from Kotlin 2.3 but has no Compose UI.
// Its 3,254,019-byte AAR plus BouncyCastle dependencies buy PDF parsing we don't need.
// https://github.com/TomRoush/PdfBox-Android · /releases · library/build.gradle
// If exporting is added later, Android PdfDocument/PrintDocumentAdapter (API19) and
// FileProvider are documented platform options, without broad storage permission.
// https://developer.android.com/training/printing/custom-docs
// https://developer.android.com/training/secure-file-sharing/share-file
@Composable
internal fun ShopReceipt(model: ShopReceiptModel, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        shape = ReceiptPaper,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "YARDMONEY",
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                model.name,
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                model.date ?: "Date not recorded",
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
            ReceiptRule()
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "ITEM / QTY × PRICE",
                    Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    "TOTAL",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            model.lines.forEach { line ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        line.item.name,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "${line.item.quantity} × ${line.price?.let(Money::format) ?: "Not specified"}",
                            Modifier.weight(1f),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            line.total?.let(Money::format) ?: "—",
                            Modifier.widthIn(max = 140.dp),
                            textAlign = TextAlign.End,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (line.item.note.isNotBlank())
                        Text(line.item.note, style = MaterialTheme.typography.bodySmall)
                }
            }
            ReceiptRule()
            ReceiptTotal("Subtotal (priced items)", model.subtotal)
            ReceiptTotal(
                if (model.missing > 0) "Partial estimated total" else "Estimated total",
                model.subtotal,
                true,
            )
            if (model.missing > 0)
                Text(
                    "${shopCount(model.missing, "unpriced item")} excluded.",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            Text(
                "Your estimate • prices may change",
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReceiptTotal(label: String, total: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            Modifier.weight(1f),
            fontFamily = FontFamily.Monospace,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            Money.format(total),
            Modifier.widthIn(max = 160.dp),
            textAlign = TextAlign.End,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ReceiptRule() {
    val color = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color,
            Offset.Zero,
            Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
    }
}

private object ReceiptPaper : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val teeth = (size.width / with(density) { 14.dp.toPx() }).toInt().coerceAtLeast(1)
        val step = size.width / teeth
        val depth = with(density) { 6.dp.toPx() }
        val path =
            Path().apply {
                moveTo(0f, depth)
                repeat(teeth) {
                    lineTo(it * step + step / 2, 0f)
                    lineTo((it + 1) * step, depth)
                }
                lineTo(size.width, size.height - depth)
                repeat(teeth) {
                    lineTo(size.width - it * step - step / 2, size.height)
                    lineTo(size.width - (it + 1) * step, size.height - depth)
                }
                close()
            }
        return Outline.Generic(path)
    }
}
