package jm.yardmoney.data

import androidx.test.platform.app.InstrumentationRegistry
import jm.yardmoney.YardMoneyApplication
import jm.yardmoney.core.BackupCipher
import jm.yardmoney.security.PortableBackup
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ShopBackupTest {
    @Test
    fun legacyBackupRestoresShoppingMetadataAndNewBackupRetainsIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "jm.yardmoney.testhost") {
            "Backup fixture requires isolated test package."
        }
        val app = context.applicationContext as YardMoneyApplication
        if (app.repository.dao.getProfile() == null) {
            val today = app.repository.today
            app.repository.onboard(
                Profile(
                    name = "Backup fixture",
                    typicalNetMinor = 1000000,
                    frequency = "MONTHLY",
                    nextPayday = today.plusDays(14).toString(),
                    anchorDay = 15,
                    secondDay = 28,
                    needsBp = 5000,
                    wantsBp = 3000,
                    savingsBp = 2000,
                    periodStart = today.toString(),
                    budgetIncomeMinor = 0,
                ),
                1000000,
            )
        }
        val backup = PortableBackup(app)
        fun password() = "shopping-test-password".toCharArray()
        val original = backup.export(password())
        try {
            val list = ShoppingList("backup-list", "Backup trip", "2026-10-03")
            val item =
                ShoppingItem(
                    "backup-item",
                    list.id,
                    "Rice",
                    "1.5",
                    null,
                    null,
                    true,
                    true,
                    "Groceries",
                    "Brown",
                )
            app.repository.saveShoppingList(list, listOf(item))
            val accountId = app.repository.dao.readAccounts().first().account.id
            val rule = CategoryRule("backup-rule", "Rice", "CONTAINS", "Groceries", "NEEDS", accountId, 1)
            app.repository.saveCategoryRule(rule)
            val current = backup.export(password())
            backup.restore(current, password())
            assertEquals(item, app.repository.dao.readShoppingItems().single { it.id == item.id })
            assertEquals(rule, app.repository.dao.readCategoryRules().single { it.id == rule.id })
            val clear = BackupCipher.decrypt(current, password())
            val json =
                try {
                    JSONObject(clear.toString(Charsets.UTF_8))
                } finally {
                    clear.fill(0)
                }
            assertEquals(5, json.getInt("version"))
            assertEquals(0, json.getJSONObject("images").length())
            json.put("version", 2)
            json.remove("category_rules")
            listOf("import_batches", "import_records", "import_mappings").forEach { json.remove(it) }
            val lists = json.getJSONArray("shopping_lists")
            for (i in 0 until lists.length()) lists.getJSONObject(i).remove("createdDate")
            val items = json.getJSONArray("shopping_items")
            for (i in 0 until items.length()) {
                items.getJSONObject(i).remove("category")
                items.getJSONObject(i).remove("note")
            }
            val legacyClear = json.toString().toByteArray(Charsets.UTF_8)
            val legacy =
                try {
                    BackupCipher.encrypt(legacyClear, password())
                } finally {
                    legacyClear.fill(0)
                }
            backup.restore(legacy, password())
            assertTrue(app.repository.dao.readCategoryRules().isEmpty())
            val restored = app.repository.dao.readShoppingItems().single { it.id == item.id }
            assertNull(app.repository.dao.readLists().single { it.id == list.id }.createdDate)
            assertEquals("Other", restored.category)
            assertEquals("", restored.note)
            assertNull(restored.manualPriceMinor)
            assertTrue(restored.checked)
            assertTrue(restored.optional)
        } finally {
            backup.restore(original, password())
        }
    }
}
