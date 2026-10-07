package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import jm.yardmoney.AppModel
import jm.yardmoney.core.*
import jm.yardmoney.data.*
import kotlinx.coroutines.launch

@Composable
internal fun QuickAddSheet(
    model: AppModel, data: FinanceSnapshot, rules: List<CategorySuggestion>, busy: Boolean,
    dismiss: () -> Unit, edit: (QuickAddDraft) -> Unit, action: (QuickAddAction) -> Unit,
    saveRule: (String, String, String, String?) -> Unit,
) {
    var line by rememberSaveable { mutableStateOf("") }
    var values by rememberSaveable { mutableStateOf(QuickAddDraft().savedValues()) }
    val draft = QuickAddDraft.fromSaved(values)
    val key = rememberSaveable { FinanceRepository.id() }
    val today = model.repo.today
    val accounts = remember(data.ledger.accounts) { data.ledger.accounts.map { IdentityOption(it.account.id, it.account.name,
        Money.format(it.balanceMinor), accountIdentity(it.account)) } }
    val captureAccounts = remember(data.ledger.accounts) { data.ledger.accounts.map { CaptureAccount(it.account.id, it.account.name) } }
    val preview = remember(line, captureAccounts, today) { QuickAddParser.parse(line, captureAccounts, today) }
    val categories = (listOf("Groceries", "Transport", "Utilities", "Home", "Health", "Dining",
        "Entertainment", "Clothing", "Savings", "Other") + data.splits.map { it.category } + rules.map { it.category })
        .distinct().map { IdentityOption(it, it) }
    val repeats = remember(data.ledger.transactions, data.accountEntries, today) {
        val entries = data.accountEntries.associateBy { it.transactionId }
        RepeatExpenses.suggestions(data.ledger.transactions.filter { it.kind == "EXPENSE" }.mapNotNull { tx ->
            entries[tx.id]?.let { RepeatExpense(tx.description, tx.amountMinor, tx.category,
                tx.bucket, it.accountId, LocalDate.parse(tx.date)) }
        }, today)
    }
    val snack = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val latestDraft by rememberUpdatedState(draft)
    val canSave = runCatching {
        Money.positive(draft.amount)
        DateWindow.UpToToday.allows(LocalDate.parse(draft.date), today) &&
            accounts.any { it.id == draft.accountId } && draft.category.isNotBlank()
    }.getOrDefault(false)
    fun save() {
        model.act(dismiss) {
            model.repo.post(TransactionInput(key, "EXPENSE", Money.positive(draft.amount),
                LocalDate.parse(draft.date), draft.description, draft.category, draft.bucket, draft.accountId))
        }
    }
    StagedEditSheet("Quick Add", line, busy, line.isNotBlank() || values != QuickAddDraft().savedValues(), dismiss,
        actions = {
            SnackbarHost(snack)
            Button(onClick = ::save, enabled = canSave && !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).heightIn(min = 48.dp)) {
                Text(if (busy) "Saving…" else "Save expense")
            }
        }) {
        QuickAddContent(line, draft, accounts, categories, repeats, preview.issues, busy, canSave,
            onLine = { text ->
                line = text
                val parsed = QuickAddParser.parse(text, captureAccounts, today)
                val matched = CategoryRuleMatcher.match(parsed.description.orEmpty(), parsed.accountId, rules)
                values = QuickAddDraft(parsed.amountMinor?.let(Money::input).orEmpty(), parsed.description.orEmpty(),
                    matched?.category.orEmpty(), matched?.bucket ?: "NEEDS", parsed.accountId.orEmpty(),
                    parsed.date?.toString().orEmpty()).savedValues()
            }, onDraft = { changed ->
                values = changed.savedValues()
                if (changed.category != draft.category && changed.category.isNotBlank() &&
                    changed.description.isNotBlank()) uiScope.launch {
                    if (snack.showSnackbar("Always use ${changed.category} for '${changed.description}'?",
                        actionLabel = "Always use", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed)
                        saveRule(changed.description, changed.category, latestDraft.bucket, latestDraft.accountId.takeIf { it.isNotBlank() })
                }
            }, onRepeat = { repeat, editing ->
                val filled = QuickAddDraft(Money.input(repeat.amountMinor), repeat.description,
                    repeat.category, repeat.bucket, repeat.accountId, today.toString())
                values = filled.savedValues()
                if (editing) edit(filled)
            }, onSave = ::save, onEdit = { edit(draft) }, onAction = action, showSaveAction = false)
    }
}
