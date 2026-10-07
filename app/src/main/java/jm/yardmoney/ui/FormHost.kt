package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.time.LocalDate
import jm.yardmoney.AppModel
import jm.yardmoney.core.*
import jm.yardmoney.data.*

/** Hosts the small forms and confirmations opened by route name (see MainPages). */
@Composable
internal fun MainForms(
    model: AppModel,
    data: FinanceSnapshot,
    scope: String?,
    busy: Boolean,
    form: String?,
    setForm: (String?) -> Unit,
) {
    val close = { setForm(null) }
    val route = form.orEmpty()
    // Everything after the first colon is a record id ("editTx:<id>").
    val id = route.substringAfter(':')
    if (route == "missingRecord") MissingRecordNotice(close)
    key(form) {
        when {
            route == "categoryRules" -> StagedEditSheet("Category rules", "", busy, false, close) {
                CategoryRulesPage(data.categoryRules.map { it.suggestion() },
                    edit = { setForm("editRule:$it") }, add = { setForm("categoryRule") })
            }
            route == "categoryRule" -> CategoryRuleEditor(model, data, null, busy) { setForm("categoryRules") }
            route.startsWith("editRule:") -> {
                val rule = data.categoryRules.find { it.id == id }
                if (rule == null) MissingRecordNotice(close)
                else CategoryRuleEditor(model, data, rule, busy) { setForm("categoryRules") }
            }
            route == "account" ->
                SimpleForm(
                    "Add account",
                    listOf("Name", "Opening balance (J$)"),
                    listOf("", "0"),
                    busy,
                    close,
                    choices =
                        listOf(
                            "Type" to listOf("CASH", "CURRENT", "SAVINGS", "WALLET"),
                            "Spendable?" to listOf("Included", "Protected"),
                        ),
                ) { v, c ->
                    model.act(close) {
                        model.repo.addAccount(
                            v[0],
                            c[0],
                            Money.parse(v[1], true),
                            c[1] == "Included",
                        )
                    }
                }
            route == "goal" ->
                SimpleForm(
                    "Savings goal",
                    listOf("Name", "Target (J$)", "Already saved elsewhere (J$)"),
                    listOf("", "", "0"),
                    busy,
                    close,
                ) { v, _ ->
                    model.act(close) {
                        model.repo.addGoal(v[0], Money.positive(v[1]), Money.parse(v[2]))
                    }
                }
            route == "commitment" -> ReservationEditor(model, data, null, scope, busy, close)
            route == "split" ->
                data.ledger.profile?.let { BudgetForm(model, it, busy, close) }
            route == "payday" ->
                data.ledger.profile?.let { profile ->
                    SimpleForm(
                        "Start a new period",
                        listOf("Next payday (YYYY-MM-DD)"),
                        listOf(nextPayday(profile, model.repo.today).toString()),
                        busy,
                        close,
                        description =
                            "Start the budget period today. Record received pay separately in Activity; this change adds no money.",
                    ) { v, _ ->
                        model.act(close) { model.repo.confirmPayday(LocalDate.parse(v[0]), 0) }
                    }
                }
            route == "limit" -> LimitEditor(model, data, null, scope, busy, close)
            route.startsWith("editLimit:") ->
                data.limits
                    .find { it.id == id }
                    ?.let { LimitEditor(model, data, it, scope, busy, close) }
            route.startsWith("editTx:") -> {
                val t = data.ledger.transactions.firstOrNull { it.id == id }
                if (t == null) MissingRecordNotice(close)
                else
                    SimpleForm(
                        "Edit record",
                        listOf("Description", "Category", "Date (YYYY-MM-DD)"),
                        listOf(t.description, t.category, t.date),
                        busy,
                        close,
                        choices =
                            listOf(
                                "Budget group" to
                                    listOf(t.bucket) +
                                        listOf("NEEDS", "WANTS", "SAVINGS").filter {
                                            it != t.bucket
                                        },
                                "Action" to listOf("Save edits", "Delete record"),
                            ),
                        description =
                            "Amounts and account movements stay together. To correct an amount, delete this record and re-enter it.",
                    ) { v, c ->
                        if (c[1] == "Delete record") setForm("delete:${t.id}")
                        else
                            model.act(close) {
                                model.repo.editRecord(t.id, v[0], v[1], c[0], LocalDate.parse(v[2]))
                            }
                    }
            }
            route.startsWith("editAccount:") -> {
                val account = data.ledger.accounts.firstOrNull { it.account.id == id }?.account
                if (account == null) MissingRecordNotice(close)
                else
                    SimpleForm(
                        "Account settings",
                        listOf("Name"),
                        listOf(account.name),
                        busy,
                        close,
                        choices =
                            listOf(
                                "Spendable?" to
                                    if (account.included) listOf("Included", "Protected")
                                    else listOf("Protected", "Included")
                            ),
                        description =
                            "Included balances count towards safe to spend. Protected balances are excluded. This changes your plan, not the amount of money in the account.",
                    ) { v, c ->
                        model.act(close) {
                            model.repo.editAccount(account.id, v[0], c[0] == "Included")
                        }
                    }
            }
            route.startsWith("editCommit:") ->
                data.ledger.commitments
                    .find { it.commitment.id == id }
                    ?.let { ReservationEditor(model, data, it, scope, busy, close) }
            route.startsWith("delete:") ->
                AlertDialog(
                    onDismissRequest = close,
                    title = { Text("Delete transaction?") },
                    text = {
                        Text(
                            "This removes the account movement, linked receipt prices and payments against bills or goals. Your balances will update."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !busy,
                            onClick = { model.act(close) { model.repo.deleteTransaction(id) } },
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = close, shape = MaterialTheme.shapes.small) {
                            Text("Keep")
                        }
                    },
                )
            route.startsWith("receipt:") ->
                data.receipt.receipts
                    .find { it.id == id }
                    ?.let { ReceiptDetails(model, it, busy, close) }
            // key() makes this when an expression, so it must be exhaustive.
            else -> Unit
        }
    }
}

@Composable
internal fun MissingRecordNotice(close: () -> Unit) {
    // Saved routes can outlive a record removed by another writer or a restored backup.
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Record unavailable") },
        text = { Text("This record is no longer available.") },
        confirmButton = { TextButton(onClick = close) { Text("OK") } },
    )
}
