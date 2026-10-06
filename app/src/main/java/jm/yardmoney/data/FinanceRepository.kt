package jm.yardmoney.data

import androidx.room.withTransaction
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import jm.yardmoney.core.*
import kotlinx.coroutines.flow.*

class FinanceRepository(private val db: YardDatabase) {
    val dao = db.finance()
    val today
        get() = jamaicaToday()

    // Shopping edits (a checkbox tap) are frequent and independent of the ledger, so they are
    // read on their own instead of reloading every table. conflate(): when writes arrive faster
    // than a read finishes, only the newest state is read.
    private val shoppingState: Flow<ShoppingState> =
        db.invalidationTracker
            .createFlow("shopping_lists", "shopping_items")
            .conflate()
            .map {
                db.withTransaction { ShoppingState(dao.readLists(), dao.readShoppingItems()) }
            }

    private val ledgerSnapshot: Flow<FinanceSnapshot> =
        db.invalidationTracker
            .createFlow(
                "profile",
                "accounts",
                "transactions",
                "entries",
                "splits",
                "commitments",
                "settlements",
                "goals",
                "goal_contributions",
                "receipt_drafts",
                "receipts",
                "receipt_items",
                "price_observations",
                "bill_templates",
                "category_limits",
            )
            .conflate()
            .map {
                // One committed revision prevents a payment from briefly being counted twice in the
                // UI.
                db.withTransaction {
                    FinanceSnapshot(
                        LedgerState(
                            dao.getProfile(),
                            dao.readAccounts(),
                            dao.readTransactions(),
                            dao.readCommitments(),
                            dao.readGoals(),
                        ),
                        ReceiptState(dao.readDrafts(), dao.readReceipts(), dao.readPrices()),
                        ShoppingState(emptyList(), emptyList()), // replaced by shoppingState below
                        dao.readSplits(),
                        dao.readCategoryLimits(),
                        dao.readEntries(),
                        dao.readContributions(),
                        dao.readReceiptItems(),
                    )
                }
            }

    val snapshot: Flow<FinanceSnapshot> =
        combine(ledgerSnapshot, shoppingState) { ledger: FinanceSnapshot, shopping: ShoppingState ->
            ledger.copy(shopping = shopping)
        }

    suspend fun onboard(profile: Profile, openingMinor: Long) = db.withTransaction {
        require(dao.getProfile() == null) { "Setup is already complete." }
        validateProfile(profile)
        require(openingMinor in 0..Money.MAX_MINOR) { "Enter your starting cash as zero or more." }
        dao.put(profile)
        dao.insert(Account(id(), "Cash", "CASH", openingMinor, true))
    }

    private fun validateProfile(p: Profile) {
        require(p.typicalNetMinor in 0..Money.MAX_MINOR) {
            "Enter your typical take-home pay as zero or more."
        }
        BudgetSplit(p.needsBp, p.wantsBp, p.savingsBp)
        require(LocalDate.parse(p.nextPayday) in today..today.plusYears(5)) {
            "Choose a planning payday within the next five years."
        }
        LocalDate.parse(p.periodStart)
        PayFrequency.valueOf(p.frequency)
        require(p.anchorDay in 1..31 && p.secondDay in 1..31) {
            "Pay days must be between 1 and 31."
        }
        if (p.frequency == "TWICE_MONTHLY")
            require(p.anchorDay < p.secondDay) { "The second pay day must come after the first." }
    }

    suspend fun updateSplit(split: BudgetSplit) {
        val p = dao.getProfile() ?: error("Complete setup first.")
        dao.put(p.copy(needsBp = split.needs, wantsBp = split.wants, savingsBp = split.savings))
    }

    suspend fun confirmPayday(newDate: LocalDate, receivedBudgetIncome: Long) {
        val p = dao.getProfile() ?: error("Complete setup first.")
        require(
            newDate > today &&
                newDate <= today.plusYears(5) &&
                receivedBudgetIncome in 0..Money.MAX_MINOR
        ) {
            "Choose a future payday within five years."
        }
        dao.put(
            p.copy(
                nextPayday = newDate.toString(),
                periodStart = today.toString(),
                budgetIncomeMinor = receivedBudgetIncome,
            )
        )
        materializeBills()
    }

    suspend fun addAccount(name: String, kind: String, opening: Long, included: Boolean) {
        require(name.isNotBlank()) { "Enter an account name." }
        require(opening in -Money.MAX_MINOR..Money.MAX_MINOR) { "Enter a smaller opening balance." }
        require(kind in setOf("CASH", "CURRENT", "SAVINGS", "WALLET"))
        require(dao.accountCount() < 1000) { "The pilot supports up to 1,000 accounts." }
        dao.insert(Account(id(), name.trim().take(100), kind, opening, included))
    }

    suspend fun post(input: TransactionInput): String = db.withTransaction { postInside(input) }

    private suspend fun postInside(i: TransactionInput): String {
        dao.submitted(i.key)?.let {
            return it.id
        }
        require(i.kind in setOf("EXPENSE", "INCOME", "TRANSFER", "REFUND", "ADJUSTMENT"))
        require(i.amountMinor in -Money.MAX_MINOR..Money.MAX_MINOR && i.amountMinor != 0L) {
            "Enter an amount that is not zero and not too large."
        }
        if (i.kind != "ADJUSTMENT") require(i.amountMinor > 0) { "Enter an amount above zero." }
        require(dao.account(i.accountId) != null) { "Choose a valid account." }
        require(i.bucket in setOf("NEEDS", "WANTS", "SAVINGS"))
        require(i.date <= today) {
            "Record received money or purchases on today or an earlier date. Use a bill reservation for future spending."
        }
        val transfer = i.kind == "TRANSFER"
        if (transfer) {
            require(i.toAccountId != null && i.toAccountId != i.accountId) {
                "Choose a different account to transfer to."
            }
            require(dao.account(i.toAccountId) != null) { "Choose a valid account to transfer to." }
        }
        if (i.kind == "REFUND" && i.refundOfId != null) {
            val original = dao.transaction(i.refundOfId) ?: error("Original expense not found.")
            require(original.kind == "EXPENSE") { "A refund must refer to an expense." }
            require(i.bucket == original.bucket) { "Use the original expense's budget group." }
            require(i.amountMinor <= original.amountMinor - dao.refunded(original.id)) {
                "Refunds cannot exceed the original expense."
            }
        }
        val tx = id()
        dao.insert(
            MoneyTransaction(
                tx,
                i.key,
                i.kind,
                i.amountMinor,
                i.date.toString(),
                i.description.trim().take(240),
                i.category,
                i.bucket,
                i.refundOfId,
            )
        )
        val signed = if (i.kind == "EXPENSE" || transfer) -i.amountMinor else i.amountMinor
        require(
            Math.addExact(dao.accountBalance(i.accountId), signed) in
                -Money.MAX_MINOR..Money.MAX_MINOR
        ) {
            "This account would exceed the supported balance range."
        }
        if (transfer)
            require(
                Math.addExact(dao.accountBalance(i.toAccountId!!), i.amountMinor) in
                    -Money.MAX_MINOR..Money.MAX_MINOR
            ) {
                "The destination balance would exceed the supported range."
            }
        val entries = mutableListOf(AccountEntry(id(), tx, i.accountId, signed))
        if (transfer) entries += AccountEntry(id(), tx, i.toAccountId!!, i.amountMinor)
        dao.insertEntries(entries)
        if (i.kind == "EXPENSE" || i.kind == "REFUND") {
            val splits = if (i.splits.isEmpty()) listOf(i.category to i.amountMinor) else i.splits
            require(splits.all { it.first.isNotBlank() && it.second > 0 })
            require(Money.sum(splits.map { it.second }) == i.amountMinor)
            dao.insertSplits(
                splits.map {
                    TransactionSplit(
                        id(),
                        tx,
                        it.first,
                        i.bucket,
                        if (i.kind == "REFUND") -it.second else it.second,
                    )
                }
            )
        }
        i.commitmentId?.let { cid ->
            require(i.kind == "EXPENSE" || transfer)
            val c = dao.commitment(cid) ?: error("Commitment not found.")
            if (c.kind == "SAVINGS") require(transfer) { "Move savings to a protected account." }
            if (c.kind == "BILL" || c.kind == "DEBT")
                require(i.kind == "EXPENSE") {
                    "Bill and debt payments must be recorded as spending."
                }
            if (transfer)
                require(!dao.account(i.toAccountId!!)!!.included) {
                    "Use a protected destination before releasing this reservation."
                }
            val remaining = c.amountMinor - dao.settled(cid)
            require(i.amountMinor <= remaining) {
                "Payment exceeds this commitment. Record extra spending separately."
            }
            dao.insert(Settlement(id(), cid, tx, i.amountMinor))
        }
        i.goalId?.let { gid ->
            require(transfer)
            val goal = dao.goal(gid) ?: error("Goal not found.")
            val source = dao.account(i.accountId)!!
            val destination = dao.account(i.toAccountId!!)!!
            require(source.included != destination.included) {
                "Link a goal when moving between spendable and protected accounts."
            }
            val contribution = if (destination.included) -i.amountMinor else i.amountMinor
            require(goal.initialSavedMinor + dao.contributed(gid) + contribution >= 0) {
                "Withdrawal exceeds the recorded goal savings."
            }
            dao.insert(GoalContribution(id(), gid, tx, contribution))
        }
        return tx
    }

    suspend fun deleteTransaction(id: String) = db.withTransaction {
        require(dao.refundCount(id) == 0) {
            "Remove linked refunds before deleting their original expense."
        }
        dao.deleteTransaction(id)
        require(dao.readGoals().all { it.savedMinor >= 0 }) {
            "Remove linked savings withdrawals before deleting this contribution."
        }
    }

    suspend fun addCommitment(
        name: String,
        kind: String,
        amount: Long,
        dueDate: LocalDate?,
        goalId: String? = null,
        frequency: String = "ONCE",
        submissionKey: String = id(),
        accountId: String? = null,
    ) = db.withTransaction {
        require(accountId == null || dao.account(accountId) != null) {
            "Choose a valid funding account."
        }
        require(submissionKey.isNotBlank() && submissionKey.length <= 120)
        if (dao.commitment(submissionKey) != null || dao.template(submissionKey) != null)
            return@withTransaction
        require(name.isNotBlank() && amount in 1..Money.MAX_MINOR) {
            "Enter a name and an amount above zero."
        }
        require(kind in setOf("BILL", "SAVINGS", "DEBT", "RESERVE"))
        if (frequency == "ONCE")
            dao.insert(
                Commitment(
                    submissionKey,
                    submissionKey,
                    name.trim().take(120),
                    kind,
                    amount,
                    dueDate?.toString(),
                    goalId,
                    accountId,
                )
            )
        else {
            require(dueDate != null && dueDate >= today.minusYears(1)) {
                "Recurring bills need a recent or future due date."
            }
            require(frequency in setOf("WEEKLY", "FORTNIGHTLY", "MONTHLY"))
            dao.insert(
                BillTemplate(
                    submissionKey,
                    name.trim().take(120),
                    kind,
                    amount,
                    dueDate.toString(),
                    frequency,
                    dueDate.dayOfMonth,
                    accountId = accountId,
                )
            )
            materializeBills()
        }
    }

    suspend fun materializeBills() = db.withTransaction {
        val p = dao.getProfile() ?: return@withTransaction
        val horizon = maxOf(today.plusMonths(2), LocalDate.parse(p.nextPayday))
        require(horizon <= today.plusYears(5))
        dao.templates().forEach { template ->
            // One lookup per series instead of one query per occurrence: a weekly bill started
            // years ago would otherwise cost hundreds of queries on every launch.
            val existing = dao.occurrenceKeys("${template.id}@%").toHashSet()
            var date = LocalDate.parse(template.firstDate)
            var count = 0
            while (date <= horizon || count == 0) {
                require(count++ < 3000)
                val occurrence = "${template.id}@$date"
                if (existing.add(occurrence))
                    dao.insert(
                        Commitment(
                            id(),
                            occurrence,
                            template.name,
                            template.kind,
                            template.amountMinor,
                            date.toString(),
                            accountId = template.accountId,
                        )
                    )
                date =
                    PaySchedule.nextAfter(
                        date,
                        PayFrequency.valueOf(template.frequency),
                        template.anchorDay,
                    )!!
            }
        }
    }

    suspend fun editCommitment(
        id: String,
        name: String,
        amount: Long,
        due: LocalDate?,
        accountId: String? = null,
    ) = db.withTransaction {
        val c = dao.commitment(id) ?: error("Reservation not found.")
        require(name.isNotBlank() && amount in 1..Money.MAX_MINOR && amount >= dao.settled(id)) {
            "Amount cannot be below payments already made."
        }
        require(accountId == null || dao.account(accountId) != null) { "Choose a valid account." }
        dao.update(
            c.copy(
                name = name.trim(),
                amountMinor = amount,
                dueDate = due?.toString(),
                accountId = accountId,
            )
        )
    }

    suspend fun removeCommitment(id: String) = db.withTransaction {
        val c = dao.commitment(id) ?: error("Reservation not found.")
        require(dao.settled(id) == 0L) {
            "Paid reservations are retained with their payment history."
        }
        dao.deleteCommitment(id)
        if (c.occurrenceKey.contains('@')) {
            val template = c.occurrenceKey.substringBefore('@')
            dao.stopTemplate(template)
            dao.cancelUnpaidSeries("$template@%")
        }
    }

    suspend fun addGoal(name: String, target: Long, saved: Long) {
        require(
            name.isNotBlank() &&
                target > 0 &&
                saved >= 0 &&
                target <= Money.MAX_MINOR &&
                saved <= Money.MAX_MINOR
        )
        dao.insert(Goal(id(), name.trim().take(120), target, saved))
    }

    suspend fun saveDraft(raw: String, imageRef: String?, fingerprint: String): String {
        require(raw.length <= 300_000)
        val key = id()
        dao.put(ReceiptDraft(key, raw, today.toString(), imageRef, fingerprint))
        return key
    }

    suspend fun saveScannedDraft(raw: String, fingerprint: String): String = db.withTransaction {
        val key = saveDraft(raw, null, fingerprint)
        saveReviewDraft(key, ReceiptDraftCodec.encode(raw))
        key
    }

    suspend fun duplicates(r: ConfirmedReceipt): List<Receipt> {
        val d = dao.draft(r.draftId) ?: error("Draft not found.")
        return dao.duplicateCandidates(d.fingerprint, r.date.toString(), r.totalMinor).filter {
            it.fingerprint == d.fingerprint ||
                it.merchant.equals(r.merchant.trim(), ignoreCase = true)
        }
    }

    suspend fun saveReviewDraft(draftId: String, reviewJson: String) = db.withTransaction {
        val draft = dao.draft(draftId) ?: error("Draft no longer exists.")
        val raw = draft.rawText.substringBefore("\n\n[Review edits]\n")
        val updated = raw + "\n\n[Review edits]\n" + reviewJson
        require(updated.length <= 300_000) {
            "This draft is too large. Reduce the number or length of item notes."
        }
        dao.put(draft.copy(rawText = updated))
    }

    suspend fun confirmReceipt(r: ConfirmedReceipt): String = db.withTransaction {
        // A double-tap retry resolves by its transaction key even after the draft was consumed.
        dao.submitted(r.submissionKey)?.let {
            return@withTransaction it.id
        }
        val draft = dao.draft(r.draftId) ?: error("Draft no longer exists.")
        require(r.merchant.isNotBlank() && r.totalMinor in 1..Money.MAX_MINOR) {
            "Enter the store name and a total above zero."
        }
        require(r.date <= today) { "A purchase date cannot be in the future." }
        if (!r.allowDuplicate)
            require(duplicates(r).isEmpty()) {
                "This looks like a receipt already saved. View it or explicitly save anyway."
            }
        if (!r.totalOnly) {
            require(
                r.items.isNotEmpty() &&
                    r.items.all {
                        it.verified &&
                            it.name.isNotBlank() &&
                            it.totalMinor in 0..Money.MAX_MINOR &&
                            Quantity.parse(it.quantity) > BigDecimal.ZERO
                    }
            )
            r.items.filter { it.size.isNotBlank() }.forEach { Quantity.parse(it.size) }
            require(
                ReceiptParser.reconciles(
                    r.items.map { it.totalMinor },
                    r.adjustmentMinor,
                    r.totalMinor,
                )
            ) {
                "Items, tax and discounts must match the total, or save a total-only expense."
            }
        }
        val transactionId =
            if (r.existingTransactionId != null) {
                val existing =
                    dao.transaction(r.existingTransactionId) ?: error("Expense not found.")
                require(existing.kind == "EXPENSE" && existing.amountMinor == r.totalMinor) {
                    "Linked expense must match this total."
                }
                existing.id
            } else
                postInside(
                    TransactionInput(
                        r.submissionKey,
                        "EXPENSE",
                        r.totalMinor,
                        r.date,
                        r.merchant,
                        "Groceries",
                        "NEEDS",
                        r.accountId,
                    )
                )
        val receiptId = id()
        dao.insert(
            Receipt(
                receiptId,
                transactionId,
                r.merchant.trim(),
                r.branch.trim(),
                r.parish.trim(),
                r.date.toString(),
                r.totalMinor,
                r.adjustmentMinor,
                // Retain captured/reviewed lines for the receipt preview, including total-only
                // expenses. Price history still comes exclusively from confirmed receipt_items.
                draft.rawText +
                    if (r.metadata.isEmpty()) ""
                    else
                        "\n\n[Verified receipt details]\n" +
                            r.metadata.entries.joinToString("\n") { (label, value) ->
                                require(
                                    label in
                                        setOf(
                                            "Time",
                                            "Payment",
                                            "Receipt number",
                                            "Transaction number",
                                            "Subtotal",
                                            "Tax",
                                            "Discount",
                                        )
                                )
                                require(value.length <= 240 && !value.contains('\n'))
                                "$label: $value"
                            },
                draft.fingerprint,
                draft.imageRef,
            )
        )
        if (!r.totalOnly) {
            val items =
                r.items.map {
                    ReceiptItem(
                        id(),
                        receiptId,
                        it.raw,
                        it.name.trim(),
                        it.quantity,
                        it.totalMinor,
                        it.size,
                        it.unit,
                        true,
                    )
                }
            dao.insertItems(items)
            // Receipt-level adjustments require reviewed item allocation before comparable prices
            // can be emitted.
            if (r.adjustmentMinor == 0L)
                dao.insertObservations(
                    items
                        .filter {
                            it.totalMinor > 0 &&
                                it.packageSize.toBigDecimalOrNull()?.let { v ->
                                    v > BigDecimal.ZERO
                                } == true &&
                                it.unit in setOf("kg", "L", "item")
                        }
                        .map { item ->
                            val size = BigDecimal(item.packageSize)
                            val qty = BigDecimal(item.quantity)
                            val productKey =
                                "${item.confirmedName.lowercase().trim()}|${size.stripTrailingZeros().toPlainString()}|${item.unit}"
                            PriceObservation(
                                id(),
                                item.id,
                                productKey,
                                item.confirmedName,
                                item.packageSize,
                                item.unit,
                                Money.unitPrice(item.totalMinor, qty),
                                Money.unitPrice(item.totalMinor, qty * size),
                                r.merchant.trim(),
                                r.branch.trim(),
                                r.parish.trim(),
                                r.date.toString(),
                            )
                        }
                )
        }
        dao.deleteDraft(draft.id)
        transactionId
    }

    suspend fun createList(name: String) {
        require(name.isNotBlank()) { "Enter a list name." }
        dao.insert(ShoppingList(id(), name.trim().take(120), today.toString()))
    }

    suspend fun addListItem(
        listId: String,
        name: String,
        quantity: String,
        key: String?,
        manual: Long?,
        optional: Boolean,
    ) {
        require(name.isNotBlank()) { "Enter an item name." }
        Quantity.parse(quantity)
        require(manual == null || manual in 1..Money.MAX_MINOR) { "Enter a price above zero." }
        manual?.let { Quantity.estimate(it, quantity) }
        dao.put(ShoppingItem(id(), listId, name.trim().take(120), quantity, key, manual, optional))
    }

    suspend fun saveShoppingList(
        list: ShoppingList,
        items: List<ShoppingItem>,
        replacing: Boolean = false,
    ) = db.withTransaction {
        require(list.name.trim().length in 1..120) {
            "Use a list name between 1 and 120 characters."
        }
        require(items.isNotEmpty()) { "Add at least one item." }
        require(items.map { it.id }.distinct().size == items.size)
        items.forEach {
            require(it.listId == list.id && it.name.trim().length in 1..120) {
                "Each item needs a name of up to 120 characters."
            }
            require(
                it.category.isNotBlank() && it.category.length <= 120 && it.note.length <= 500
            ) {
                "Each item needs a category and a note under 500 characters."
            }
            Quantity.parse(it.quantity)
            require(it.manualPriceMinor == null || it.manualPriceMinor in 0..Money.MAX_MINOR) {
                "Enter a price of zero or more."
            }
            it.manualPriceMinor?.let { price -> Quantity.estimate(price, it.quantity) }
        }
        Money.sum(
            items.mapNotNull {
                it.manualPriceMinor?.let { price -> Quantity.estimate(price, it.quantity) }
            }
        )
        // A stable draft ID makes retrying a completed save idempotent, never a duplicate list.
        if (!replacing && dao.shoppingList(list.id) != null) return@withTransaction
        dao.put(list.copy(name = list.name.trim()))
        dao.clearShoppingItems(list.id)
        items.forEach { dao.put(it) }
    }

    suspend fun restoreShoppingList(list: ShoppingList, items: List<ShoppingItem>) =
        db.withTransaction {
            dao.put(list)
            items.forEach { dao.put(it) }
        }

    suspend fun duplicateShoppingList(list: ShoppingList, items: List<ShoppingItem>) {
        val copy =
            list.copy(
                id = id(),
                name = (list.name.take(113) + " (copy)"),
                createdDate = today.toString(),
            )
        saveShoppingList(copy, items.map { it.copy(id = id(), listId = copy.id, checked = false) })
    }

    suspend fun setCategoryLimit(
        category: String,
        bucket: String,
        amount: Long,
        accountId: String? = null,
        originalId: String? = null,
    ) = db.withTransaction {
        require(accountId == null || dao.account(accountId) != null) { "Choose a valid account." }
        require(
            category.isNotBlank() &&
                bucket in setOf("NEEDS", "WANTS", "SAVINGS") &&
                amount in 0..Money.MAX_MINOR
        )
        // Rekeying an edited limit replaces its previous identity atomically.
        if (originalId != null) dao.deleteCategoryLimit(originalId)
        dao.put(
            CategoryLimit(
                (accountId?.let { "$it:" } ?: "") + "$bucket:${category.trim().lowercase()}",
                category.trim(),
                bucket,
                amount,
                accountId,
            )
        )
    }

    suspend fun editAccount(id: String, name: String, included: Boolean) {
        val account = dao.account(id) ?: error("Account not found.")
        require(name.isNotBlank()) { "Enter an account name." }
        dao.update(account.copy(name = name.trim().take(100), included = included))
    }

    suspend fun editRecord(
        id: String,
        description: String,
        category: String,
        bucket: String,
        date: LocalDate,
    ) = db.withTransaction {
        val t = dao.transaction(id) ?: error("Record not found.")
        require(
            category.isNotBlank() && bucket in setOf("NEEDS", "WANTS", "SAVINGS") && date <= today
        )
        if (t.bucket != bucket) {
            require(dao.refundCount(id) == 0) {
                "Change linked refunds before changing the original budget group."
            }
            t.refundOfId?.let {
                require(dao.transaction(it)?.bucket == bucket) {
                    "Use the original expense's budget group."
                }
            }
        }
        dao.update(
            t.copy(
                description = description.trim().take(240),
                category = category.trim(),
                bucket = bucket,
                date = date.toString(),
            )
        )
        val splits = dao.transactionSplits(id)
        dao.updateSplits(
            splits.map {
                it.copy(
                    bucket = bucket,
                    category = if (splits.size == 1) category.trim() else it.category,
                )
            }
        )
    }

    companion object {
        fun id() = UUID.randomUUID().toString()

        fun fingerprint(bytes: ByteArray) =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
    }
}
