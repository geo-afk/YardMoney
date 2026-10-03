package jm.yardmoney.data

import androidx.room.*

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val typicalNetMinor: Long,
    val frequency: String,
    val nextPayday: String,
    val anchorDay: Int,
    val secondDay: Int,
    val needsBp: Int,
    val wantsBp: Int,
    val savingsBp: Int,
    val periodStart: String,
    val budgetIncomeMinor: Long,
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val openingMinor: Long,
    val included: Boolean,
)

data class AccountBalance(@Embedded val account: Account, val balanceMinor: Long)

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["submissionKey"], unique = true), Index("date")],
)
data class MoneyTransaction(
    @PrimaryKey val id: String,
    val submissionKey: String,
    val kind: String,
    val amountMinor: Long,
    val date: String,
    val description: String,
    val category: String,
    val bucket: String,
    val refundOfId: String? = null,
)

@Entity(
    tableName = "entries",
    foreignKeys =
        [
            ForeignKey(
                entity = Account::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = MoneyTransaction::class,
                parentColumns = ["id"],
                childColumns = ["transactionId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("accountId"), Index("transactionId")],
)
data class AccountEntry(
    @PrimaryKey val id: String,
    val transactionId: String,
    val accountId: String,
    val signedMinor: Long,
)

@Entity(
    tableName = "splits",
    foreignKeys =
        [
            ForeignKey(
                entity = MoneyTransaction::class,
                parentColumns = ["id"],
                childColumns = ["transactionId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("transactionId")],
)
data class TransactionSplit(
    @PrimaryKey val id: String,
    val transactionId: String,
    val category: String,
    val bucket: String,
    val amountMinor: Long,
)

@Entity(
    tableName = "commitments",
    indices = [Index(value = ["occurrenceKey"], unique = true), Index("dueDate")],
)
data class Commitment(
    @PrimaryKey val id: String,
    val occurrenceKey: String,
    val name: String,
    val kind: String,
    val amountMinor: Long,
    val dueDate: String?,
    val goalId: String? = null,
)

@Entity(
    tableName = "settlements",
    foreignKeys =
        [
            ForeignKey(
                entity = Commitment::class,
                parentColumns = ["id"],
                childColumns = ["commitmentId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = MoneyTransaction::class,
                parentColumns = ["id"],
                childColumns = ["transactionId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("commitmentId"), Index("transactionId")],
)
data class Settlement(
    @PrimaryKey val id: String,
    val commitmentId: String,
    val transactionId: String,
    val amountMinor: Long,
)

data class CommitmentBalance(@Embedded val commitment: Commitment, val fulfilledMinor: Long) {
    val remainingMinor
        get() = commitment.amountMinor - fulfilledMinor
}

@Entity(tableName = "bill_templates")
data class BillTemplate(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val amountMinor: Long,
    val firstDate: String,
    val frequency: String,
    val anchorDay: Int,
    val active: Boolean = true,
)

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey val id: String,
    val name: String,
    val targetMinor: Long,
    val initialSavedMinor: Long,
)

@Entity(
    tableName = "goal_contributions",
    foreignKeys =
        [
            ForeignKey(
                entity = Goal::class,
                parentColumns = ["id"],
                childColumns = ["goalId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = MoneyTransaction::class,
                parentColumns = ["id"],
                childColumns = ["transactionId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("goalId"), Index("transactionId")],
)
data class GoalContribution(
    @PrimaryKey val id: String,
    val goalId: String,
    val transactionId: String,
    val amountMinor: Long,
)

data class GoalBalance(@Embedded val goal: Goal, val contributedMinor: Long) {
    val savedMinor
        get() = goal.initialSavedMinor + contributedMinor
}

@Entity(tableName = "receipt_drafts")
data class ReceiptDraft(
    @PrimaryKey val id: String,
    val rawText: String,
    val createdDate: String,
    val imageRef: String?,
    val fingerprint: String,
)

@Entity(
    tableName = "receipts",
    foreignKeys =
        [
            ForeignKey(
                entity = MoneyTransaction::class,
                parentColumns = ["id"],
                childColumns = ["transactionId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices =
        [
            Index(value = ["transactionId"], unique = true),
            Index("fingerprint"),
            Index(value = ["merchant", "date", "totalMinor"]),
        ],
)
data class Receipt(
    @PrimaryKey val id: String,
    val transactionId: String,
    val merchant: String,
    val branch: String,
    val parish: String,
    val date: String,
    val totalMinor: Long,
    val adjustmentMinor: Long,
    val rawText: String,
    val fingerprint: String,
    val imageRef: String?,
)

@Entity(
    tableName = "receipt_items",
    foreignKeys =
        [
            ForeignKey(
                entity = Receipt::class,
                parentColumns = ["id"],
                childColumns = ["receiptId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("receiptId")],
)
data class ReceiptItem(
    @PrimaryKey val id: String,
    val receiptId: String,
    val rawName: String,
    val confirmedName: String,
    val quantity: String,
    val totalMinor: Long,
    val packageSize: String,
    val unit: String,
    val confirmed: Boolean,
)

@Entity(
    tableName = "price_observations",
    foreignKeys =
        [
            ForeignKey(
                entity = ReceiptItem::class,
                parentColumns = ["id"],
                childColumns = ["itemId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index(value = ["itemId"], unique = true), Index(value = ["productKey", "date"])],
)
data class PriceObservation(
    @PrimaryKey val id: String,
    val itemId: String,
    val productKey: String,
    val name: String,
    val packageSize: String,
    val unit: String,
    val packPriceMinor: Long,
    val unitPriceMinor: Long,
    val merchant: String,
    val branch: String,
    val parish: String,
    val date: String,
)

@Entity(tableName = "shopping_lists")
data class ShoppingList(@PrimaryKey val id: String, val name: String)

@Entity(
    tableName = "shopping_items",
    foreignKeys =
        [
            ForeignKey(
                entity = ShoppingList::class,
                parentColumns = ["id"],
                childColumns = ["listId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("listId")],
)
data class ShoppingItem(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    val quantity: String,
    val productKey: String?,
    val manualPriceMinor: Long?,
    val optional: Boolean,
    val checked: Boolean = false,
)

@Entity(tableName = "category_limits")
data class CategoryLimit(
    @PrimaryKey val id: String,
    val category: String,
    val bucket: String,
    val limitMinor: Long,
)
