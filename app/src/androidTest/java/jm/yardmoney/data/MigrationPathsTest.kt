package jm.yardmoney.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

// https://developer.android.com/training/data-storage/room/migrating-db-versions
// Validate every supported starting schema and the intermediate upgrade, not only an empty DB.
class MigrationPathsTest {
    @get:Rule
    val helper =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), YardDatabase::class.java)

    @Test
    fun eachUpgradePathPreservesLinkedFinancialAndReceiptRecords() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((from, to) in listOf(1 to 2, 1 to 3, 2 to 3)) {
            val name = "quality-migration-$from-$to-${System.nanoTime()}"
            try {
                helper.createDatabase(name, from).use { db ->
                    db.execSQL("INSERT INTO accounts VALUES ('cash','Cash 💰','CASH',100000,1)")
                    db.execSQL(
                        "INSERT INTO transactions VALUES ('purchase','purchase','EXPENSE',2500,'2026-10-01','Market 🍚','Groceries','NEEDS',NULL)"
                    )
                    db.execSQL("INSERT INTO entries VALUES ('entry','purchase','cash',-2500)")
                    db.execSQL(
                        "INSERT INTO splits VALUES ('split','purchase','Groceries','NEEDS',2500)"
                    )
                    db.execSQL(
                        "INSERT INTO receipts VALUES ('receipt','purchase','Market','','','2026-10-01',2500,0,'RICE 25.00','scan-fingerprint',NULL)"
                    )
                    db.execSQL(
                        "INSERT INTO receipt_items VALUES ('product','receipt','RICE','Rice','1',2500,'1','kg',1)"
                    )
                    db.execSQL(
                        "INSERT INTO price_observations VALUES ('price','product','rice','Rice','1','kg',2500,2500,'Market','','','2026-10-01')"
                    )
                    db.execSQL("INSERT INTO shopping_lists VALUES ('list','Weekly shop')")
                    db.execSQL(
                        "INSERT INTO shopping_items VALUES ('item','list','Rice','2','rice',NULL,1,1)"
                    )
                    val binding = if (from == 1) "" else ",NULL"
                    db.execSQL(
                        "INSERT INTO commitments VALUES ('bill','bill','Internet','BILL',5000,'2026-10-10',NULL$binding)"
                    )
                }
                val migrations =
                    if (from == 1) arrayOf(YardDatabase.MIGRATION_1_2, YardDatabase.MIGRATION_2_3)
                    else arrayOf(YardDatabase.MIGRATION_2_3)
                helper.runMigrationsAndValidate(name, to, true, *migrations).use { db ->
                    db.query(
                            "SELECT openingMinor + (SELECT SUM(signedMinor) FROM entries WHERE accountId='cash') FROM accounts WHERE id='cash'"
                        )
                        .use { c ->
                            assertTrue(c.moveToFirst())
                            assertEquals(97500L, c.getLong(0))
                        }
                    db.query("SELECT rawText,totalMinor FROM receipts WHERE id='receipt'").use { c
                        ->
                        assertTrue(c.moveToFirst())
                        assertEquals("RICE 25.00", c.getString(0))
                        assertEquals(2500L, c.getLong(1))
                    }
                    db.query(
                            "SELECT checked,optional,manualPriceMinor FROM shopping_items WHERE id='item'"
                        )
                        .use { c ->
                            assertTrue(c.moveToFirst())
                            assertEquals(1, c.getInt(0))
                            assertEquals(1, c.getInt(1))
                            if (to == 3) assertEquals(2500L, c.getLong(2))
                            else assertTrue(c.isNull(2))
                        }
                    db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                }
            } finally {
                context.deleteDatabase(name)
            }
        }
    }
}
