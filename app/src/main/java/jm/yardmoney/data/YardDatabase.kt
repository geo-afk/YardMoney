package jm.yardmoney.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities =
        [
            Profile::class,
            Account::class,
            MoneyTransaction::class,
            AccountEntry::class,
            TransactionSplit::class,
            Commitment::class,
            Settlement::class,
            Goal::class,
            GoalContribution::class,
            ReceiptDraft::class,
            Receipt::class,
            ReceiptItem::class,
            PriceObservation::class,
            ShoppingList::class,
            ShoppingItem::class,
            BillTemplate::class,
            CategoryLimit::class,
        ],
    version = 2,
    exportSchema = true,
)
abstract class YardDatabase : RoomDatabase() {
    abstract fun finance(): FinanceDao

    companion object {
        val MIGRATION_1_2 =
            object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Existing reservations remain unassigned until the user chooses a funding
                    // account.
                    listOf("commitments", "bill_templates", "category_limits").forEach {
                        db.execSQL("ALTER TABLE $it ADD COLUMN accountId TEXT")
                    }
                }
            }
    }
}
