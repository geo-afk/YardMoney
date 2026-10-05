package jm.yardmoney.demo

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import androidx.room.withTransaction
import jm.yardmoney.YardMoneyApplication
import jm.yardmoney.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Explicit development installer, never discovered as a regular test or shipped in the app. */
class DemoDataInstaller : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        super.onStart()
        Thread {
            val result = Bundle()
            try {
                check(targetContext.packageName == "jm.yardmoney.demo") {
                    "Dummy records may only be installed into the separate demo application."
                }
                val app = targetContext.applicationContext as YardMoneyApplication
                runBlocking {
                    app.database.withTransaction {
                        // Never overwrite an existing demo profile or duplicate its records on
                        // retry.
                        if (app.repository.dao.getProfile() == null) seed(app.repository)
                    }
                    val data = app.repository.snapshot.first()
                    check(data.ledger.transactions.size >= 400) {
                        "Expected at least 400 demo transactions; found ${data.ledger.transactions.size}."
                    }
                    // Additional manual demo receipts are preserved on repeat installs.
                    check(data.receipt.receipts.size >= 18) {
                        "Expected at least 18 demo receipts; found ${data.receipt.receipts.size}."
                    }
                    result.putString(
                        "summary",
                        "Loaded " +
                            data.ledger.transactions.size +
                            " transactions, " +
                            data.ledger.accounts.size +
                            " accounts, " +
                            data.ledger.commitments.size +
                            " scheduled reservations, " +
                            data.ledger.goals.size +
                            " goals, " +
                            data.receipt.receipts.size +
                            " receipts and " +
                            data.shopping.items.size +
                            " shopping items.",
                    )
                }
                finish(Activity.RESULT_OK, result)
            } catch (error: Throwable) {
                result.putString("error", error.toString())
                finish(Activity.RESULT_CANCELED, result)
            }
        }
            .start()
    }

    private suspend fun seed(repo: FinanceRepository) {
        val today = repo.today
        repo.onboard(
            Profile(
                name = "Demo household",
                typicalNetMinor = 12000000,
                frequency = "MONTHLY",
                nextPayday = today.plusDays(14).toString(),
                anchorDay = 15,
                secondDay = 28,
                needsBp = 5000,
                wantsBp = 3000,
                savingsBp = 2000,
                periodStart = today.minusDays(13).toString(),
                budgetIncomeMinor = 0,
            ),
            2500000,
        )
        repo.addAccount("Demo current account", "CURRENT", 5000000, true)
        repo.addAccount("Demo savings", "SAVINGS", 8000000, false)
        repo.addAccount("Demo mobile wallet", "WALLET", 1000000, true)
        val accounts = repo.dao.readAccounts().map { it.account }
        val cash = accounts.first { it.kind == "CASH" }.id
        val bank = accounts.first { it.kind == "CURRENT" }.id
        val savings = accounts.first { it.kind == "SAVINGS" }.id
        val wallet = accounts.first { it.kind == "WALLET" }.id
        repo.addGoal("Demo emergency fund", 30000000, 8000000)
        repo.addGoal("Demo holiday", 15000000, 2500000)
        repo.addGoal("Demo new laptop", 18000000, 6000000)
        val goal = repo.dao.readGoals().first { it.goal.name == "Demo emergency fund" }.goal.id
        val needs = listOf("Groceries", "Transport", "Utilities", "Health", "Household")
        val wants = listOf("Dining", "Entertainment", "Clothing", "Hobbies", "Coffee")
        for (month in 0 until 6) {
            val date = today.minusDays(month * 30L)
            repo.post(
                TransactionInput(
                    "demo-salary-$month",
                    "INCOME",
                    12000000,
                    date,
                    "Demo monthly salary",
                    "Salary",
                    "NEEDS",
                    bank,
                )
            )
            repo.post(
                TransactionInput(
                    "demo-gig-$month",
                    "INCOME",
                    1800000 + month * 10000L,
                    date,
                    "Demo freelance work",
                    "Freelance",
                    "NEEDS",
                    bank,
                )
            )
        }
        for (month in 0 until 6) {
            val date = today.minusDays(month * 30L)
            repo.post(
                TransactionInput(
                    "demo-cash-fund-$month",
                    "TRANSFER",
                    3500000,
                    date,
                    "Demo cash withdrawal",
                    "Transfer",
                    "NEEDS",
                    bank,
                    toAccountId = cash,
                )
            )
            repo.post(
                TransactionInput(
                    "demo-wallet-fund-$month",
                    "TRANSFER",
                    2000000,
                    date,
                    "Demo wallet top-up",
                    "Transfer",
                    "NEEDS",
                    bank,
                    toAccountId = wallet,
                )
            )
        }
        for (day in 179 downTo 0) {
            val date = today.minusDays(day.toLong())
            val amount = 45000L + day % 9 * 12500L
            val expense =
                repo.post(
                    TransactionInput(
                        "demo-needs-$day",
                        "EXPENSE",
                        amount,
                        date,
                        "Demo " + needs[day % needs.size] + " purchase",
                        needs[day % needs.size],
                        "NEEDS",
                        cash,
                    )
                )
            repo.post(
                TransactionInput(
                    "demo-wants-$day",
                    "EXPENSE",
                    22000L + day % 7 * 9000L,
                    date,
                    "Demo " + wants[day % wants.size],
                    wants[day % wants.size],
                    "WANTS",
                    wallet,
                )
            )
            if (day % 15 == 0)
                repo.post(
                    TransactionInput(
                        "demo-refund-$day",
                        "REFUND",
                        amount / 3,
                        date,
                        "Demo partial refund",
                        needs[day % needs.size],
                        "NEEDS",
                        cash,
                        refundOfId = expense,
                    )
                )
            if (day % 14 == 0)
                repo.post(
                    TransactionInput(
                        "demo-save-$day",
                        "TRANSFER",
                        450000,
                        date,
                        "Demo emergency savings deposit",
                        "Savings",
                        "SAVINGS",
                        bank,
                        toAccountId = savings,
                        goalId = goal,
                    )
                )
            if (day % 30 == 0)
                repo.post(
                    TransactionInput(
                        "demo-adjust-$day",
                        "ADJUSTMENT",
                        if (day % 60 == 0) 12500 else -7500,
                        date,
                        "Demo balance reconciliation",
                        "Adjustment",
                        "NEEDS",
                        cash,
                    )
                )
        }
        val bills =
            listOf(
                "Demo rent" to 3800000L,
                "Demo electricity" to 850000L,
                "Demo internet" to 650000L,
                "Demo water" to 280000L,
            )
        bills.forEachIndexed { index, bill ->
            repo.addCommitment(
                bill.first,
                "BILL",
                bill.second,
                today.plusDays(index * 3L + 2),
                frequency = "MONTHLY",
                submissionKey = "demo-bill-$index",
            )
        }
        repo.addCommitment(
            "Demo overdue phone bill",
            "BILL",
            320000,
            today.minusDays(2),
            submissionKey = "demo-overdue",
        )
        repo.addCommitment(
            "Demo school supplies",
            "RESERVE",
            750000,
            today.plusDays(7),
            submissionKey = "demo-school",
        )
        repo.addCommitment(
            "Demo loan payment",
            "DEBT",
            1200000,
            today.plusDays(9),
            submissionKey = "demo-debt",
        )
        repo.addCommitment(
            "Demo next savings deposit",
            "SAVINGS",
            600000,
            today.plusDays(10),
            goalId = goal,
            submissionKey = "demo-reserve-savings",
        )
        repo.post(
            TransactionInput(
                "demo-partial-bill",
                "EXPENSE",
                100000,
                today,
                "Demo partial phone payment",
                "Utilities",
                "NEEDS",
                bank,
                commitmentId = "demo-overdue",
            )
        )
        needs.forEach { repo.setCategoryLimit(it, "NEEDS", 1500000) }
        wants.forEach { repo.setCategoryLimit(it, "WANTS", 650000) }
        for (index in 0 until 18) {
            val date = today.minusDays(index * 9L)
            val raw = "FICTIONAL DEMO RECEIPT $index\nRice, milk and bread"
            val draft = repo.saveDraft(raw, null, "demo-receipt-$index")
            val rice = 98000L + index * 1000
            val milk = 52000L + index * 500
            val bread = 39000L + index * 300
            repo.confirmReceipt(
                ConfirmedReceipt(
                    draft,
                    "Demo Market " + (index % 3 + 1),
                    "Demo branch",
                    "Kingston",
                    date,
                    rice + milk + bread,
                    0,
                    listOf(
                        ConfirmedItem("Rice", "Demo rice", "1", rice, "1", "kg", true),
                        ConfirmedItem("Milk", "Demo milk", "1", milk, "1", "l", true),
                        ConfirmedItem("Bread", "Demo bread", "1", bread, "500", "g", true),
                    ),
                    cash,
                    "demo-receipt-post-$index",
                    false,
                )
            )
        }
        for (index in 0 until 2) repo.saveDraft(
            "DEMO draft awaiting review $index",
            null,
            "demo-draft-$index",
        )
        for (name in
            listOf("Demo weekly groceries", "Demo household restock", "Demo weekend picnic")) {
            repo.createList(name)
            val list = repo.dao.readLists().first { it.name == name }
            for ((index, item) in
                listOf("Rice", "Milk", "Bread", "Eggs", "Fruit", "Soap", "Juice", "Snacks")
                    .withIndex()) {
                repo.addListItem(
                    list.id,
                    "Demo $item",
                    if (index % 3 == 0) "2" else "1",
                    null,
                    35000L + index * 10000,
                    index >= 6,
                )
            }
        }
        val items = repo.dao.readShoppingItems()
        items
            .filterIndexed { index, _ -> index % 4 == 0 }
            .forEach { repo.dao.setShoppingChecked(it.id, !it.checked, it.checked) }
    }
}
