package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import jm.yardmoney.AppModel
import jm.yardmoney.core.*
import kotlinx.coroutines.launch
import jm.yardmoney.data.*

@Composable
internal fun TransactionForm(
    model: AppModel,
    data: FinanceSnapshot,
    initialKind: String,
    commitment: Commitment?,
    busy: Boolean,
    initialAccountId: String? = null,
    initialDraft: QuickAddDraft? = null,
    rules: List<CategorySuggestion> = emptyList(),
    saveRule: ((String, String, String, String?) -> Unit)? = null,
    close: () -> Unit,
) {
    val key = rememberSaveable { FinanceRepository.id() }
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    val remaining =
        data.ledger.commitments.find { it.commitment.id == commitment?.id }?.remainingMinor
    var amount by rememberSaveable { mutableStateOf(initialDraft?.amount ?: remaining?.let(Money::input) ?: "") }
    var date by rememberSaveable { mutableStateOf(initialDraft?.date ?: model.repo.today.toString()) }
    var description by rememberSaveable { mutableStateOf(initialDraft?.description ?: commitment?.name ?: "") }
    var category by rememberSaveable { mutableStateOf(initialDraft?.category ?: "Other") }
    var bucket by rememberSaveable { mutableStateOf(initialDraft?.bucket ?: "NEEDS") }
    var account by rememberSaveable {
        mutableStateOf(
            initialDraft?.accountId ?: (commitment?.accountId ?: initialAccountId)?.takeIf { id ->
                data.ledger.accounts.any { it.account.id == id }
            } ?: data.ledger.accounts.firstOrNull()?.account?.id.orEmpty()
        )
    }
    var destination by rememberSaveable {
        mutableStateOf(
            data.ledger.accounts.firstOrNull { it.account.id != account }?.account?.id ?: ""
        )
    }
    var goal by rememberSaveable { mutableStateOf("") }
    var refund by rememberSaveable { mutableStateOf("") }
    var splits by rememberSaveable { mutableStateOf("") }
    val originalFields = rememberSaveable {
        listOf(
            kind,
            amount,
            date,
            description,
            category,
            bucket,
            account,
            destination,
            goal,
            refund,
            splits,
        )
    }
    val accounts = data.ledger.accounts.associate { it.account.id to it.account.name }
    val accountOptions =
        data.ledger.accounts.map {
            IdentityOption(
                it.account.id,
                it.account.name,
                Money.format(it.balanceMinor),
                accountIdentity(it.account),
            )
        }
    val categories =
        (listOf(
                "Groceries",
                "Transport",
                "Utilities",
                "Home",
                "Health",
                "Dining",
                "Entertainment",
                "Clothing",
                "Savings",
                "Other",
            ) + data.splits.map { it.category })
            .distinct()
            .map { IdentityOption(it, it) }

    var categoryChosen by rememberSaveable { mutableStateOf(!initialDraft?.category.isNullOrBlank()) }
    val snack = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    LaunchedEffect(description, account, rules, kind) {
        if (!categoryChosen && kind == "EXPENSE" && commitment == null) {
            CategoryRuleMatcher.match(description, account, rules)?.let {
                category = it.category
                bucket = it.bucket
            }
        }
    }
    var splitExpanded by rememberSaveable { mutableStateOf(false) }
    val validAmount = runCatching {
        if (kind == "ADJUSTMENT") Money.parse(amount, true) != 0L else Money.positive(amount) > 0
    }
        .getOrDefault(false)
    val canSave =
        kind in listOf("EXPENSE", "INCOME", "TRANSFER", "REFUND", "ADJUSTMENT") &&
            validAmount &&
            runCatching { DateWindow.UpToToday.allows(LocalDate.parse(date), model.repo.today) }.getOrDefault(false) &&
            (kind !in listOf("EXPENSE", "REFUND") || category.isNotBlank()) &&
            account in accounts &&
            (kind != "TRANSFER" || destination in accounts && destination != account)
    MoneyEntrySheet(
        title = commitment?.name ?: "Record money",
        feedback = { SnackbarHost(snack) },
        action =
            when (kind) {
                "EXPENSE" -> "Save expense"
                "INCOME" -> "Record income"
                "TRANSFER" -> "Transfer money"
                "REFUND" -> "Record refund"
                else -> "Save adjustment"
            },
        busy = busy,
        canSave = canSave,
        close = close,
        keyValue = amount,
        dirty =
            listOf(
                kind,
                amount,
                date,
                description,
                category,
                bucket,
                account,
                destination,
                goal,
                refund,
                splits,
            ) != originalFields,
        save = {
            model.act(close) {
                val pieces =
                    if (kind !in listOf("EXPENSE", "REFUND") || splits.isBlank()) emptyList()
                    else
                        splits.split(';').map { piece ->
                            val fields = piece.split('=')
                            require(fields.size == 2) {
                                "Use Category=amount for each split."
                            }
                            fields[0].trim() to Money.positive(fields[1])
                        }
                model.repo.post(
                    TransactionInput(
                        key,
                        kind,
                        if (kind == "ADJUSTMENT") Money.parse(amount, true)
                        else Money.positive(amount),
                        LocalDate.parse(date),
                        description,
                        category,
                        bucket,
                        account,
                        destination.takeIf { kind == "TRANSFER" },
                        commitment?.id,
                        goal.takeIf { kind == "TRANSFER" && it.isNotBlank() },
                        refund.takeIf { kind == "REFUND" && it.isNotBlank() },
                        pieces,
                    )
                )
            }
        },
    ) {
        if (commitment == null)
            MoneyEntrySection("Transaction type") {
                TransactionTypePicker(kind) {
                    kind = it
                    refund = ""
                    goal = ""
                }
                Text(
                    when (kind) {
                        "TRANSFER" ->
                            "Move money between accounts without adding income or spending."
                        "ADJUSTMENT" -> "Correct a balance. Use a negative amount to reduce it."
                        "REFUND" -> "Return money from an expense. Link the original when possible."
                        "INCOME" -> "Record pay only once it has arrived."
                        else -> "Record money you spent."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        MoneyEntrySection("Amount and date") {
            MoneyField("Amount (J$)", amount, prominent = true) { amount = it }
            Field("Date (YYYY-MM-DD)", date) { date = it }
        }
        MoneyEntrySection(if (kind == "TRANSFER") "Move between accounts" else "Account") {
            IdentityPicker(
                if (kind == "TRANSFER") "From account" else "Account",
                account,
                accountOptions,
            ) {
                account = it
                if (destination == it)
                    destination = accounts.keys.firstOrNull { id -> id != it } ?: ""
            }
            if (kind == "TRANSFER") {
                IdentityPicker(
                    "To account",
                    destination,
                    accountOptions.filter { it.id != account },
                ) {
                    destination = it
                }
                IdChoice(
                    "Savings goal (optional)",
                    goal,
                    mapOf("" to "None") +
                        data.ledger.goals.associate { it.goal.id to it.goal.name },
                ) {
                    goal = it
                }
            }
        }
        MoneyEntrySection("Details") {
            Field("Description", description) { description = it }
            if (kind == "EXPENSE" || kind == "REFUND") {
                IdentityPicker("Category", category, categories, allowCustom = true) {
                    category = it
                    categoryChosen = true
                    val merchant = description.trim()
                    val chosen = it
                    if (saveRule != null && merchant.isNotBlank()) uiScope.launch {
                        if (snack.showSnackbar("Always use $chosen for '$merchant'?", "Always use",
                            duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed)
                            saveRule(merchant, chosen, bucket, account.takeIf { it.isNotBlank() })
                    }
                }
                Choice("Budget group", bucket, listOf("NEEDS", "WANTS", "SAVINGS")) { bucket = it }
            }
            if (kind == "REFUND")
                IdChoice(
                    "Original expense (optional)",
                    refund,
                    mapOf("" to "Unlinked refund") +
                        data.ledger.transactions
                            .filter { it.kind == "EXPENSE" }
                            .associate {
                                it.id to
                                    (it.description.ifBlank { it.category } +
                                        " · " +
                                        Money.format(it.amountMinor) +
                                        " · " +
                                        it.date)
                            },
                ) {
                    refund = it
                    data.ledger.transactions
                        .find { tx -> tx.id == it }
                        ?.let { original ->
                            bucket = original.bucket
                            category = original.category
                        }
                }
        }
        if (kind == "EXPENSE" || kind == "REFUND")
            MoneyEntrySection("Category split (optional)") {
                Tick("Split this amount between categories", splitExpanded || splits.isNotBlank()) {
                    splitExpanded = it
                    if (!it) splits = ""
                }
                if (splitExpanded || splits.isNotBlank()) {
                    Field("Categories and amounts", splits) { splits = it }
                    Text(
                        "Example: Food=100; Transport=200. Split amounts must equal the total.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        if (commitment != null)
            Text(
                "This payment reduces the reservation and records the account movement together.",
                style = MaterialTheme.typography.bodySmall,
            )
    }
}

@Composable
internal fun IdChoice(
    label: String,
    id: String,
    options: Map<String, String>,
    change: (String) -> Unit,
) = DropdownField(label, id, options, change)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransactionTypePicker(selected: String, change: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
                "EXPENSE" to "Expense",
                "INCOME" to "Income",
                "TRANSFER" to "Transfer",
                "REFUND" to "Refund",
                "ADJUSTMENT" to "Adjustment",
            )
            .forEach { (id, label) ->
                val icon =
                    when (id) {
                        "EXPENSE" -> Icons.Default.NorthEast
                        "INCOME" -> Icons.Default.SouthWest
                        "TRANSFER" -> Icons.Default.SwapHoriz
                        "REFUND" -> Icons.AutoMirrored.Filled.Undo
                        else -> Icons.Default.Tune
                    }
                FilterChip(
                    selected = selected == id,
                    onClick = { change(id) },
                    enabled = !LocalSaving.current,
                    label = { Text(label) },
                    leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
    }
}
