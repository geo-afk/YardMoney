package jm.yardmoney.security

import android.database.Cursor
import android.util.Base64
import androidx.room.withTransaction
import java.time.LocalDate
import jm.yardmoney.YardMoneyApplication
import jm.yardmoney.core.*
import jm.yardmoney.data.FinanceRepository
import org.json.JSONArray
import org.json.JSONObject

/** Versioned portable snapshot, independent of device Keystore; no unencrypted temporary file. */
class PortableBackup(private val app: YardMoneyApplication) {
    private val tables =
        listOf(
            "profile",
            "accounts",
            "category_limits",
            "transactions",
            "entries",
            "splits",
            "goals",
            "bill_templates",
            "commitments",
            "settlements",
            "goal_contributions",
            "receipt_drafts",
            "receipts",
            "receipt_items",
            "price_observations",
            "shopping_lists",
            "shopping_items",
        )

    suspend fun export(password: CharArray): ByteArray =
        try {
            exportInside(password)
        } finally {
            // Wipe secrets even when validation, storage, or a cancelled database read fails early.
            password.fill('\u0000')
        }

    private suspend fun exportInside(password: CharArray): ByteArray {
        require(password.size >= 12) { "Use a backup password of at least 12 characters." }
        val root =
            app.database.withTransaction {
                val json = JSONObject().put("version", 3)
                val images = JSONObject()
                var used = 0L
                tables.forEach { table ->
                    val rows = JSONArray()
                    app.database.openHelper.readableDatabase
                        .query("SELECT * FROM $table ORDER BY rowid")
                        .use { cursor ->
                            while (cursor.moveToNext()) {
                                val row = JSONObject()
                                cursor.columnNames.forEachIndexed { i, name ->
                                    val value: Any =
                                        when (cursor.getType(i)) {
                                            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                                            Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                                            else -> error("Unsupported backup column")
                                        }
                                    row.put(name, value)
                                    if (
                                        name == "imageRef" && value is String && !images.has(value)
                                    ) {
                                        val photo = app.storage.readReceipt(value)
                                        used += ((photo.size.toLong() + 2) / 3) * 4
                                        require(used <= 45_000_000) {
                                            "Receipt images exceed the pilot backup limit."
                                        }
                                        images.put(
                                            value,
                                            Base64.encodeToString(photo, Base64.NO_WRAP),
                                        )
                                        photo.fill(0)
                                    }
                                }
                                used += row.toString().toByteArray(Charsets.UTF_8).size + 2
                                require(used <= 45_000_000) {
                                    "Records exceed the pilot backup limit."
                                }
                                rows.put(row)
                            }
                        }
                    json.put(table, rows)
                }
                json.put("images", images)
            }
        val clear = root.toString().toByteArray(Charsets.UTF_8)
        require(clear.size <= 50_000_000) { "This backup exceeds the pilot's 50 MB limit." }
        return try {
            BackupCipher.encrypt(clear, password)
        } finally {
            clear.fill(0)
            password.fill('\u0000')
        }
    }

    suspend fun restore(bytes: ByteArray, password: CharArray) {
        try {
            restoreInside(bytes, password)
        } finally {
            password.fill('\u0000')
        }
    }

    private suspend fun restoreInside(bytes: ByteArray, password: CharArray) {
        require(bytes.size <= 50_000_064 && bytes.size > 49) { "Invalid or oversized backup." }
        val clear =
            try {
                BackupCipher.decrypt(bytes, password)
            } catch (e: javax.crypto.AEADBadTagException) {
                error("Incorrect password or damaged backup. Your current data is unchanged.")
            } finally {
                password.fill('\u0000')
            }
        val root =
            try {
                JSONObject(clear.toString(Charsets.UTF_8))
            } finally {
                clear.fill(0)
            }
        require(root.getInt("version") in 1..3) { "Unsupported backup version." }
        require(
            root.keys().asSequence().toSet() == (tables + listOf("version", "images")).toSet()
        ) {
            "Unexpected backup content."
        }
        val images = root.getJSONObject("images")
        val refs = mutableMapOf<String, String>()
        try {
            images.keys().forEach { ref ->
                require(ReceiptNames.storedFile.matches(ref))
                val data = Base64.decode(images.getString(ref), Base64.NO_WRAP)
                refs[ref] = app.storage.saveReceipt(FinanceRepository.id(), data)
                data.fill(0)
            }
            app.database.withTransaction {
                val sql = app.database.openHelper.writableDatabase
                val columns = tables.associateWith { table ->
                    sql.query("PRAGMA table_info($table)").use { c ->
                        buildMap<String, String> {
                            while (c.moveToNext()) put(c.getString(1), c.getString(2))
                        }
                    }
                }
                // Everything, including validation, is transactional: a rejected backup leaves the
                // ledger unchanged.
                tables.asReversed().forEach { sql.execSQL("DELETE FROM $it") }
                tables.forEach { table ->
                    val rows = root.getJSONArray(table)
                    require(rows.length() <= 100_000) { "Too many records." }
                    val shape = columns.getValue(table)
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONObject(i)
                        // Backups from schema 1 have no account binding; preserve them as shared
                        // plans.
                        if (
                            root.getInt("version") == 1 &&
                                table in listOf("commitments", "bill_templates", "category_limits")
                        )
                            row.put("accountId", JSONObject.NULL)
                        if (root.getInt("version") < 3) {
                            if (table == "shopping_lists") row.put("createdDate", JSONObject.NULL)
                            if (table == "shopping_items") {
                                row.put("category", "Other")
                                row.put("note", "")
                            }
                        }
                        require(row.keys().asSequence().toSet() == shape.keys) {
                            "Invalid $table columns."
                        }
                        val args =
                            shape
                                .map { (name, type) ->
                                    if (row.isNull(name)) null
                                    else if (type == "INTEGER") {
                                        val value = row.get(name)
                                        require(value is Int || value is Long) {
                                            "Amounts must be exact integers."
                                        }
                                        (value as Number).toLong()
                                    } else {
                                        val value = row.get(name)
                                        require(value is String && value.length <= 300_000)
                                        if (name == "imageRef")
                                            refs[value] ?: error("Missing receipt image")
                                        else value
                                    }
                                }
                                .toTypedArray<Any?>()
                        sql.execSQL(
                            "INSERT INTO $table (${shape.keys.joinToString(",")}) VALUES (${shape.keys.joinToString(","){"?"}})",
                            args,
                        )
                    }
                }
                listOf("commitments", "bill_templates", "category_limits").forEach { table ->
                    sql.query(
                            "SELECT id FROM $table WHERE accountId IS NOT NULL AND accountId NOT IN (SELECT id FROM accounts)"
                        )
                        .use {
                            require(!it.moveToFirst()) { "Invalid plan account." }
                        }
                }
                sql.query("PRAGMA foreign_key_check").use {
                    require(!it.moveToFirst()) { "Backup relationships are invalid." }
                }
                fun check(query: String, message: String) {
                    sql.query(query).use { require(!it.moveToFirst()) { message } }
                }
                check(
                    "SELECT id FROM accounts WHERE included NOT IN (0,1) OR openingMinor < -${Money.MAX_MINOR} OR openingMinor > ${Money.MAX_MINOR}",
                    "Invalid account balance.",
                )
                check(
                    "SELECT id FROM transactions WHERE amountMinor=0 OR amountMinor < -${Money.MAX_MINOR} OR amountMinor > ${Money.MAX_MINOR} OR kind NOT IN ('EXPENSE','INCOME','TRANSFER','REFUND','ADJUSTMENT') OR bucket NOT IN ('NEEDS','WANTS','SAVINGS') OR (kind<>'ADJUSTMENT' AND amountMinor<0)",
                    "Invalid transaction.",
                )
                check(
                    "SELECT t.id FROM transactions t WHERE (t.kind='TRANSFER' AND ((SELECT COUNT(*) FROM entries e WHERE e.transactionId=t.id)<>2 OR (SELECT SUM(signedMinor) FROM entries e WHERE e.transactionId=t.id)<>0 OR (SELECT COUNT(DISTINCT accountId) FROM entries e WHERE e.transactionId=t.id)<>2 OR (SELECT MAX(signedMinor) FROM entries e WHERE e.transactionId=t.id)<>t.amountMinor)) OR (t.kind<>'TRANSFER' AND ((SELECT COUNT(*) FROM entries e WHERE e.transactionId=t.id)<>1 OR (SELECT SUM(signedMinor) FROM entries e WHERE e.transactionId=t.id)<>CASE WHEN t.kind='EXPENSE' THEN -t.amountMinor ELSE t.amountMinor END))",
                    "Account movements do not reconcile.",
                )
                check(
                    "SELECT c.id FROM commitments c WHERE c.amountMinor<=0 OR c.amountMinor>${Money.MAX_MINOR} OR COALESCE((SELECT SUM(amountMinor) FROM settlements WHERE commitmentId=c.id),0)>c.amountMinor",
                    "Invalid reservation.",
                )
                check("SELECT id FROM settlements WHERE amountMinor<=0", "Invalid payment.")
                check(
                    "SELECT s.id FROM settlements s JOIN transactions t ON t.id=s.transactionId JOIN commitments c ON c.id=s.commitmentId WHERE s.amountMinor>t.amountMinor OR t.kind NOT IN ('EXPENSE','TRANSFER') OR (c.kind IN ('BILL','DEBT') AND t.kind<>'EXPENSE') OR (c.kind='SAVINGS' AND t.kind<>'TRANSFER')",
                    "Invalid linked payment.",
                )
                check(
                    "SELECT id FROM transactions WHERE kind='REFUND' AND refundOfId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM transactions original WHERE original.id=transactions.refundOfId AND original.kind='EXPENSE' AND original.bucket=transactions.bucket)",
                    "Invalid linked refund.",
                )
                check(
                    "SELECT t.id FROM transactions t WHERE t.kind='EXPENSE' AND COALESCE((SELECT SUM(amountMinor) FROM transactions WHERE refundOfId=t.id),0)>t.amountMinor",
                    "Refunds exceed spending.",
                )
                check(
                    "SELECT a.id FROM accounts a WHERE a.openingMinor+COALESCE((SELECT SUM(signedMinor) FROM entries WHERE accountId=a.id),0) < -${Money.MAX_MINOR} OR a.openingMinor+COALESCE((SELECT SUM(signedMinor) FROM entries WHERE accountId=a.id),0) > ${Money.MAX_MINOR}",
                    "Account balance exceeds range.",
                )
                check(
                    "SELECT id FROM goals WHERE targetMinor<=0 OR targetMinor>${Money.MAX_MINOR} OR initialSavedMinor<0 OR initialSavedMinor>${Money.MAX_MINOR} OR initialSavedMinor+COALESCE((SELECT SUM(amountMinor) FROM goal_contributions WHERE goalId=goals.id),0)<0",
                    "Invalid goal amounts.",
                )
                check(
                    "SELECT g.id FROM goal_contributions g JOIN transactions t ON t.id=g.transactionId WHERE t.kind<>'TRANSFER' OR abs(g.amountMinor)<>t.amountMinor",
                    "Goal contribution does not match its transfer.",
                )
                check(
                    "SELECT id FROM category_limits WHERE limitMinor<0 OR limitMinor>${Money.MAX_MINOR} OR bucket NOT IN ('NEEDS','WANTS','SAVINGS')",
                    "Invalid category limit.",
                )
                require(app.repository.dao.accountCount() <= 1000)
                sql.query("SELECT COUNT(*),MIN(id),MAX(id) FROM profile").use { c ->
                    c.moveToFirst()
                    require(c.getInt(0) == 1 && c.getInt(1) == 1 && c.getInt(2) == 1)
                }
                check(
                    "SELECT t.id FROM transactions t WHERE kind IN ('EXPENSE','REFUND') AND COALESCE((SELECT SUM(amountMinor) FROM splits s WHERE s.transactionId=t.id),0) <> CASE WHEN kind='REFUND' THEN -t.amountMinor ELSE t.amountMinor END",
                    "Expense splits do not reconcile.",
                )
                check(
                    "SELECT s.id FROM splits s JOIN transactions t ON t.id=s.transactionId WHERE t.kind NOT IN ('EXPENSE','REFUND') OR s.bucket<>t.bucket OR (t.kind='EXPENSE' AND s.amountMinor<=0) OR (t.kind='REFUND' AND s.amountMinor>=0)",
                    "Invalid spending split.",
                )
                val profile = app.repository.dao.getProfile() ?: error("Backup has no profile.")
                BudgetSplit(profile.needsBp, profile.wantsBp, profile.savingsBp)
                PayFrequency.valueOf(profile.frequency)
                LocalDate.parse(profile.periodStart)
                LocalDate.parse(profile.nextPayday)
                require(
                    profile.typicalNetMinor in 0..Money.MAX_MINOR &&
                        profile.anchorDay in 1..31 &&
                        profile.secondDay in 1..31
                )
                tables
                    .filter { it in listOf("transactions", "receipts", "price_observations") }
                    .forEach { table ->
                        sql.query("SELECT date FROM $table").use { c ->
                            while (c.moveToNext()) LocalDate.parse(c.getString(0))
                        }
                    }
                sql.query("SELECT dueDate FROM commitments WHERE dueDate IS NOT NULL").use { c ->
                    while (c.moveToNext()) LocalDate.parse(c.getString(0))
                }
                sql.query("SELECT quantity,packageSize,confirmed FROM receipt_items").use { c ->
                    while (c.moveToNext()) {
                        Quantity.parse(c.getString(0))
                        if (c.getString(1).isNotBlank()) Quantity.parse(c.getString(1))
                        require(c.getInt(2) == 1)
                    }
                }
                // Schema-1/2 backups preserve their selected receipt estimates just like the Room
                // migration.
                if (root.getInt("version") < 3)
                    sql.execSQL(
                        "UPDATE shopping_items SET manualPriceMinor=(SELECT packPriceMinor FROM price_observations p WHERE p.productKey=shopping_items.productKey ORDER BY p.date DESC,p.rowid DESC LIMIT 1) WHERE manualPriceMinor IS NULL AND productKey IS NOT NULL"
                    )
                sql.query("SELECT quantity,manualPriceMinor,category,note FROM shopping_items")
                    .use { c ->
                        while (c.moveToNext()) {
                            Quantity.parse(c.getString(0))
                            if (!c.isNull(1)) {
                                require(c.getLong(1) in 0..Money.MAX_MINOR)
                                Quantity.estimate(c.getLong(1), c.getString(0))
                            }
                            require(c.getString(2).length in 1..120 && c.getString(3).length <= 500)
                        }
                    }
                sql.query("SELECT createdDate FROM shopping_lists WHERE createdDate IS NOT NULL")
                    .use { c ->
                        while (c.moveToNext()) LocalDate.parse(c.getString(0))
                    }
                sql.query(
                        "SELECT firstDate,frequency,anchorDay,amountMinor,active FROM bill_templates"
                    )
                    .use { c ->
                        while (c.moveToNext()) {
                            LocalDate.parse(c.getString(0))
                            require(
                                c.getString(1) in setOf("WEEKLY", "FORTNIGHTLY", "MONTHLY") &&
                                    c.getInt(2) in 1..31 &&
                                    c.getLong(3) in 1..Money.MAX_MINOR &&
                                    c.getInt(4) in 0..1
                            )
                        }
                    }
            }
        } catch (e: Exception) {
            refs.values.forEach { runCatching { app.storage.deleteReceipt(it) } }
            throw e
        }
        app.storage.removeUnusedReceiptFiles(refs.values.toSet())
    }
}
