package jm.yardmoney.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AccountMigrationTest {
    @Test
    fun schemaOneRecordsSurviveAndCanBeAssignedAfterMigration() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "account-migration-" + System.nanoTime() + ".db"
        val schema =
            JSONObject(
                    instrumentation.context.assets
                        .open("jm.yardmoney.data.YardDatabase/1.json")
                        .bufferedReader()
                        .use { it.readText() }
                )
                .getJSONObject("database")
        val old = context.openOrCreateDatabase(name, 0, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                old.execSQL(
                    entity
                        .getString("createSql")
                        .replace(
                            (36.toChar().toString() + "{TABLE_NAME}"),
                            entity.getString("tableName"),
                        )
                )
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (i in 0 until indices.length()) old.execSQL(
                    indices
                        .getJSONObject(i)
                        .getString("createSql")
                        .replace(
                            (36.toChar().toString() + "{TABLE_NAME}"),
                            entity.getString("tableName"),
                        )
                )
            }
            old.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
            )
            old.execSQL(
                "INSERT INTO room_master_table (id,identity_hash) VALUES (42,?)",
                arrayOf(schema.getString("identityHash")),
            )
            old.execSQL("INSERT INTO accounts VALUES ('cash','Cash','CASH',10000,1)")
            old.execSQL(
                "INSERT INTO commitments VALUES ('bill','bill','Internet','BILL',5000,'2026-10-10',NULL)"
            )
            old.version = 1
        } finally {
            old.close()
        }
        val db =
            Room.databaseBuilder(context, YardDatabase::class.java, name)
                .addMigrations(YardDatabase.MIGRATION_1_2)
                .build()
        try {
            val repo = FinanceRepository(db)
            assertEquals(10000L, repo.dao.accountBalance("cash"))
            assertNull(repo.dao.commitment("bill")!!.accountId)
            repo.editCommitment("bill", "Internet", 5000, null, "cash")
            assertEquals("cash", repo.dao.commitment("bill")!!.accountId)
            repo.setCategoryLimit("Groceries", "NEEDS", 2000)
            repo.setCategoryLimit("Dining", "WANTS", 3000, "cash", "NEEDS:groceries")
            val limits = repo.dao.readCategoryLimits()
            assertEquals(1, limits.size)
            assertEquals("cash:WANTS:dining", limits.single().id)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
