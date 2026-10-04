package jm.yardmoney.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Test

class ShopMigrationTest {
    @Test
    fun schemaTwoShoppingItemsMigrateWithoutLosingTheirState() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "shop-migration-${System.nanoTime()}.db"
        val schema =
            JSONObject(
                    instrumentation.context.assets
                        .open("jm.yardmoney.data.YardDatabase/2.json")
                        .bufferedReader()
                        .use { it.readText() }
                )
                .getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                fun sql(text: String) =
                    text.replace(
                        36.toChar().toString() + "{TABLE_NAME}",
                        entity.getString("tableName"),
                    )
                old.execSQL(sql(entity.getString("createSql")))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (j in 0 until indices.length()) old.execSQL(
                    sql(indices.getJSONObject(j).getString("createSql"))
                )
            }
            old.execSQL(
                "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
            )
            old.execSQL(
                "INSERT INTO room_master_table VALUES(42,?)",
                arrayOf(schema.getString("identityHash")),
            )
            old.execSQL("INSERT INTO shopping_lists VALUES('trip','Weekly shop')")
            old.execSQL(
                "INSERT INTO shopping_items VALUES('milk','trip','Milk','2',NULL,15000,1,1)"
            )
            old.execSQL("INSERT INTO shopping_items VALUES('rice','trip','Rice','1',NULL,NULL,0,0)")
            old.execSQL(
                "INSERT INTO transactions VALUES('purchase','purchase','EXPENSE',1000,'2026-10-01','Trip','Groceries','NEEDS',NULL)"
            )
            old.execSQL(
                "INSERT INTO receipts VALUES('receipt','purchase','Market','','','2026-10-01',1000,0,'','fingerprint',NULL)"
            )
            old.execSQL(
                "INSERT INTO receipt_items VALUES('scanned','receipt','OATS','Oats','1',1000,'1','kg',1)"
            )
            old.execSQL(
                "INSERT INTO price_observations VALUES('price','scanned','oats-key','Oats','1','kg',1000,1000,'Market','','','2026-10-01')"
            )
            old.execSQL(
                "INSERT INTO shopping_items VALUES('oats','trip','Oats','1','oats-key',NULL,0,0)"
            )
            old.version = 2
        }
        val db =
            Room.databaseBuilder(context, YardDatabase::class.java, name)
                .addMigrations(YardDatabase.MIGRATION_2_3)
                .build()
        try {
            val repo = FinanceRepository(db)
            val list = repo.dao.readLists().single()
            val items = repo.dao.readShoppingItems()
            assertNull(list.createdDate)
            assertEquals(3, items.size)
            val milk = items.single { it.id == "milk" }
            assertEquals(15000L, milk.manualPriceMinor)
            assertTrue(milk.checked)
            assertTrue(milk.optional)
            assertEquals("Other", milk.category)
            assertEquals("", milk.note)
            assertNull(items.single { it.id == "rice" }.manualPriceMinor)
            assertEquals(1000L, items.single { it.id == "oats" }.manualPriceMinor)
            val updated = milk.copy(category = "Groceries", note = "Low fat")
            repo.saveShoppingList(
                list.copy(createdDate = "2026-10-03"),
                listOf(updated, items.single { it.id == "rice" }),
                true,
            )
            assertEquals("Low fat", repo.dao.readShoppingItems().single { it.id == "milk" }.note)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun stableDraftSaveDuplicateAndUndoAreAtomic() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, YardDatabase::class.java).build()
        try {
            val repo = FinanceRepository(db)
            val list = ShoppingList("list", "Trip", "2026-10-03")
            val item =
                ShoppingItem(
                    "one",
                    list.id,
                    "Milk",
                    "2",
                    null,
                    15000,
                    false,
                    true,
                    "Groceries",
                    "Low fat",
                )
            repo.saveShoppingList(list, listOf(item))
            repo.saveShoppingList(list, listOf(item))
            assertEquals(1, repo.dao.readLists().size)
            assertEquals(1, repo.dao.readShoppingItems().size)
            repo.duplicateShoppingList(list, listOf(item))
            val copy = repo.dao.readShoppingItems().single { it.id != item.id }
            assertFalse(copy.checked)
            assertEquals("Low fat", copy.note)
            assertEquals(15000L, copy.manualPriceMinor)
            repo.dao.deleteShoppingList(list.id)
            assertEquals(1, repo.dao.readShoppingItems().size)
            repo.restoreShoppingList(list, listOf(item))
            assertEquals(2, repo.dao.readLists().size)
            assertEquals(2, repo.dao.readShoppingItems().size)
        } finally {
            db.close()
        }
    }
}
