package jm.yardmoney.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM category_rules ORDER BY createdAt DESC, id")
    suspend fun readCategoryRules(): List<CategoryRule>

    @Upsert suspend fun put(rule: CategoryRule)

    @Query("DELETE FROM category_rules WHERE id=:id")
    suspend fun deleteCategoryRule(id: String)

    @Query("SELECT * FROM goal_contributions")
    suspend fun readContributions(): List<GoalContribution>

    @Query("SELECT * FROM receipt_items") suspend fun readReceiptItems(): List<ReceiptItem>

    @Query("SELECT * FROM profile WHERE id=1") fun profile(): Flow<Profile?>

    @Query("SELECT * FROM profile WHERE id=1") suspend fun getProfile(): Profile?

    @Upsert suspend fun put(profile: Profile)

    @Query(
        "SELECT a.*, a.openingMinor + COALESCE((SELECT SUM(e.signedMinor) FROM entries e WHERE e.accountId=a.id),0) AS balanceMinor FROM accounts a ORDER BY a.name"
    )
    fun accounts(): Flow<List<AccountBalance>>

    @Query("SELECT * FROM accounts WHERE id=:id") suspend fun account(id: String): Account?

    @Query(
        "SELECT openingMinor+COALESCE((SELECT SUM(signedMinor) FROM entries WHERE accountId=:id),0) FROM accounts WHERE id=:id"
    )
    suspend fun accountBalance(id: String): Long

    @Query("SELECT COUNT(*) FROM accounts") suspend fun accountCount(): Int

    @Insert suspend fun insert(account: Account)

    @Query("SELECT * FROM transactions ORDER BY date DESC, rowid DESC")
    fun transactions(): Flow<List<MoneyTransaction>>

    @Query("SELECT * FROM transactions WHERE id=:id")
    suspend fun transaction(id: String): MoneyTransaction?

    @Query("SELECT * FROM transactions WHERE submissionKey=:key")
    suspend fun submitted(key: String): MoneyTransaction?

    @Query(
        "SELECT COALESCE(SUM(amountMinor),0) FROM transactions WHERE kind='REFUND' AND refundOfId=:id"
    )
    suspend fun refunded(id: String): Long

    @Query("SELECT COUNT(*) FROM transactions WHERE refundOfId=:id")
    suspend fun refundCount(id: String): Int

    @Insert suspend fun insert(transaction: MoneyTransaction)

    @Update suspend fun update(transaction: MoneyTransaction)

    @Update suspend fun updateSplits(splits: List<TransactionSplit>)

    @Query("SELECT * FROM splits WHERE transactionId=:id")
    suspend fun transactionSplits(id: String): List<TransactionSplit>

    @Query("SELECT * FROM category_limits ORDER BY category")
    fun categoryLimits(): Flow<List<CategoryLimit>>

    @Upsert suspend fun put(limit: CategoryLimit)

    @Query("DELETE FROM category_limits WHERE id=:id") suspend fun deleteCategoryLimit(id: String)

    @Query("SELECT * FROM entries") suspend fun readEntries(): List<AccountEntry>

    @Insert suspend fun insertEntries(entries: List<AccountEntry>)

    @Insert suspend fun insertSplits(splits: List<TransactionSplit>)

    @Query("SELECT * FROM splits") fun splits(): Flow<List<TransactionSplit>>

    @Query("DELETE FROM transactions WHERE id=:id") suspend fun deleteTransaction(id: String)

    @Query(
        "SELECT c.*, COALESCE((SELECT SUM(s.amountMinor) FROM settlements s WHERE s.commitmentId=c.id),0) AS fulfilledMinor FROM commitments c ORDER BY c.dueDate"
    )
    fun commitments(): Flow<List<CommitmentBalance>>

    @Query("SELECT * FROM commitments WHERE id=:id") suspend fun commitment(id: String): Commitment?

    @Query("SELECT COALESCE(SUM(amountMinor),0) FROM settlements WHERE commitmentId=:id")
    suspend fun settled(id: String): Long

    @Insert suspend fun insert(commitment: Commitment)

    @Update suspend fun update(commitment: Commitment)

    @Query("DELETE FROM commitments WHERE id=:id") suspend fun deleteCommitment(id: String)

    @Query("SELECT * FROM bill_templates WHERE active=1")
    suspend fun templates(): List<BillTemplate>

    @Query("SELECT * FROM bill_templates WHERE id=:id")
    suspend fun template(id: String): BillTemplate?

    @Insert suspend fun insert(template: BillTemplate)

    @Query("UPDATE bill_templates SET active=0 WHERE id=:id") suspend fun stopTemplate(id: String)

    @Query(
        "DELETE FROM commitments WHERE occurrenceKey LIKE :prefix AND NOT EXISTS (SELECT 1 FROM settlements WHERE commitmentId=commitments.id)"
    )
    suspend fun cancelUnpaidSeries(prefix: String)

    @Query("SELECT COUNT(*) FROM commitments WHERE occurrenceKey=:key")
    suspend fun occurrenceCount(key: String): Int

    @Query("SELECT occurrenceKey FROM commitments WHERE occurrenceKey LIKE :prefix")
    suspend fun occurrenceKeys(prefix: String): List<String>

    @Insert suspend fun insert(settlement: Settlement)

    @Query(
        "SELECT g.*, COALESCE((SELECT SUM(c.amountMinor) FROM goal_contributions c WHERE c.goalId=g.id),0) AS contributedMinor FROM goals g ORDER BY g.name"
    )
    fun goals(): Flow<List<GoalBalance>>

    @Query("SELECT * FROM goals WHERE id=:id") suspend fun goal(id: String): Goal?

    @Query("SELECT COALESCE(SUM(amountMinor),0) FROM goal_contributions WHERE goalId=:id")
    suspend fun contributed(id: String): Long

    @Update suspend fun update(account: Account)

    @Insert suspend fun insert(goal: Goal)

    @Insert suspend fun insert(contribution: GoalContribution)

    @Upsert suspend fun put(draft: ReceiptDraft)

    @Query("SELECT * FROM receipt_drafts ORDER BY createdDate DESC")
    fun drafts(): Flow<List<ReceiptDraft>>

    @Query("SELECT * FROM receipt_drafts WHERE id=:id") suspend fun draft(id: String): ReceiptDraft?

    @Query("DELETE FROM receipt_drafts WHERE id=:id") suspend fun deleteDraft(id: String)

    @Query("SELECT * FROM receipts ORDER BY date DESC") fun receipts(): Flow<List<Receipt>>

    @Query("SELECT * FROM receipt_items WHERE receiptId=:id")
    suspend fun receiptItems(id: String): List<ReceiptItem>

    @Query("DELETE FROM receipts WHERE id=:id") suspend fun deleteReceipt(id: String)

    @Query("SELECT * FROM receipts WHERE transactionId=:id")
    suspend fun transactionReceipts(id: String): List<Receipt>

    @Query(
        "SELECT * FROM receipts WHERE fingerprint=:fingerprint OR (date=:date AND totalMinor=:total)"
    )
    suspend fun duplicateCandidates(
        fingerprint: String,
        date: String,
        total: Long,
    ): List<Receipt>

    @Insert suspend fun insert(receipt: Receipt)

    @Insert suspend fun insertItems(items: List<ReceiptItem>)

    @Insert suspend fun insertObservations(observations: List<PriceObservation>)

    @Query("SELECT * FROM price_observations ORDER BY date DESC")
    fun prices(): Flow<List<PriceObservation>>

    @Query("SELECT * FROM shopping_lists ORDER BY name") fun lists(): Flow<List<ShoppingList>>

    @Query("SELECT * FROM shopping_items ORDER BY rowid")
    fun shoppingItems(): Flow<List<ShoppingItem>>

    @Insert suspend fun insert(list: ShoppingList)

    @Upsert suspend fun put(list: ShoppingList)

    @Query("SELECT * FROM shopping_lists WHERE id=:id")
    suspend fun shoppingList(id: String): ShoppingList?

    @Query("DELETE FROM shopping_lists WHERE id=:id") suspend fun deleteShoppingList(id: String)

    @Query("DELETE FROM shopping_items WHERE listId=:listId")
    suspend fun clearShoppingItems(listId: String)

    @Upsert suspend fun put(item: ShoppingItem)

    @Query("UPDATE shopping_items SET checked=:checked WHERE id=:id AND checked=:expected")
    suspend fun setShoppingChecked(id: String, checked: Boolean, expected: Boolean)

    @Query("DELETE FROM shopping_items WHERE id=:id") suspend fun deleteShoppingItem(id: String)

    @Query("SELECT * FROM profile WHERE id=1") suspend fun readProfile(): Profile?

    @Query(
        "SELECT a.*, a.openingMinor + COALESCE((SELECT SUM(e.signedMinor) FROM entries e WHERE e.accountId=a.id),0) AS balanceMinor FROM accounts a ORDER BY a.name"
    )
    suspend fun readAccounts(): List<AccountBalance>

    @Query("SELECT * FROM transactions ORDER BY date DESC, rowid DESC")
    suspend fun readTransactions(): List<MoneyTransaction>

    @Query("SELECT * FROM category_limits ORDER BY category")
    suspend fun readCategoryLimits(): List<CategoryLimit>

    @Query("SELECT * FROM splits") suspend fun readSplits(): List<TransactionSplit>

    @Query(
        "SELECT c.*, COALESCE((SELECT SUM(s.amountMinor) FROM settlements s WHERE s.commitmentId=c.id),0) AS fulfilledMinor FROM commitments c ORDER BY c.dueDate"
    )
    suspend fun readCommitments(): List<CommitmentBalance>

    @Query(
        "SELECT g.*, COALESCE((SELECT SUM(c.amountMinor) FROM goal_contributions c WHERE c.goalId=g.id),0) AS contributedMinor FROM goals g ORDER BY g.name"
    )
    suspend fun readGoals(): List<GoalBalance>

    @Query("SELECT * FROM receipt_drafts ORDER BY createdDate DESC")
    suspend fun readDrafts(): List<ReceiptDraft>

    @Query("SELECT * FROM receipts ORDER BY date DESC") suspend fun readReceipts(): List<Receipt>

    @Query("SELECT * FROM price_observations ORDER BY date DESC")
    suspend fun readPrices(): List<PriceObservation>

    @Query("SELECT * FROM shopping_lists ORDER BY name") suspend fun readLists(): List<ShoppingList>

    @Query("SELECT * FROM shopping_items ORDER BY rowid")
    suspend fun readShoppingItems(): List<ShoppingItem>
}
