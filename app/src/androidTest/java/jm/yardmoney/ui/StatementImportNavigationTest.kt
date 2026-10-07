package jm.yardmoney.ui

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import jm.yardmoney.*
import jm.yardmoney.data.Profile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StatementImportNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun sharedCsvIsReviewedRetainedOnRotationImportedAndUndone() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="jm.yardmoney.testhost")
        val app=context.applicationContext as YardMoneyApplication
        val repo=app.repository
        val prefs=app.appearancePrefs()
        val lock=prefs.getBoolean(Prefs.LOCK,false)
        prefs.edit().putBoolean(Prefs.LOCK,false).commit()
        if(repo.dao.getProfile()==null) {
            val today=repo.today
            repo.onboard(Profile(name="CSV UI test",typicalNetMinor=0,frequency="MONTHLY",nextPayday=today.plusDays(14).toString(),
                anchorDay=20,secondDay=28,needsBp=5000,wantsBp=3000,savingsBp=2000,periodStart=today.toString(),budgetIncomeMinor=0),1000000)
        }
        val account=repo.dao.readAccounts().first().account
        val beforeCount=repo.dao.readTransactions().size
        val beforeBalance=repo.dao.accountBalance(account.id)
        val suffix=java.util.UUID.randomUUID().toString().take(8)
        val file=java.io.File(app.cacheDir,"exports/statement-$suffix.csv")
        file.parentFile!!.mkdirs()
        file.writeText("date,description,amount\n${repo.today},Synthetic Taxi $suffix,-600\n${repo.today},Synthetic pay $suffix,1000",Charsets.UTF_8)
        val uri=FileProvider.getUriForFile(app,"${app.packageName}.files",file)
        try {
            val intent=Intent(context,MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/csv")
                .putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                compose.waitUntil(10000) { compose.onAllNodesWithText("Target account").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(beforeCount,repo.dao.readTransactions().size)
                compose.onNodeWithText("Target account").performScrollTo().performClick()
                compose.onAllNodesWithText(account.name).onLast().performClick()
                compose.onNodeWithText("Date format").performScrollTo().performClick()
                compose.onNodeWithText("yyyy-MM-dd").performClick()
                scenario.recreate()
                compose.waitUntil(10000) { compose.onAllNodesWithText("Preview rows").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Preview rows").performScrollTo().performClick()
                compose.onNodeWithText("Import 2 selected rows").assertExists()
                assertEquals(beforeCount,repo.dao.readTransactions().size)
                compose.onNodeWithText("Import 2 selected rows").performClick()
                compose.waitUntil(10000) { compose.onAllNodesWithText("2 records imported").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(beforeCount+2,repo.dao.readTransactions().size)
                assertEquals(beforeBalance+40000,repo.dao.accountBalance(account.id))
                compose.onNodeWithText("Undo import").performScrollTo().performClick()
                compose.waitUntil(10000) { compose.onAllNodesWithText("Import undone.").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(beforeCount,repo.dao.readTransactions().size)
                assertEquals(beforeBalance,repo.dao.accountBalance(account.id))
            }
        } finally { file.delete(); prefs.edit().putBoolean(Prefs.LOCK,lock).commit() }
    }
}
