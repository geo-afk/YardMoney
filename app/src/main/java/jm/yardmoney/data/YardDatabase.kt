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
            CategoryRule::class,
            ImportBatch::class,
            ImportRecord::class,
            ImportMapping::class,
        ],
    version = 5,
    exportSchema = true,
)
abstract class YardDatabase : RoomDatabase() {
    abstract fun finance(): FinanceDao

    companion object {
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS import_batches (id TEXT NOT NULL, accountId TEXT NOT NULL, createdAt INTEGER NOT NULL, rowCount INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_import_batches_accountId ON import_batches(accountId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS import_records (transactionId TEXT NOT NULL, batchId TEXT NOT NULL, rowHash TEXT NOT NULL, PRIMARY KEY(transactionId), FOREIGN KEY(batchId) REFERENCES import_batches(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(transactionId) REFERENCES transactions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_import_records_batchId_rowHash ON import_records(batchId,rowHash)")
                db.execSQL("CREATE TABLE IF NOT EXISTS import_mappings (accountId TEXT NOT NULL, dateColumn INTEGER NOT NULL, descriptionColumn INTEGER NOT NULL, amountColumn INTEGER NOT NULL, debitColumn INTEGER NOT NULL, creditColumn INTEGER NOT NULL, balanceColumn INTEGER NOT NULL, dateFormat TEXT NOT NULL, header INTEGER NOT NULL, PRIMARY KEY(accountId), FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS category_rules (id TEXT NOT NULL, pattern TEXT NOT NULL, matchType TEXT NOT NULL, category TEXT NOT NULL, bucket TEXT NOT NULL, accountId TEXT, createdAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_category_rules_accountId ON category_rules(accountId)")
            }
        }

        val MIGRATION_2_3 =
            object : androidx.room.migration.Migration(2, 3) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Unknown historic dates stay unknown; item prices and checklist state stay
                    // intact.
                    db.execSQL("ALTER TABLE shopping_lists ADD COLUMN createdDate TEXT")
                    // Preserve the explicitly selected receipt price for historic checklist items.
                    db.execSQL(
                        "UPDATE shopping_items SET manualPriceMinor=(SELECT packPriceMinor FROM price_observations p WHERE p.productKey=shopping_items.productKey ORDER BY p.date DESC,p.rowid DESC LIMIT 1) WHERE manualPriceMinor IS NULL AND productKey IS NOT NULL"
                    )
                    db.execSQL(
                        "ALTER TABLE shopping_items ADD COLUMN category TEXT NOT NULL DEFAULT 'Other'"
                    )
                    db.execSQL(
                        "ALTER TABLE shopping_items ADD COLUMN note TEXT NOT NULL DEFAULT ''"
                    )
                }
            }

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
