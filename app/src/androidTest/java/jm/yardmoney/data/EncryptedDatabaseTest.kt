package jm.yardmoney.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    @Test
    fun encryptedFileReopensWithCorrectKeyAndRejectsWrongKey() {
        runBlocking {
            System.loadLibrary("sqlcipher")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "yardmoney-test-${UUID.randomUUID()}.db"
            val password = ByteArray(32) { (it + 1).toByte() }
            fun open(key: ByteArray) =
                Room.databaseBuilder(context, YardDatabase::class.java, name)
                    .openHelperFactory(SupportOpenHelperFactory(key))
                    .build()
            try {
                val original = open(password.copyOf())
                try {
                    original.finance().insert(Account("cash", "Cash", "CASH", 12300, true))
                    assertEquals(12300L, original.finance().accountBalance("cash"))
                } finally {
                    original.close()
                }
                val prefix =
                    context.getDatabasePath(name).inputStream().use { input ->
                        ByteArray(16).also { input.read(it) }
                    }
                assertFalse(prefix.contentEquals("SQLite format 3\u0000".toByteArray()))
                val wrong = open(ByteArray(32) { 99 })
                var rejected = false
                try {
                    wrong.finance().readAccounts()
                } catch (_: Exception) {
                    rejected = true
                } finally {
                    wrong.close()
                }
                assertTrue("Wrong key must not open the database", rejected)
                val reopened = open(password.copyOf())
                try {
                    assertEquals(12300L, reopened.finance().accountBalance("cash"))
                } finally {
                    reopened.close()
                }
            } finally {
                context.deleteDatabase(name)
                password.fill(0)
            }
        }
    }
}
