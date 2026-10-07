package jm.yardmoney.data

import androidx.test.platform.app.InstrumentationRegistry
import jm.yardmoney.YardMoneyApplication
import jm.yardmoney.core.*
import jm.yardmoney.security.PortableBackup
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StatementImportBackupTest {
    @Test fun versionFiveRetainsProvenanceAndVersionFourRestoresWithoutImportMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "jm.yardmoney.testhost")
        val app = context.applicationContext as YardMoneyApplication
        val repo = app.repository
        if (repo.dao.getProfile() == null) {
            val today = repo.today
            repo.onboard(Profile(name="Import backup",typicalNetMinor=0,frequency="MONTHLY",nextPayday=today.plusDays(14).toString(),
                anchorDay=20,secondDay=28,needsBp=5000,wantsBp=3000,savingsBp=2000,periodStart=today.toString(),budgetIncomeMinor=0),1000000)
        }
        val backup=PortableBackup(app)
        fun password()="statement-backup-test-password".toCharArray()
        val original=backup.export(password())
        try {
            val account=repo.dao.readAccounts().first().account.id
            val mapping=StatementMapping(0,1,2,dateFormat="yyyy-MM-dd")
            val document=StatementCsv.read(java.io.StringReader("date,description,amount\n${repo.today},Synthetic statement backup,12.34"))
            val rows=StatementCsv.preview(document,mapping,account,emptyList(),repo.today)
            val batch=StatementCsv.batchId(account,mapping,document)
            repo.importStatement(batch,account,mapping,rows)
            val current=backup.export(password())
            backup.restore(current,password())
            assertNotNull(repo.dao.importBatch(batch)); assertEquals(1,repo.dao.importRecords(batch).size)
            assertEquals(mapping,repo.dao.readImportMappings().single { it.accountId==account }.mapping())
            repo.undoStatementImport(batch); assertNull(repo.dao.importBatch(batch))
            val clear=BackupCipher.decrypt(current,password())
            val json=try { JSONObject(clear.toString(Charsets.UTF_8)) } finally { clear.fill(0) }
            json.put("version",4)
            listOf("import_batches","import_records","import_mappings").forEach { json.remove(it) }
            val legacyClear=json.toString().toByteArray(Charsets.UTF_8)
            val legacy=try { BackupCipher.encrypt(legacyClear,password()) } finally { legacyClear.fill(0) }
            backup.restore(legacy,password())
            assertTrue(repo.dao.readImportBatches().isEmpty()); assertTrue(repo.dao.readImportMappings().isEmpty())
            assertNotNull(repo.dao.readTransactions().find { it.description=="Synthetic statement backup" })
            legacy.fill(0); current.fill(0)
        } finally { backup.restore(original,password()); original.fill(0) }
    }
}
