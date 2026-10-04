package jm.yardmoney.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import jm.yardmoney.core.Money
import jm.yardmoney.data.*

@Composable
internal fun AccountTile(row: AccountBalance, modifier: Modifier = Modifier, open: () -> Unit) {
    val color = identityColor(accountIdentity(row.account))
    Card(
        onClick = open,
        modifier = modifier,
        shape = YardShape.card,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .10f)),
    ) {
        Column(
            Modifier.padding(YardSpace.xl),
            verticalArrangement = Arrangement.spacedBy(YardSpace.md),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YardSpace.sm),
            ) {
                IdentityBadge(accountIdentity(row.account))
                Text(
                    row.account.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(Icons.Default.ChevronRight, null)
            }
            Text(
                Money.format(row.balanceMinor),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (row.account.included) "Available to spend" else "Protected savings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransactionRows(
    records: List<MoneyTransaction>,
    data: FinanceSnapshot,
    edit: (String) -> Unit,
) {
    var shown by rememberSaveable { mutableIntStateOf(30) }
    val accountsByTransaction =
        remember(data.accountEntries, data.ledger.accounts) {
            transactionAccounts(data.accountEntries, data.ledger.accounts)
        }
    records.take(shown).forEach { tx ->
        key(tx.id) {
            Card(
                onClick = { edit(tx.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
            ) {
                Row(
                    Modifier.padding(YardSpace.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(YardSpace.md),
                ) {
                    IdentityBadge(
                        categoryIdentity(if (tx.kind == "TRANSFER") "Transfer" else tx.category)
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            tx.description.ifBlank { tx.category },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            tx.date + " · " + tx.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            accountsByTransaction[tx.id].orEmpty().forEach { row ->
                                val identity = accountIdentity(row.account)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        identity.icon,
                                        null,
                                        Modifier.size(16.dp),
                                        tint = identityColor(identity),
                                    )
                                    Text(
                                        row.account.name,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        Text(
                            (if (tx.kind == "EXPENSE") "−"
                            else if (tx.kind in listOf("INCOME", "REFUND")) "+" else "") +
                                Money.format(tx.amountMinor),
                            style = MaterialTheme.typography.titleLarge,
                            color =
                                if (tx.kind in listOf("INCOME", "REFUND"))
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (records.size > shown)
        TextButton(onClick = { shown += 30 }, modifier = Modifier.fillMaxWidth()) {
            Text("Show more records")
        }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DashboardPage(
    data: FinanceSnapshot,
    today: LocalDate,
    scope: String?,
    open: (String) -> Unit,
    account: (String) -> Unit,
) {
    val safe =
        remember(data.ledger.accounts, data.ledger.commitments, data.ledger.profile, today) {
            safe(data, today)
        }
    Page {
        SectionHeading(
            "Hello, " + data.ledger.profile!!.name.ifBlank { "neighbour" },
            subtitle = "A little clarity. A lot more possibility.",
        )
        SafeCard(safe, data.ledger.profile.nextPayday)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { open("transaction") }, shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add expense")
            }
            FilledTonalButton(onClick = { open("scan") }, shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Default.DocumentScanner, null)
                Spacer(Modifier.width(8.dp))
                Text("Scan receipt")
            }
        }
        if (scope != null)
            Text(
                "This account only. Unassigned bills are available in All accounts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        SectionHeading(
            "Your accounts",
            action = { TextButton(onClick = { open("accounts") }) { Text("View all") } },
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            data.ledger.accounts.forEach { row ->
                AccountTile(row, Modifier.width(272.dp)) { account(row.account.id) }
            }
        }
        BudgetOverview(data, today)
        SectionHeading("Coming up", subtitle = "A clear view of what is already spoken for.")
        val upcoming =
            reservationGroups(data.ledger.commitments)
                .map { it.primary }
                .filter { it.remainingMinor > 0 }
                .take(3)
        if (upcoming.isEmpty())
            EmptyState(
                "Room to plan ahead",
                "Add your next bill or savings reservation in Plan.",
                icon = Icons.Default.Event,
            )
        else
            ReservationList(
                upcoming,
                pay = { open("pay:" + it.id) },
                edit = { open("editCommit:" + it.id) },
            )
        SectionHeading(
            "Recent activity",
            action = { TextButton(onClick = { open("activity") }) { Text("See all") } },
        )
        if (data.ledger.transactions.isEmpty())
            EmptyState(
                "Your story starts here",
                "Record received income or your first purchase to bring these charts to life.",
                icon = Icons.Default.Payments,
            )
        TransactionRows(data.ledger.transactions.take(5), data) { open("editTx:$it") }
    }
}

@Composable
internal fun ActivityPage(
    data: FinanceSnapshot,
    today: LocalDate,
    open: (String) -> Unit,
    draft: (String) -> Unit,
) {
    Page {
        SectionHeading(
            "Your money in motion",
            subtitle = "Find a record, spot a pattern, make your next move.",
            action = {
                IconButton(onClick = { open("transaction") }) {
                    Icon(Icons.Default.Add, "Add record")
                }
            },
        )
        ActivityExplorer(
            data.ledger.transactions,
            LocalDate.parse(data.ledger.profile!!.periodStart),
            today,
        ) { records ->
            TransactionRows(records, data) { open("editTx:$it") }
        }
        SectionHeading("Receipts & drafts", subtitle = "Your purchases, with the details attached.")
        if (data.receipt.drafts.isEmpty() && data.receipt.receipts.isEmpty())
            EmptyState(
                "Keep the little details",
                "Scan a receipt to save its items and build a personal price history.",
                icon = Icons.Default.DocumentScanner,
                action = { TextButton(onClick = { open("scan") }) { Text("Scan receipt") } },
            )
        data.receipt.drafts.forEach { d ->
            Record("Review receipt", d.createdDate, "Draft") { draft(d.id) }
        }
        data.receipt.receipts.take(30).forEach { r ->
            Record(r.merchant, r.date + " · " + r.branch, Money.format(r.totalMinor)) {
                open("receipt:" + r.id)
            }
        }
    }
}

@Composable
internal fun PlanPage(
    data: FinanceSnapshot,
    today: LocalDate,
    scope: String?,
    open: (String) -> Unit,
) {
    Page {
        SectionHeading(
            "Make room for what matters",
            subtitle = "Next payday · " + data.ledger.profile!!.nextPayday,
            action = {
                IconButton(onClick = { open("payday") }) {
                    Icon(Icons.Default.EditCalendar, "Start a new pay period")
                }
            },
        )
        BudgetOverview(data, today, onChangeSplit = { open("split") })
        SectionHeading(
            "Category limits",
            subtitle = "Spent, available, and always in view.",
            action = {
                IconButton(onClick = { open("limit") }) {
                    Icon(Icons.Default.Add, "Set a category limit")
                }
            },
        )
        CategoryLimits(data, today) { open("editLimit:" + it.id) }
        SectionHeading(
            "Bills and protected money",
            subtitle = "Tap a card to edit. Pay when you are ready.",
            action = {
                IconButton(onClick = { open("commitment") }) {
                    Icon(Icons.Default.Add, "Add bill or reservation")
                }
            },
        )
        if (scope != null)
            Text(
                "Showing bills assigned to this account. Choose All accounts to manage unassigned bills.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        ReservationList(
            data.ledger.commitments,
            pay = { open("pay:" + it.id) },
            edit = { open("editCommit:" + it.id) },
        )
        SectionHeading(
            "Savings goals",
            subtitle =
                if (scope == null) "Big plans, one deposit at a time."
                else "Contributions linked to this account.",
            action = {
                IconButton(onClick = { open("goal") }) {
                    Icon(Icons.Default.Add, "Add savings goal")
                }
            },
        )
        if (data.ledger.goals.isEmpty())
            EmptyState(
                "Something worth saving for",
                if (scope == null) "Create a goal, then link it when you move money into savings."
                else "Link a goal to a transfer involving this account to see contributions here.",
                icon = Icons.Default.Savings,
            )
        data.ledger.goals.forEach { g ->
            ProgressMoneyCard(
                g.goal.name,
                if (scope == null) "Saved toward your goal"
                else "Account contributions toward shared target",
                g.savedMinor,
                g.goal.targetMinor,
                categoryIdentity(g.goal.name),
            )
        }
    }
}

@Composable
internal fun AccountsPage(
    data: FinanceSnapshot,
    today: LocalDate,
    scope: String?,
    open: (String) -> Unit,
    select: (String?) -> Unit,
) {
    Page {
        SectionHeading(
            if (scope == null) "All your money" else data.ledger.accounts.first().account.name,
            subtitle =
                if (scope == null) "One place for every account."
                else "Balance, movement and activity for this account.",
            action = {
                IconButton(onClick = { open("account") }) { Icon(Icons.Default.Add, "Add account") }
            },
        )
        MoneyCard {
            Text(
                if (scope == null) "Combined balance" else "Account balance",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                Money.format(Money.sum(data.ledger.accounts.map { it.balanceMinor })),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (scope == null)
                    "Includes protected accounts. Safe to spend excludes protected money."
                else if (data.ledger.accounts.first().account.included) "Included in safe to spend"
                else "Protected from everyday spending",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (scope != null)
            TextButton(onClick = { select(null) }) {
                Icon(Icons.Default.Wallet, null)
                Spacer(Modifier.width(8.dp))
                Text("Combine all accounts")
            }
        data.ledger.accounts.forEach { row ->
            AccountTile(row, Modifier.fillMaxWidth()) { select(row.account.id) }
            TextButton(onClick = { open("editAccount:" + row.account.id) }) {
                Icon(Icons.Default.Tune, null)
                Spacer(Modifier.width(8.dp))
                Text("Manage " + row.account.name)
            }
        }
        FinancialCharts(data, today)
        SectionHeading("Account activity")
        if (data.ledger.transactions.isEmpty())
            EmptyState(
                "A fresh start",
                "Record a transaction in this account to see its history.",
                icon = Icons.Default.AccountBalanceWallet,
            )
        TransactionRows(data.ledger.transactions, data) { open("editTx:$it") }
    }
}
