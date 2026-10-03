package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.LocalDate
import jm.yardmoney.AppModel
import jm.yardmoney.core.Money
import jm.yardmoney.data.*

@Composable
internal fun TransactionForm(
    model: AppModel,
    data: FinanceSnapshot,
    initialKind: String,
    commitment: Commitment?,
    busy: Boolean,
    close: () -> Unit,
) {
    val key = rememberSaveable { FinanceRepository.id() }
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    val remaining =
        data.ledger.commitments.find { it.commitment.id == commitment?.id }?.remainingMinor
    var amount by rememberSaveable { mutableStateOf(remaining?.let(Money::input) ?: "") }
    var date by rememberSaveable { mutableStateOf(model.repo.today.toString()) }
    var description by rememberSaveable { mutableStateOf(commitment?.name ?: "") }
    var category by rememberSaveable { mutableStateOf("Other") }
    var bucket by rememberSaveable { mutableStateOf("NEEDS") }
    var account by rememberSaveable { mutableStateOf(data.ledger.accounts.first().account.id) }
    var destination by rememberSaveable {
        mutableStateOf(
            data.ledger.accounts.firstOrNull { it.account.id != account }?.account?.id ?: ""
        )
    }
    var goal by rememberSaveable { mutableStateOf("") }
    var refund by rememberSaveable { mutableStateOf("") }
    var splits by rememberSaveable { mutableStateOf("") }
    val accounts = data.ledger.accounts.associate { it.account.id to it.account.name }

    var splitExpanded by rememberSaveable { mutableStateOf(false) }
    val validAmount = runCatching {
        if (kind == "ADJUSTMENT") Money.parse(amount, true) != 0L else Money.positive(amount) > 0
    }
        .getOrDefault(false)
    val canSave =
        validAmount &&
            runCatching { LocalDate.parse(date) }.isSuccess &&
            account in accounts &&
            (kind != "TRANSFER" || destination in accounts && destination != account)
    MoneyEntrySheet(
        title = commitment?.name ?: "Record money",
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
                Choice(
                    "Type",
                    kind,
                    listOf("EXPENSE", "INCOME", "TRANSFER", "REFUND", "ADJUSTMENT"),
                ) {
                    kind = it
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
            Field("Amount (J$)", amount) { amount = it }
            Field("Date (YYYY-MM-DD)", date) { date = it }
        }
        MoneyEntrySection(if (kind == "TRANSFER") "Move between accounts" else "Account") {
            IdChoice(if (kind == "TRANSFER") "From account" else "Account", account, accounts) {
                account = it
                if (destination == it)
                    destination = accounts.keys.firstOrNull { id -> id != it } ?: ""
            }
            if (kind == "TRANSFER") {
                IdChoice("To account", destination, accounts.filterKeys { it != account }) {
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
                Field("Category", category) { category = it }
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
