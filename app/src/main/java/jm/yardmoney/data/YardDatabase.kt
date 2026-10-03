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
    version = 1,
    exportSchema = true,
)
abstract class YardDatabase : RoomDatabase() {
    abstract fun finance(): FinanceDao
}
