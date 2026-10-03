package jm.yardmoney.ui

import jm.yardmoney.data.MoneyTransaction
import jm.yardmoney.data.TransactionSplit
import org.junit.Assert.*
import org.junit.Test

class BucketActivityTest {
    private fun transaction(id: String, kind: String) =
        MoneyTransaction(id, id, kind, 1000, "2026-10-02", "Store", "Food", "NEEDS")

    @Test
    fun refundsReduceOnlyTheirRecordedCategoryAndBucket() {
        val rows =
            listOf(
                TransactionSplit("a", "expense", "Food", "NEEDS", 1000),
                TransactionSplit("b", "refund", "Food", "NEEDS", -250),
                TransactionSplit("c", "other", "Leisure", "WANTS", 500),
            )
        val tx =
            listOf(
                transaction("expense", "EXPENSE"),
                transaction("refund", "REFUND"),
                transaction("other", "EXPENSE"),
            )
        assertEquals(listOf("Food" to 750L), bucketCategories(rows, tx, "NEEDS"))
        assertEquals(listOf("Leisure" to 500L), bucketCategories(rows, tx, "WANTS"))
    }

    @Test
    fun transfersIncomeAndOutOfPeriodSplitsAreExcluded() {
        val rows =
            listOf(
                TransactionSplit("a", "transfer", "Goal", "SAVINGS", 1000),
                TransactionSplit("b", "income", "Pay", "SAVINGS", 1000),
                TransactionSplit("c", "older", "Food", "SAVINGS", 1000),
            )
        assertTrue(
            bucketCategories(
                    rows,
                    listOf(transaction("transfer", "TRANSFER"), transaction("income", "INCOME")),
                    "SAVINGS",
                )
                .isEmpty()
        )
    }

    @Test
    fun refundOnlyPeriodRetainsNegativeNetRatherThanHidingIt() {
        assertEquals(
            listOf("Food" to -250L),
            bucketCategories(
                listOf(TransactionSplit("a", "refund", "Food", "NEEDS", -250)),
                listOf(transaction("refund", "REFUND")),
                "NEEDS",
            ),
        )
    }
}
