package jm.yardmoney.data

import java.time.LocalDate

// Repository-level state and input models. Room entities live in Entities.kt.

data class LedgerState(
    val profile: Profile?,
    val accounts: List<AccountBalance>,
    val transactions: List<MoneyTransaction>,
    val commitments: List<CommitmentBalance>,
    val goals: List<GoalBalance>,
)

data class ReceiptState(
    val drafts: List<ReceiptDraft>,
    val receipts: List<Receipt>,
    val prices: List<PriceObservation>,
)

data class ShoppingState(val lists: List<ShoppingList>, val items: List<ShoppingItem>)

data class FinanceSnapshot(
    val ledger: LedgerState,
    val receipt: ReceiptState,
    val shopping: ShoppingState,
    val splits: List<TransactionSplit>,
    val limits: List<CategoryLimit>,
    val accountEntries: List<AccountEntry> = emptyList(),
    val contributions: List<GoalContribution> = emptyList(),
    val receiptItems: List<ReceiptItem> = emptyList(),
    val savingsAccountIds: Set<String>? = null,
    val categoryRules: List<CategoryRule> = emptyList(),
)

data class TransactionInput(
    val key: String,
    val kind: String,
    val amountMinor: Long,
    val date: LocalDate,
    val description: String,
    val category: String,
    val bucket: String,
    val accountId: String,
    val toAccountId: String? = null,
    val commitmentId: String? = null,
    val goalId: String? = null,
    val refundOfId: String? = null,
    val splits: List<Pair<String, Long>> = emptyList(),
)

data class ConfirmedItem(
    val raw: String,
    val name: String,
    val quantity: String,
    val totalMinor: Long,
    val size: String,
    val unit: String,
    val verified: Boolean,
)

data class ConfirmedReceipt(
    val draftId: String,
    val merchant: String,
    val branch: String,
    val parish: String,
    val date: LocalDate,
    val totalMinor: Long,
    val adjustmentMinor: Long,
    val items: List<ConfirmedItem>,
    val accountId: String,
    val submissionKey: String,
    val totalOnly: Boolean,
    val allowDuplicate: Boolean = false,
    val existingTransactionId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)
