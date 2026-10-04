package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import jm.yardmoney.AppModel
import jm.yardmoney.core.Money
import jm.yardmoney.data.*

@Composable
internal fun ReservationEditor(
    model: AppModel,
    data: FinanceSnapshot,
    existing: CommitmentBalance?,
    initialAccountId: String?,
    busy: Boolean,
    close: () -> Unit,
) {
    val original = existing?.commitment
    val key = rememberSaveable(original?.id) { FinanceRepository.id() }
    var name by rememberSaveable(original?.id) { mutableStateOf(original?.name ?: "") }
    var amount by
        rememberSaveable(original?.id) {
            mutableStateOf(original?.amountMinor?.let(Money::input) ?: "")
        }
    var due by rememberSaveable(original?.id) { mutableStateOf(original?.dueDate ?: "") }
    var kind by rememberSaveable(original?.id) { mutableStateOf(original?.kind ?: "BILL") }
    var repeat by rememberSaveable(original?.id) { mutableStateOf("ONCE") }
    var account by
        rememberSaveable(original?.id) {
            mutableStateOf(
                original?.accountId ?: if (original == null) initialAccountId ?: "" else ""
            )
        }
    var removing by remember { mutableStateOf(false) }
    val valid =
        name.isNotBlank() &&
            runCatching { Money.positive(amount) >= (existing?.fulfilledMinor ?: 0L) }
                .getOrDefault(false) &&
            (due.isBlank() || runCatching { LocalDate.parse(due) }.isSuccess)
    MoneyEntrySheet(
        if (original == null) "Reserve money" else "Edit this reservation",
        "Save reservation",
        busy,
        valid,
        save = {
            model.act(close) {
                if (original == null)
                    model.repo.addCommitment(
                        name,
                        kind,
                        Money.positive(amount),
                        due.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                        frequency = repeat,
                        submissionKey = key,
                        accountId = account.ifBlank { null },
                    )
                else
                    model.repo.editCommitment(
                        original.id,
                        name,
                        Money.positive(amount),
                        due.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                        accountId = account.ifBlank { null },
                    )
            }
        },
        close = close,
        keyValue = amount,
        dirty =
            name != (original?.name ?: "") ||
                amount != (original?.amountMinor?.let(Money::input) ?: "") ||
                due != (original?.dueDate ?: "") ||
                account != (original?.accountId ?: initialAccountId ?: "") ||
                repeat != "ONCE" ||
                kind != (original?.kind ?: "BILL"),
    ) {
        IdentityBadge(categoryIdentity(name.ifBlank { "Bills" }))
        Field("Name", name) { name = it }
        MoneyField("Amount (J$)", amount, prominent = true) { amount = it }
        DateDropdown("Due date (YYYY-MM-DD; optional)", due, optional = true) { due = it }
        IdentityPicker(
            "Funding account",
            account,
            listOf(
                IdentityOption(
                    "",
                    "Unassigned · all accounts",
                    "Choose an account to include this bill in its plan",
                    MoneyIdentity(Icons.Default.Wallet, 0xFF00865A),
                )
            ) +
                data.ledger.accounts.map {
                    IdentityOption(
                        it.account.id,
                        it.account.name,
                        Money.format(it.balanceMinor),
                        accountIdentity(it.account),
                    )
                },
        ) {
            account = it
        }
        if (original == null) {
            Choice("Kind", kind, listOf("BILL", "SAVINGS", "DEBT", "RESERVE")) { kind = it }
            Choice("Repeat", repeat, listOf("ONCE", "WEEKLY", "FORTNIGHTLY", "MONTHLY")) {
                repeat = it
            }
        } else {
            Text(
                if (original.occurrenceKey.contains('@'))
                    "Changes apply to this scheduled payment. Other dates keep their own details."
                else "Keep this reservation up to date as your plans change.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (existing.fulfilledMinor > 0) AmountRow("Already paid", existing.fulfilledMinor)
            OutlinedButton(
                onClick = { removing = true },
                enabled = !busy && existing.fulfilledMinor == 0L,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.DeleteOutline, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (original.occurrenceKey.contains('@')) "Cancel unpaid series"
                    else "Remove reservation"
                )
            }
        }
    }
    if (removing && original != null)
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text("Remove reservation?") },
            text = {
                Text(
                    if (original.occurrenceKey.contains('@'))
                        "Unpaid dates in this series will be removed. Paid history remains."
                    else
                        "This releases the reserved amount. It does not change your account balance."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { model.act(close) { model.repo.removeCommitment(original.id) } }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text("Keep") } },
        )
}

@Composable
internal fun LimitEditor(
    model: AppModel,
    data: FinanceSnapshot,
    existing: CategoryLimit?,
    scope: String?,
    busy: Boolean,
    close: () -> Unit,
) {
    var category by
        rememberSaveable(existing?.id) { mutableStateOf(existing?.category ?: "Groceries") }
    var amount by
        rememberSaveable(existing?.id) {
            mutableStateOf(existing?.limitMinor?.let(Money::input) ?: "")
        }
    var bucket by rememberSaveable(existing?.id) { mutableStateOf(existing?.bucket ?: "NEEDS") }
    var account by
        rememberSaveable(existing?.id) {
            mutableStateOf(if (existing != null) existing.accountId ?: "" else scope ?: "")
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
                "Other",
            ) + data.splits.map { it.category })
            .distinct()
            .map { IdentityOption(it, it) }
    MoneyEntrySheet(
        "Category spending limit",
        "Save limit",
        busy,
        category.isNotBlank() && runCatching { Money.parse(amount) >= 0 }.getOrDefault(false),
        save = {
            model.act(close) {
                model.repo.setCategoryLimit(
                    category,
                    bucket,
                    Money.parse(amount),
                    account.ifBlank { null },
                    originalId = existing?.id,
                )
            }
        },
        close = close,
        keyValue = amount,
        dirty =
            category != (existing?.category ?: "Groceries") ||
                amount != (existing?.limitMinor?.let(Money::input) ?: "") ||
                bucket != (existing?.bucket ?: "NEEDS") ||
                account != (if (existing != null) existing.accountId ?: "" else scope ?: ""),
    ) {
        IdentityPicker("Category", category, categories, allowCustom = true) { category = it }
        MoneyField("Limit per period (J$)", amount, prominent = true) { amount = it }
        Choice("Budget group", bucket, listOf("NEEDS", "WANTS", "SAVINGS")) { bucket = it }
        IdentityPicker(
            "Limit scope",
            account,
            listOf(
                IdentityOption(
                    "",
                    "All accounts",
                    "Shared spending guideline",
                    MoneyIdentity(Icons.Default.Wallet, 0xFF00865A),
                )
            ) +
                data.ledger.accounts.map {
                    IdentityOption(
                        it.account.id,
                        it.account.name,
                        identity = accountIdentity(it.account),
                    )
                },
        ) {
            account = it
        }
        Text(
            "Limits guide spending. They do not reserve money or subtract from your balance.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
