package jm.yardmoney.core

import java.io.*
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Streaming, bounded CSV decoding; the raw file is never retained. */
object StatementCsv {
    const val MAX_BYTES = 20_000_000L
    const val MAX_ROWS = 20_000
    private val spaces = Regex("\\s+")
    val dateFormats = listOf("dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd")
    fun read(input: InputStream): StatementDocument {
        val bounded = object : FilterInputStream(input) {
            var count = 0L
            fun checked(n: Int): Int { if (n > 0) count += n; require(count <= MAX_BYTES) { "Choose a CSV file smaller than 20 MB." }; return n }
            override fun read(): Int { val n = `in`.read(); if (n >= 0) checked(1); return n }
            override fun read(b: ByteArray, off: Int, len: Int): Int = checked(`in`.read(b, off, len))
        }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return read(InputStreamReader(bounded, decoder))
    }
    fun read(reader: Reader): StatementDocument {
        val buffered = reader.buffered()
        buffered.mark(65537)
        val sample = StringBuilder()
        var quoted = false
        while (true) {
            val n = buffered.read(); if (n < 0) break
            val c = n.toChar()
            if (c == '"') quoted = !quoted
            if ((c == '\n' || c == '\r') && !quoted) break
            sample.append(c)
            require(sample.length <= 65536) { "The CSV header is too long." }
        }
        buffered.reset()
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        quoted = false
        sample.forEach { c -> if (c == '"') quoted = !quoted else if (!quoted && c in counts) counts[c] = counts.getValue(c) + 1 }
        val delimiter = counts.maxBy { it.value }.key
        val stream = PushbackReader(buffered, 1)
        val records = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var closedQuote = false
        var seen = false
        var characters = 0L
        fun fieldDone() { row += field.toString(); field = StringBuilder(); closedQuote = false; require(row.size <= 128) { "The CSV has too many columns." } }
        fun rowDone() {
            fieldDone()
            if (row.any { it.isNotBlank() }) records += row.toList()
            require(records.size <= MAX_ROWS + 1) { "Import up to 20,000 statement rows at a time." }
            row = mutableListOf(); seen = false
        }
        while (true) {
            val n = stream.read(); if (n < 0) break
            characters++; require(characters <= MAX_BYTES) { "Choose a CSV file smaller than 20 MB." }
            val c = n.toChar()
            if (characters == 1L && c == '\uFEFF') continue
            seen = true
            if (inQuotes) {
                if (c == '"') {
                    val next = stream.read()
                    if (next == '"'.code) field.append('"')
                    else { inQuotes = false; closedQuote = true; if (next >= 0) stream.unread(next) }
                } else field.append(c)
            } else when {
                c == delimiter -> fieldDone()
                c == '\n' || c == '\r' -> { if (c == '\r') { val next = stream.read(); if (next >= 0 && next != '\n'.code) stream.unread(next) }; rowDone() }
                c == '"' -> { require(field.isEmpty() && !closedQuote) { "Check the CSV quotes." }; inQuotes = true }
                closedQuote && c != ' ' -> throw IllegalArgumentException("Check the text after a quoted CSV field.")
                !closedQuote -> field.append(c)
            }
            require(field.length <= 10000) { "A CSV field is too long." }
        }
        require(!inQuotes) { "Close the quoted CSV field before importing." }
        if (seen || row.isNotEmpty() || field.isNotEmpty()) rowDone()
        require(records.isNotEmpty()) { "Choose a CSV file containing statement rows." }
        val first = records.first().map { it.trim().lowercase(Locale.ROOT) }
        val header = first.count { it in listOf("date", "description", "amount", "debit", "credit", "balance", "transaction date", "details") } >= 2
        require(records.size - (if (header) 1 else 0) <= MAX_ROWS) { "Import up to 20,000 statement rows at a time." }
        return StatementDocument(records, delimiter, header)
    }
    fun mapping(document: StatementDocument): StatementMapping {
        val labels = document.records.first().map { it.trim().lowercase(Locale.ROOT) }
        fun find(vararg names: String) = if (document.likelyHeader) labels.indexOfFirst { it in names } else -1
        return StatementMapping(find("date", "transaction date"), find("description", "details"), find("amount"),
            find("debit"), find("credit"), find("balance"), header = document.likelyHeader)
    }
    fun normalise(text: String) = text.trim().replace(spaces, " ").lowercase(Locale.ROOT)
    fun fingerprint(account: String, date: LocalDate, signed: Long, description: String) =
        hash(listOf(account, date.toString(), signed.toString(), normalise(description)))
    fun hash(parts: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { val bytes = it.toByteArray(Charsets.UTF_8); digest.update(bytes.size.toString().toByteArray()); digest.update(':'.code.toByte()); digest.update(bytes) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun batchId(account: String, mapping: StatementMapping, document: StatementDocument) =
        hash(listOf(account, mapping.toString()) + document.records.map { hash(it) })
    private fun amount(text: String): Long {
        val trimmed = text.trim().replace(Regex("(?i)^(?:J\\$|JMD\\s*|\\$)\\s*"), "")
        val negative = trimmed.startsWith('(') && trimmed.endsWith(')')
        val number = if (negative) trimmed.substring(1, trimmed.length - 1) else trimmed
        val minor = Money.parse(number, allowNegative = true)
        require(!negative || minor >= 0) { "Check the negative amount." }
        return if (negative) -minor else minor
    }
    fun validateMapping(mapping: StatementMapping) {
        require(mapping.dateFormat in dateFormats) { "Choose the statement date format." }
        require(mapping.dateColumn in 0..127 && mapping.descriptionColumn in 0..127 &&
            listOf(mapping.amountColumn, mapping.debitColumn, mapping.creditColumn, mapping.balanceColumn).all { it in -1..127 }) {
            "Choose valid statement columns."
        }
        require(mapping.amountColumn >= 0 || mapping.debitColumn >= 0 && mapping.creditColumn >= 0) { "Map an amount column or both debit and credit columns." }
        val used = listOf(mapping.dateColumn, mapping.descriptionColumn, mapping.balanceColumn) +
            if (mapping.amountColumn >= 0) listOf(mapping.amountColumn) else listOf(mapping.debitColumn, mapping.creditColumn)
        require(used.filter { it >= 0 }.distinct().size == used.count { it >= 0 }) { "Map each selected field to a different column." }
    }
    fun preview(document: StatementDocument, mapping: StatementMapping, account: String,
        existing: List<StatementExisting>, today: LocalDate): List<StatementRow> {
        validateMapping(mapping)
        require(mapping.dateColumn >= 0 && mapping.descriptionColumn >= 0) { "Map the date and description columns." }
        require(mapping.amountColumn >= 0 || mapping.debitColumn >= 0 && mapping.creditColumn >= 0) { "Map an amount column or both debit and credit columns." }
        val used = listOf(mapping.dateColumn, mapping.descriptionColumn, mapping.balanceColumn) +
            if (mapping.amountColumn >= 0) listOf(mapping.amountColumn) else listOf(mapping.debitColumn, mapping.creditColumn)
        require(used.filter { it >= 0 }.distinct().size == used.count { it >= 0 }) { "Map each selected field to a different column." }
        val formatter = DateTimeFormatter.ofPattern(mapping.dateFormat.replace("yyyy", "uuuu"), Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)
        val seen = existing.map { fingerprint(it.accountId, it.date, it.signedMinor, it.description) }.toMutableSet()
        val rows = document.records.drop(if (mapping.header) 1 else 0)
        require(rows.size <= MAX_ROWS) { "Import up to 20,000 statement rows at a time." }
        return rows.mapIndexed { index, cells ->
            val issues = mutableListOf<String>()
            fun cell(column: Int) = cells.getOrNull(column)?.trim().orEmpty()
            val description = cell(mapping.descriptionColumn)
            if (description.isBlank() || description.length > 240) issues += "Use a description of 1 to 240 characters."
            val date = runCatching { LocalDate.parse(cell(mapping.dateColumn), formatter).also { require(it <= today) } }.getOrNull()
            if (date == null) issues += "Check the date and selected format; future dates are not posted."
            val signed = runCatching {
                if (mapping.amountColumn >= 0) amount(cell(mapping.amountColumn))
                else {
                    val debit = cell(mapping.debitColumn).takeIf { it.isNotBlank() }?.let(::amount) ?: 0
                    val credit = cell(mapping.creditColumn).takeIf { it.isNotBlank() }?.let(::amount) ?: 0
                    require(debit >= 0 && credit >= 0 && (debit == 0L || credit == 0L))
                    Math.subtractExact(credit, debit)
                }.also { require(it != 0L) }
            }.getOrNull()
            if (signed == null) issues += "Check the amount, or use only one debit or credit per row."
            if (mapping.balanceColumn >= 0 && cell(mapping.balanceColumn).isNotBlank() && runCatching { amount(cell(mapping.balanceColumn)) }.isFailure)
                issues += "Check this row's balance amount."
            val fingerprint = if (date != null && signed != null) fingerprint(account, date, signed, description) else ""
            val duplicate = fingerprint.isNotBlank() && !seen.add(fingerprint)
            StatementRow(index, date, signed, description, hash(cells + index.toString()), duplicate, issues)
        }
    }
}
