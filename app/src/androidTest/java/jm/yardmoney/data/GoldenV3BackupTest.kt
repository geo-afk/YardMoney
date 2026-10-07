package jm.yardmoney.data

import androidx.test.platform.app.InstrumentationRegistry
import jm.yardmoney.YardMoneyApplication
import jm.yardmoney.security.PortableBackup
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
        } finally {
            backup.restore(original, "golden-original-password".toCharArray())
        }
    }
}
