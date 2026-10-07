package jm.yardmoney.data

import androidx.test.platform.app.InstrumentationRegistry
import jm.yardmoney.YardMoneyApplication
import jm.yardmoney.security.PortableBackup
import jm.yardmoney.core.BackupCipher
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GoldenV3BackupTest {
    @Test fun currentExporterFixtureRestoresItsLedgerAndShoppingRecords() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "jm.yardmoney.testhost") { "Use the isolated test installation." }
        val app = context.applicationContext as YardMoneyApplication
        if (app.repository.dao.getProfile() == null) {
            val today = app.repository.today
            app.repository.onboard(Profile(name = "Golden restore test", typicalNetMinor = 0,
                frequency = "MONTHLY", nextPayday = today.plusDays(14).toString(),
                anchorDay = 20, secondDay = 28, needsBp = 5000, wantsBp = 3000,
                savingsBp = 2000, periodStart = today.toString(), budgetIncomeMinor = 0), 0)
        }
        val backup = PortableBackup(app)
        val original = backup.export("golden-original-password".toCharArray())
        try {
            val bytes = instrumentation.context.assets.open("fixtures/golden-v3.yardbackup").use { it.readBytes() }
            backup.restore(bytes, "golden-v3-test-password".toCharArray())
            assertEquals("Golden fictional household", app.repository.dao.getProfile()?.name)
            assertEquals(940000L, app.repository.dao.readAccounts().single().balanceMinor)
            assertEquals("Transport", app.repository.dao.readTransactions().single().category)
            assertEquals("Golden v3 fixture", app.repository.dao.readShoppingItems().single().note)
            assertTrue(app.repository.dao.readCategoryRules().isEmpty())
            // Versions 1/2 lack account bindings and/or shopping metadata; fill only their
            // documented defaults, never silently accept unexpected table or column sets.
            for (version in 1..2) {
                val json = JSONObject(BackupCipher.decrypt(bytes, "golden-v3-test-password".toCharArray()).toString(Charsets.UTF_8))
                json.put("version", version)
                val lists = json.getJSONArray("shopping_lists")
                for (i in 0 until lists.length()) lists.getJSONObject(i).remove("createdDate")
                val items = json.getJSONArray("shopping_items")
                for (i in 0 until items.length()) {
                    items.getJSONObject(i).remove("category")
                    items.getJSONObject(i).remove("note")
                }
                if (version == 1) for (table in listOf("commitments", "bill_templates", "category_limits")) {
                    val rows = json.getJSONArray(table)
                    for (i in 0 until rows.length()) rows.getJSONObject(i).remove("accountId")
                }
                val legacy = BackupCipher.encrypt(json.toString().toByteArray(), "golden-v3-test-password".toCharArray())
                backup.restore(legacy, "golden-v3-test-password".toCharArray())
                assertEquals(940000L, app.repository.dao.readAccounts().single().balanceMinor)
                assertEquals("Other", app.repository.dao.readShoppingItems().single().category)
                assertTrue(app.repository.dao.readCategoryRules().isEmpty())
            }
            val legacyWithPhoto = JSONObject(BackupCipher.decrypt(bytes, "golden-v3-test-password".toCharArray()).toString(Charsets.UTF_8))
            legacyWithPhoto.getJSONObject("images").put("golden-photo.bin", "AQID")
            legacyWithPhoto.getJSONArray("receipts").put(JSONObject()
                .put("id", "golden-receipt").put("transactionId", legacyWithPhoto.getJSONArray("transactions").getJSONObject(0).getString("id"))
                .put("merchant", "Fictional taxi").put("branch", "").put("parish", "").put("date", "2026-10-06")
                .put("totalMinor", 60000).put("adjustmentMinor", 0).put("rawText", "Fictional receipt preview")
                .put("fingerprint", "golden-photo-test").put("imageRef", "golden-photo.bin"))
            val photoBackup = BackupCipher.encrypt(legacyWithPhoto.toString().toByteArray(), "golden-v3-test-password".toCharArray())
            backup.restore(photoBackup, "golden-v3-test-password".toCharArray())
            val receipt = app.repository.dao.readReceipts().single()
            assertNull(receipt.imageRef)
            assertEquals("Fictional receipt preview", receipt.rawText)
            assertFalse(File(context.noBackupFilesDir, "receipts/golden-photo.bin").exists())
            val exported = JSONObject(BackupCipher.decrypt(backup.export("golden-v3-test-password".toCharArray()),
                "golden-v3-test-password".toCharArray()).toString(Charsets.UTF_8))
            assertEquals(0, exported.getJSONObject("images").length())
            assertTrue(exported.getJSONArray("receipts").getJSONObject(0).isNull("imageRef"))
        } finally {
            backup.restore(original, "golden-original-password".toCharArray())
        }
    }
}
