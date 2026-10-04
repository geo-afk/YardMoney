package jm.yardmoney.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
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
internal fun ShopReceipt(
    model: ShopReceiptModel,
    modifier: Modifier = Modifier,
    appearance: ReceiptAppearance = rememberReceiptAppearance(),
    thumbnail: Boolean = false,
) {
    val background =
        receiptBackground(
            appearance,
            MaterialTheme.colorScheme.background.toArgb(),
            MaterialTheme.colorScheme.primary.toArgb(),
        )
    val ink = Color(receiptInk(background))
    val thermal = appearance.layout == ReceiptLayout.Thermal
    val compact = appearance.layout == ReceiptLayout.Compact
    val minimal = appearance.layout == ReceiptLayout.Minimal
    val detailed = appearance.layout == ReceiptLayout.Detailed
    val font = if (thermal) FontFamily.Monospace else FontFamily.Default
    // Repair all text and rules against arbitrary paper colors to WCAG 4.5:1.
    androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides ink) {
        Surface(
            modifier.fillMaxWidth(),
            shape =
                if (thermal) ReceiptPaper
                else if (compact) RoundedCornerShape(4.dp) else MaterialTheme.shapes.medium,
            color = Color(background),
            contentColor = ink,
            tonalElevation = 0.dp,
        ) {
            Column(
                Modifier.padding(
                    horizontal = if (compact) 12.dp else if (minimal) 24.dp else 20.dp,
                    vertical = if (compact) 12.dp else if (thumbnail) 16.dp else 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp),
            ) {
                // Thumbnail previews use the actual layout hierarchy, not a shared compact
                // template.
                if (compact) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            model.name,
                            Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            model.date ?: "Not specified",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                } else {
                    val alignment = if (thermal) TextAlign.Center else TextAlign.Start
                    Text(
                        if (detailed) "PURCHASE DETAILS" else "YARDMONEY",
                        Modifier.fillMaxWidth(),
                        textAlign = alignment,
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        model.name,
                        Modifier.fillMaxWidth(),
                        textAlign = alignment,
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        style =
                            if (minimal) MaterialTheme.typography.headlineSmall
                            else MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        model.date ?: "Not specified",
                        Modifier.fillMaxWidth(),
                        textAlign = alignment,
                        fontFamily = font,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (thermal) ReceiptRule() else if (!minimal) HorizontalDivider(color = ink)
                if (thermal || compact) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            if (compact) "ITEM / QTY" else "ITEM / QTY × PRICE",
                            Modifier.weight(1f),
                            fontFamily = font,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            "TOTAL",
                            fontFamily = font,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                val lines =
                    if (appearance.layout == ReceiptLayout.Detailed)
                        model.lines.sortedBy { it.item.category }
                    else model.lines
                if (lines.isEmpty())
                    Text("No item lines were captured.", style = MaterialTheme.typography.bodySmall)
                lines.forEachIndexed { index, line ->
                    when (appearance.layout) {
                        ReceiptLayout.Thermal -> ReceiptProduct(line, font)
                        ReceiptLayout.Minimal -> {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(line.item.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        receiptQuantityPrice(line),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    if (line.item.note.isNotBlank())
                                        Text(
                                            line.item.note,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                }
                                Text(
                                    line.total?.let(Money::format) ?: "Not specified",
                                    Modifier.widthIn(max = 140.dp),
                                    textAlign = TextAlign.End,
                                )
                            }
                            if (index < lines.lastIndex) Spacer(Modifier.height(4.dp))
                        }
                        ReceiptLayout.Compact -> {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${if (line.quantitySpecified) line.item.quantity + " × " else ""}${line.item.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        "Unit: ${line.price?.let(Money::format) ?: "Not specified"}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    if (line.item.note.isNotBlank())
                                        Text(
                                            line.item.note,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                }
                                Text(
                                    line.total?.let(Money::format) ?: "Not specified",
                                    Modifier.widthIn(max = 140.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                        ReceiptLayout.Detailed -> {
                            if (
                                index == 0 || lines[index - 1].item.category != line.item.category
                            ) {
                                Text(
                                    line.item.category,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Surface(
                                Modifier.fillMaxWidth(),
                                color = Color(background),
                                contentColor = ink,
                                border = BorderStroke(1.dp, ink.copy(alpha = .35f)),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(line.item.name, fontWeight = FontWeight.Bold)
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "Quantity",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            Text(
                                                if (line.quantitySpecified) line.item.quantity
                                                else "Not specified"
                                            )
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "Unit price",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            Text(line.price?.let(Money::format) ?: "Not specified")
                                        }
                                    }
                                    if (line.item.note.isNotBlank())
                                        Text(
                                            line.item.note,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    ReceiptTotal("Line total", line.total, true, FontFamily.Default)
                                }
                            }
                        }
                    }
                }
                if (thermal) ReceiptRule() else HorizontalDivider(color = ink)
                ReceiptTotal(
                    if (model.scanned) "Subtotal" else "Subtotal (priced items)",
                    if (model.scanned) model.recordedSubtotal else model.subtotal,
                )
                ReceiptTotal(
                    if (model.scanned) "Receipt total"
                    else if (model.missing > 0) "Partial estimated total" else "Estimated total",
                    if (model.scanned) model.actualTotal else model.subtotal,
                    true,
                    font,
                )
                if (model.missing > 0)
                    Text(
                        "${shopCount(model.missing, "unpriced item")} excluded.",
                        fontFamily = font,
                        style = MaterialTheme.typography.bodySmall,
                    )
                Text(
                    if (model.scanned) "Saved receipt" else "Your estimate • prices may change",
                    Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontFamily = font,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun receiptQuantityPrice(line: ShopReceiptLine): String =
    "${if (line.quantitySpecified) line.item.quantity else "Not specified"} × ${line.price?.let(Money::format) ?: "Not specified"}"

@Composable
private fun ReceiptProduct(line: ShopReceiptLine, font: FontFamily) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(line.item.name, fontFamily = font, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                receiptQuantityPrice(line),
                Modifier.weight(1f),
                fontFamily = font,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                line.total?.let(Money::format) ?: "Not specified",
                Modifier.widthIn(max = 140.dp),
                textAlign = TextAlign.End,
                fontFamily = font,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (line.item.note.isNotBlank())
            Text(line.item.note, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReceiptTotal(
    label: String,
    total: Long?,
    bold: Boolean = false,
    font: FontFamily = FontFamily.Default,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            Modifier.weight(1f),
            fontFamily = font,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            total?.let(Money::format) ?: "Not specified",
            Modifier.widthIn(max = 160.dp),
            textAlign = TextAlign.End,
            fontFamily = font,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ReceiptRule() {
    val color = LocalContentColor.current
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
