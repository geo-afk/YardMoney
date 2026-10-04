package jm.yardmoney.data

import java.time.LocalDate
import jm.yardmoney.core.*
import org.json.JSONArray
import org.json.JSONObject

// The existing version-1 review payload in receipt_drafts.rawText already stores parsed
// fields and items. Populate it at scan time too; old drafts keep the parser fallback.
// No table, second receipt model, or Room schema migration is needed.
internal object ReceiptDraftCodec {
    fun encode(raw: String): String {
        val parsed = ReceiptParser.parse(raw)
        val json =
            JSONObject()
                .put("version", 1)
                .put("merchant", parsed.merchant)
                .put("branch", parsed.location)
                .put("date", parsed.date?.toString() ?: "")
                .put("total", parsed.totalMinor?.let(Money::input) ?: "")
                .put("subtotal", parsed.subtotalMinor?.let(Money::input) ?: "")
                .put("tax", parsed.taxMinor?.let(Money::input) ?: "")
                .put("discount", parsed.discountMinor?.let(Money::input) ?: "")
                .put("time", parsed.time)
                .put("payment", parsed.paymentMethod)
                .put("receiptNo", parsed.receiptNumber)
                .put("transactionNo", parsed.transactionNumber)
                .put("totalOnly", parsed.lines.isEmpty())
        val items = JSONArray()
        parsed.lines.forEach { line ->
            items.put(
                JSONObject()
                    .put("raw", line.raw)
                    .put("name", line.name)
                    .put("quantity", line.quantity)
                    .put("quantitySpecified", line.quantitySpecified)
                    .put("total", line.totalMinor?.let(Money::input) ?: "")
                    .put("unitPrice", line.unitPriceMinor?.let(Money::input) ?: "")
                    .put("confidence", line.confidence)
                    .put("size", "")
                    .put("unit", "item")
            )
        }
        return json.put("items", items).toString()
    }

    fun parse(raw: String): ReceiptSuggestion {
        val fallback = ReceiptParser.parse(raw)
        val json =
            runCatching {
                    JSONObject(
                        raw.substringAfter("[Review edits]")
                            .substringBefore("[Verified receipt details]")
                    )
                }
                .getOrNull()
                ?.takeIf { it.optInt("version") == 1 } ?: return fallback
        fun text(key: String, default: String) = json.optString(key, default)
        fun money(key: String, default: Long?): Long? =
            if (json.has(key))
                json
                    .optString(key)
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { Money.parse(it, true) }.getOrNull() }
            else default
        val items = json.optJSONArray("items")
        val lines =
            if (items == null) fallback.lines
            else
                (0 until items.length()).map { index ->
                    val line = items.getJSONObject(index)
                    val original = fallback.lines.find { it.raw == line.optString("raw") }
                    fun amount(key: String): Long? =
                        line
                            .optString(key)
                            .takeIf { it.isNotBlank() }
                            ?.let { runCatching { Money.parse(it, true) }.getOrNull() }
                    SuggestedLine(
                        line.optString("raw"),
                        line.optString("name"),
                        line.optString("quantity", "1"),
                        amount("total"),
                        unitPriceMinor =
                            if (line.has("unitPrice")) amount("unitPrice")
                            else original?.unitPriceMinor,
                        confidence = line.optString("confidence", "Check extracted text"),
                        quantitySpecified =
                            line.optBoolean(
                                "quantitySpecified",
                                original?.quantitySpecified == true ||
                                    line.optString("quantity", "1") != (original?.quantity ?: "1"),
                            ),
                    )
                }
        return fallback.copy(
            merchant = text("merchant", fallback.merchant),
            location = text("branch", fallback.location),
            date =
                if (json.has("date"))
                    runCatching { LocalDate.parse(json.optString("date")) }.getOrNull()
                else fallback.date,
            totalMinor = money("total", fallback.totalMinor),
            subtotalMinor = money("subtotal", fallback.subtotalMinor),
            taxMinor = money("tax", fallback.taxMinor),
            discountMinor = money("discount", fallback.discountMinor),
            lines = lines,
            time = text("time", fallback.time),
            paymentMethod = text("payment", fallback.paymentMethod),
            receiptNumber = text("receiptNo", fallback.receiptNumber),
            transactionNumber = text("transactionNo", fallback.transactionNumber),
        )
    }
}
