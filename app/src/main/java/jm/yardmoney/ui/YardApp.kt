package jm.yardmoney.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import jm.yardmoney.AppModel
import jm.yardmoney.core.*
import jm.yardmoney.data.*

internal val LocalSaving = staticCompositionLocalOf { false }

@Composable
fun YardApp(model: AppModel = viewModel()) {
    val data by model.snapshot.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    YardTheme {
        CompositionLocalProvider(LocalSaving provides busy) {
            Surface(Modifier.fillMaxSize()) {
                if (data == null)
                    Column(
                        Modifier.safeDrawingPadding().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text(
                            "YardMoney",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (error == null) {
                            CircularProgressIndicator()
                            Text("Opening your private budget…")
                        } else {
                            Text(error!!, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(
                                onClick = { model.retryOpenData() },
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                else if (data!!.ledger.profile == null) Setup(model, busy)
                else MainPages(model, data!!, busy)
                if (error != null && data != null)
                    AlertDialog(
                        onDismissRequest = { model.error.value = null },
                        title = { Text("Please check this") },
                        text = { Text(error!!) },
                        confirmButton = {
                            TextButton(
                                onClick = { model.error.value = null },
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text("OK")
                            }
                        },
                    )
            }
        }
    }
}

@Composable
private fun Setup(model: AppModel, busy: Boolean) {
    var name by rememberSaveable { mutableStateOf("") }
    var income by rememberSaveable { mutableStateOf("") }
    var opening by rememberSaveable { mutableStateOf("0") }
    var payday by rememberSaveable { mutableStateOf(model.repo.today.plusDays(14).toString()) }
    var frequency by rememberSaveable { mutableStateOf("FORTNIGHTLY") }
    var first by rememberSaveable { mutableStateOf("15") }
    var second by rememberSaveable { mutableStateOf("30") }
    Page(applySystemInsets = true) {
        Text(
            "YardMoney",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text("A little clarity before payday.", style = MaterialTheme.typography.headlineSmall)
        var appearanceExpanded by rememberSaveable { mutableStateOf(false) }
        TextButton(
            onClick = { appearanceExpanded = !appearanceExpanded },
            shape = MaterialTheme.shapes.small,
        ) {
            Text(if (appearanceExpanded) "Hide appearance settings" else "Personalise appearance")
        }
        if (appearanceExpanded) AppearancePanel()
        Text(
            "Your budget works offline. All amounts are Jamaican dollars. Start with cash; add your other accounts later."
        )
        Field("What should we call you?", name) { name = it }
        Field("Typical take-home pay (J$; optional)", income) { income = it }
        Text(
            "This guides your plan; it does not add money to your account.",
            style = MaterialTheme.typography.bodySmall,
        )
        Choice("How often are you paid?", frequency, PayFrequency.entries.map { it.name }) {
            frequency = it
        }
        Field("Next payday (YYYY-MM-DD)", payday) { payday = it }
        if (frequency == "MONTHLY" || frequency == "TWICE_MONTHLY")
            Field("Monthly pay day (1–31)", first) { first = it }
        if (frequency == "TWICE_MONTHLY")
            Field("Second monthly pay day (1–31)", second) { second = it }
        Field("Cash you have right now (J$)", opening) { opening = it }
        Text("Begin with 50% needs, 30% wants and 20% savings. You can adjust this in Plan.")
        Button(
            enabled = !busy,
            onClick = {
                model.act {
                    val date = LocalDate.parse(payday)
                    require(date >= model.repo.today) { "Choose today or a future payday." }
                    model.repo.onboard(
                        Profile(
                            name = name.trim(),
                            typicalNetMinor = Money.parse(income.ifBlank { "0" }),
                            frequency = frequency,
                            nextPayday = date.toString(),
                            anchorDay = first.toInt(),
                            secondDay = second.toInt(),
                            needsBp = 5000,
                            wantsBp = 3000,
                            savingsBp = 2000,
                            periodStart = model.repo.today.toString(),
                            budgetIncomeMinor = 0,
                        ),
                        Money.parse(opening),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(if (busy) "Saving…" else "Make my plan")
        }
        BackupPanel(model, restoreOnly = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainPages(
    model: AppModel,
    data: FinanceSnapshot,
    busy: Boolean,
) {
    var tab by rememberSaveable { mutableStateOf("Home") }
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    var draftId by rememberSaveable { mutableStateOf<String?>(null) }
    var payCommitId by rememberSaveable { mutableStateOf<String?>(null) }
    var txKind by rememberSaveable { mutableStateOf("EXPENSE") }
    var camera by remember { mutableStateOf(false) }
    var cropUri by rememberSaveable { mutableStateOf<String?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) cropUri = uri.toString()
        }
    val scanProgress by model.scanProgress.collectAsStateWithLifecycle()
    val safe = safe(data, model.repo.today)
    val icons =
        listOf(
            Icons.Default.Home,
            Icons.AutoMirrored.Filled.List,
            Icons.Default.Event,
            Icons.Default.ShoppingCart,
            Icons.Default.MoreHoriz,
        )
    val outlinedIcons =
        listOf(
            Icons.Outlined.Home,
            Icons.AutoMirrored.Outlined.List,
            Icons.Outlined.Event,
            Icons.Outlined.ShoppingCart,
            Icons.Outlined.MoreHoriz,
        )
    if (scanProgress != null)
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Reading receipt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(scanProgress!!)
                }
            },
            confirmButton = {},
        )
    val expanded = LocalConfiguration.current.screenWidthDp >= 600
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.padding(top = 8.dp),
                windowInsets =
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    ),
                title = {
                    Text(if (tab == "Home") "YardMoney" else tab, fontWeight = FontWeight.Bold)
                },
                actions = {
                    if (busy) CircularProgressIndicator(Modifier.size(24.dp))
                    IconButton(
                        enabled = !busy,
                        onClick = {
                            form = "transaction"
                            txKind = "EXPENSE"
                            payCommitId = null
                        },
                    ) {
                        Icon(Icons.Default.Add, "Add transaction")
                    }
                },
            )
        },
        bottomBar = {
            if (!expanded)
                Box(Modifier.navigationBarsPadding()) {
                    NavigationBar(
                        modifier =
                            Modifier.padding(horizontal = 12.dp)
                                .padding(bottom = 4.dp)
                                .height(
                                    if (
                                        androidx.compose.ui.platform.LocalDensity.current
                                            .fontScale > 1.3f
                                    )
                                        96.dp
                                    else 76.dp
                                )
                                .clip(RoundedCornerShape(28.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(28.dp),
                                ),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        listOf("Home", "Activity", "Plan", "Shop", "More").forEachIndexed {
                            index,
                            label ->
                            NavigationWithoutTapEffects {
                                val interaction = remember { MutableInteractionSource() }
                                val focused by interaction.collectIsFocusedAsState()
                                NavigationBarItem(
                                    interactionSource = interaction,
                                    colors =
                                        NavigationBarItemDefaults.colors(
                                            selectedIconColor =
                                                MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor =
                                                androidx.compose.ui.graphics.Color.Transparent,
                                            unselectedIconColor =
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor =
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    selected = tab == label,
                                    onClick = { tab = label },
                                    icon = {
                                        NavigationSelectionIcon(
                                            tab == label,
                                            icons[index],
                                            outlinedIcons[index],
                                            focused,
                                        )
                                    },
                                    label = { NavigationSelectionLabel(label, tab == label) },
                                )
                            }
                        }
                    }
                }
        },
    ) { inset ->
        Row(Modifier.padding(inset)) {
            if (expanded)
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    listOf("Home", "Activity", "Plan", "Shop", "More").forEachIndexed { index, label
                        ->
                        NavigationWithoutTapEffects {
                            val interaction = remember { MutableInteractionSource() }
                            val focused by interaction.collectIsFocusedAsState()
                            NavigationRailItem(
                                interactionSource = interaction,
                                colors =
                                    NavigationRailItemDefaults.colors(
                                        selectedIconColor =
                                            MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor =
                                            androidx.compose.ui.graphics.Color.Transparent,
                                        unselectedIconColor =
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor =
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                selected = tab == label,
                                onClick = { tab = label },
                                icon = {
                                    NavigationSelectionIcon(
                                        tab == label,
                                        icons[index],
                                        outlinedIcons[index],
                                        focused,
                                    )
                                },
                                label = { NavigationSelectionLabel(label, tab == label) },
                            )
                        }
                    }
                }
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    androidx.compose.animation.EnterTransition.None togetherWith
                        androidx.compose.animation.ExitTransition.None
                },
                label = "Page transition",
            ) { destination ->
                when (destination) {
                    "Home" ->
                        Page {
                            Text(
                                "Hello, ${data.ledger.profile!!.name.ifBlank{"neighbour"}}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            SafeCard(safe, data.ledger.profile!!.nextPayday)
                            BudgetOverview(data, model.repo.today)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        form = "transaction"
                                        txKind = "EXPENSE"
                                        payCommitId = null
                                    },
                                    Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Text("Add expense")
                                }
                                OutlinedButton(
                                    onClick = { form = "scan" },
                                    Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Text("Scan receipt")
                                }
                            }
                            Card {
                                Column(
                                    Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "What is protected?",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    AmountRow("Included account balances", safe.availableMinor)
                                    AmountRow(
                                        "Bills, savings and debt reserved",
                                        safe.protectedMinor,
                                    )
                                    Text(
                                        "Reservations include unpaid items due by payday, plus items with no due date. Money already in protected accounts is excluded.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            Text("Coming up", style = MaterialTheme.typography.titleLarge)
                            data.ledger.commitments
                                .filter { it.remainingMinor > 0 }
                                .take(4)
                                .forEach { c ->
                                    Record(
                                        c.commitment.name,
                                        "${c.commitment.dueDate?:"No due date"} · ${c.commitment.kind.lowercase()}",
                                        Money.format(c.remainingMinor),
                                    ) {
                                        payCommitId = c.commitment.id
                                        txKind =
                                            if (c.commitment.kind == "SAVINGS") "TRANSFER"
                                            else "EXPENSE"
                                        form = "transaction"
                                    }
                                }
                            if (data.ledger.commitments.none { it.remainingMinor > 0 })
                                Text(
                                    "No bills reserved yet. Add what is due before payday in Plan."
                                )
                            Text("Your accounts", style = MaterialTheme.typography.titleLarge)
                            data.ledger.accounts.forEach {
                                Record(
                                    it.account.name,
                                    if (it.account.included) "Available to spend" else "Protected",
                                    Money.format(it.balanceMinor),
                                )
                            }
                        }
                    "Activity" ->
                        Page {
                            Text("Money in and out", style = MaterialTheme.typography.headlineSmall)
                            val p = data.ledger.profile!!
                            val current =
                                data.ledger.transactions.filter {
                                    it.date >= p.periodStart &&
                                        it.date <= model.repo.today.toString()
                                }
                            AmountRow(
                                "Received this period",
                                Money.sum(
                                    current.filter { it.kind == "INCOME" }.map { it.amountMinor }
                                ),
                            )
                            AmountRow(
                                "Spent, less refunds",
                                Money.sum(
                                    current
                                        .filter { it.kind == "EXPENSE" || it.kind == "REFUND" }
                                        .map {
                                            if (it.kind == "REFUND") -it.amountMinor
                                            else it.amountMinor
                                        }
                                ),
                            )
                            Choice(
                                "Record",
                                txKind,
                                listOf("EXPENSE", "INCOME", "TRANSFER", "REFUND", "ADJUSTMENT"),
                            ) {
                                txKind = it
                                payCommitId = null
                                form = "transaction"
                            }
                            if (data.ledger.transactions.isEmpty())
                                Text("Your first transaction will appear here.")
                            data.ledger.transactions.forEach { t ->
                                Record(
                                    t.description.ifBlank { t.category },
                                    "${t.date} · ${t.kind.lowercase()} · ${t.bucket.lowercase()}",
                                    Money.format(t.amountMinor),
                                ) {
                                    form = "editTx:${t.id}"
                                }
                            }
                            Text("Receipt drafts", style = MaterialTheme.typography.titleLarge)
                            data.receipt.drafts.forEach { d ->
                                Record("Review receipt", d.createdDate, "Draft") { draftId = d.id }
                            }
                            data.receipt.receipts.forEach { r ->
                                Record(
                                    r.merchant,
                                    "${r.date} · ${r.branch} · reviewed",
                                    Money.format(r.totalMinor),
                                ) {
                                    form = "receipt:${r.id}"
                                }
                            }
                        }
                    "Plan" ->
                        Page {
                            val p = data.ledger.profile!!
                            Text("Your payday plan", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Next payday: ${p.nextPayday} · ${p.frequency.lowercase().replace('_',' ')}"
                            )
                            OutlinedButton(
                                onClick = { form = "payday" },
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text("Start a new pay period")
                            }
                            BudgetOverview(
                                data,
                                model.repo.today,
                                onChangeSplit = { form = "split" },
                            )
                            Text("Category limits", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "These guide spending within your percentage plan. They do not subtract another reservation from safe to spend."
                            )
                            OutlinedButton(
                                onClick = { form = "limit" },
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text("Set a category limit")
                            }
                            val periodIds =
                                data.ledger.transactions
                                    .filter {
                                        it.date >= p.periodStart &&
                                            it.date <= model.repo.today.toString()
                                    }
                                    .map { it.id }
                                    .toSet()
                            data.limits.forEach { limit ->
                                val used =
                                    Money.sum(
                                        data.splits
                                            .filter {
                                                it.transactionId in periodIds &&
                                                    it.bucket == limit.bucket &&
                                                    it.category.equals(limit.category, true)
                                            }
                                            .map { it.amountMinor }
                                    )
                                AmountRow(
                                    "${limit.category} · remaining this period",
                                    limit.limitMinor - used,
                                )
                            }
                            Text(
                                "Bills and protected money",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Button(
                                onClick = { form = "commitment" },
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text("Add bill or reservation")
                            }
                            data.ledger.commitments.forEach { c ->
                                Record(
                                    c.commitment.name,
                                    "${c.commitment.dueDate?:"No date"} · ${if(c.remainingMinor==0L)"Paid" else "Reserved"}",
                                    Money.format(c.remainingMinor),
                                ) {
                                    if (c.remainingMinor > 0) {
                                        payCommitId = c.commitment.id
                                        txKind =
                                            if (c.commitment.kind == "SAVINGS") "TRANSFER"
                                            else "EXPENSE"
                                        form = "transaction"
                                    }
                                }
                                TextButton(
                                    onClick = { form = "editCommit:${c.commitment.id}" },
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Text("Edit reservation")
                                }
                            }
                            Text("Savings goals", style = MaterialTheme.typography.titleLarge)
                            OutlinedButton(
                                onClick = { form = "goal" },
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text("Add savings goal")
                            }
                            data.ledger.goals.forEach { g ->
                                Card {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(g.goal.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${Money.format(g.savedMinor)} of ${Money.format(g.goal.targetMinor)}"
                                        )
                                        LinearProgressIndicator(
                                            progress = {
                                                (g.savedMinor.toDouble() / g.goal.targetMinor)
                                                    .toFloat()
                                                    .coerceIn(0f, 1f)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Text(
                                            "Link this goal when transferring into protected savings or withdrawing back to a spendable account. Progress shows recorded contributions, not a verified account balance.",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    "Shop" -> ShoppingPage(model, data, safe, busy)
                    else -> SettingsPage(model, data) { form = it }
                }
            }
        }
    }
    if (form == "scan")
        AlertDialog(
            onDismissRequest = { form = null },
            title = { Text("Capture a receipt") },
            text = {
                Text(
                    "Use a flat receipt in good light. Every result stays a draft until you check it. You can also record a manual expense."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        form = null
                        camera = true
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Camera")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        form = null
                        picker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Import photo")
                }
            },
        )
    if (camera)
        ReceiptCamera(
            onDismiss = { camera = false },
            onPhoto = { uri ->
                camera = false
                cropUri = uri.toString()
            },
        )
    cropUri?.let { uri ->
        ReceiptCrop(model, Uri.parse(uri), { cropUri = null }) { prepared ->
            cropUri = null
            model.importReceipt(prepared) { draftId = it }
        }
    }
    if (draftId != null)
        data.receipt.drafts
            .find { it.id == draftId }
            ?.let {
                ReceiptReview(
                    model,
                    data,
                    it,
                    busy,
                    close = { draftId = null },
                    rescanned = { newId -> draftId = newId },
                )
            }
    if (form == "transaction")
        TransactionForm(
            model,
            data,
            txKind,
            data.ledger.commitments.find { it.commitment.id == payCommitId }?.commitment,
            busy,
        ) {
            form = null
            payCommitId = null
        }
    val close = { form = null }
    when (form) {
        "account" ->
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
                    model.repo.addAccount(v[0], c[0], Money.parse(v[1], true), c[1] == "Included")
                }
            }
        "goal" ->
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
        "commitment" ->
            SimpleForm(
                "Reserve money",
                listOf("Name", "Amount (J$)", "Due date (YYYY-MM-DD; blank = protect now)"),
                listOf("", "", ""),
                busy,
                close,
                choices =
                    listOf(
                        "Kind" to listOf("BILL", "SAVINGS", "DEBT", "RESERVE"),
                        "Repeat" to listOf("ONCE", "WEEKLY", "FORTNIGHTLY", "MONTHLY"),
                    ),
            ) { v, c ->
                model.act(close) {
                    model.repo.addCommitment(
                        v[0],
                        c[0],
                        Money.positive(v[1]),
                        v[2].takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                        frequency = c[1],
                    )
                }
            }
        "split" -> BudgetForm(model, data.ledger.profile!!, busy, close)
        "payday" ->
            SimpleForm(
                "Start a new period",
                listOf("Next payday (YYYY-MM-DD)"),
                listOf(nextPayday(data.ledger.profile!!, model.repo.today).toString()),
                busy,
                close,
                description =
                    "Start the budget period today. Record received pay separately in Activity; this change adds no money.",
            ) { v, _ ->
                model.act(close) { model.repo.confirmPayday(LocalDate.parse(v[0]), 0) }
            }
        "limit" ->
            SimpleForm(
                "Category spending limit",
                listOf("Category", "Limit per period (J$)"),
                listOf("Groceries", ""),
                busy,
                close,
                choices = listOf("Budget group" to listOf("NEEDS", "WANTS", "SAVINGS")),
            ) { v, c ->
                model.act(close) { model.repo.setCategoryLimit(v[0], c[0], Money.parse(v[1])) }
            }
    }
    if (form?.startsWith("editTx:") == true) {
        val t = data.ledger.transactions.first { it.id == form!!.substringAfter(':') }
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
                            listOf("NEEDS", "WANTS", "SAVINGS").filter { it != t.bucket },
                    "Action" to listOf("Save edits", "Delete record"),
                ),
            description =
                "Amounts and account movements stay together. To correct an amount, delete this record and re-enter it.",
        ) { v, c ->
            if (c[1] == "Delete record") form = "delete:${t.id}"
            else
                model.act(close) {
                    model.repo.editRecord(t.id, v[0], v[1], c[0], LocalDate.parse(v[2]))
                }
        }
    }
    if (form?.startsWith("editAccount:") == true) {
        val account =
            data.ledger.accounts.first { it.account.id == form!!.substringAfter(':') }.account
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
            model.act(close) { model.repo.editAccount(account.id, v[0], c[0] == "Included") }
        }
    }
    if (form?.startsWith("editCommit:") == true)
        data.ledger.commitments
            .firstOrNull { it.commitment.id == form!!.substringAfter(':') }
            ?.let { balance ->
                val c = balance.commitment
                SimpleForm(
                    "Edit this reservation",
                    listOf("Name", "Amount (J$)", "Due date (YYYY-MM-DD; optional)"),
                    listOf(c.name, Money.input(c.amountMinor), c.dueDate ?: ""),
                    busy,
                    close,
                    choices =
                        listOf(
                            "Action" to
                                listOf(
                                    "Save edits",
                                    if (c.occurrenceKey.contains('@')) "Cancel unpaid series"
                                    else "Remove reservation",
                                )
                        ),
                    description =
                        "Edits apply to this occurrence. Cancellation removes unpaid reservations; paid history stays.",
                ) { v, choice ->
                    model.act(close) {
                        if (choice[0] == "Save edits")
                            model.repo.editCommitment(
                                c.id,
                                v[0],
                                Money.positive(v[1]),
                                v[2].takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                            )
                        else model.repo.removeCommitment(c.id)
                    }
                }
            }
    if (form?.startsWith("delete:") == true) {
        val id = form!!.substringAfter(':')
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
                TextButton(
                    onClick = close,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Keep")
                }
            },
        )
    }
    if (form?.startsWith("receipt:") == true)
        data.receipt.receipts
            .find { it.id == form!!.substringAfter(':') }
            ?.let { ReceiptDetails(model, it, busy, close) }
}

private fun nextPayday(p: Profile, today: LocalDate): LocalDate {
    var d = LocalDate.parse(p.nextPayday)
    if (p.frequency == "IRREGULAR") return today.plusDays(14)
    do {
        d =
            PaySchedule.nextAfter(d, PayFrequency.valueOf(p.frequency), p.anchorDay, p.secondDay)
                ?: today.plusDays(14)
    } while (d <= today)
    return d
}

internal fun safe(data: FinanceSnapshot, today: LocalDate): SafeToSpend =
    BudgetEngine.safeToSpend(
        Money.sum(data.ledger.accounts.filter { it.account.included }.map { it.balanceMinor }),
        data.ledger.commitments.map {
            Reserve(
                it.commitment.id,
                it.remainingMinor,
                it.commitment.dueDate?.let(LocalDate::parse),
            )
        },
        today,
        LocalDate.parse(data.ledger.profile!!.nextPayday),
    )

@Composable
internal fun Page(applySystemInsets: Boolean = false, content: @Composable ColumnScope.() -> Unit) =
    Box(
        if (applySystemInsets) Modifier.fillMaxSize().safeDrawingPadding()
        else Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 840.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(LocalLayoutSpacing.current),
            content = content,
        )
    }

@Composable
internal fun Field(label: String, value: String, change: (String) -> Unit) {
    when {
        label.contains("YYYY-MM-DD") ->
            DateDropdown(
                label,
                value,
                optional = label.contains("optional", true) || label.contains("blank", true),
                change = change,
            )
        label.contains("J$") -> MoneyField(label, value, change)
        label.contains("pay day", true) ->
            Choice(label, value, (1..31).map { it.toString() }, change)
        label.contains("%") ||
            label.contains("quantity", true) ||
            label.contains("Package size", true) ||
            label.contains("pay day", true) -> NumberField(label, value, change)
        else ->
            OutlinedTextField(
                value = value,
                onValueChange = change,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
                enabled = !LocalSaving.current,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                shape = MaterialTheme.shapes.small,
            )
    }
}

@Composable
internal fun Choice(label: String, value: String, options: List<String>, change: (String) -> Unit) =
    DropdownField(
        label,
        value,
        options.associateWith {
            it.lowercase().replace('_', ' ').replaceFirstChar { c -> c.titlecase() }
        },
        change,
    )

@Composable
internal fun Record(title: String, detail: String, amount: String, click: (() -> Unit)? = null) {
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(amount.replace(",", ",\u200B"), style = MaterialTheme.typography.titleLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (click != null)
        OutlinedCard(onClick = click, modifier = Modifier.fillMaxWidth(), content = { body() })
    else OutlinedCard(Modifier.fillMaxWidth(), content = { body() })
}

@Composable
internal fun AmountRow(label: String, amount: Long) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            Money.format(amount).replace(",", ",\u200B"),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun SafeCard(safe: SafeToSpend, payday: String) {
    Card(
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Safe to spend", style = MaterialTheme.typography.titleMedium)
            Text(
                Money.format(safe.safeMinor).replace(",", ",\u200B"),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (safe.days <= 0) "Payday has arrived. Update your plan."
                else "${safe.days} days to payday · $payday"
            )
            val color = MaterialTheme.colorScheme.primary
            val base = MaterialTheme.colorScheme.surfaceVariant
            val motion = LocalMotion.current
            val fraction =
                if (safe.availableMinor > 0)
                    (safe.safeMinor.toDouble() / safe.availableMinor).toFloat().coerceIn(0f, 1f)
                else 0f
            val shown by
                animateFloatAsState(
                    fraction,
                    animationSpec = motion.floatSpec(),
                    label = "Safe balance",
                )
            LinearProgressIndicator(
                progress = { shown },
                color = color,
                trackColor = base,
                modifier = Modifier.fillMaxWidth().height(14.dp),
            )
            Text(
                safe.dailyMinor?.let { "About ${Money.format(it)} a day" }
                    ?: if (safe.safeMinor < 0)
                        "Your commitments exceed available money. Review your plan."
                    else "No daily spending allowance right now."
            )
        }
    }
}

@Composable
internal fun SimpleForm(
    title: String,
    labels: List<String>,
    initial: List<String>,
    busy: Boolean,
    close: () -> Unit,
    choices: List<Pair<String, List<String>>> = emptyList(),
    description: String? = null,
    save: (List<String>, List<String>) -> Unit,
) {
    val stringStatesSaver =
        listSaver<List<MutableState<String>>, String>(
            save = { states -> states.map { it.value } },
            restore = { strings -> strings.map { mutableStateOf(it) } },
        )
    val values = rememberSaveable(saver = stringStatesSaver) { initial.map { mutableStateOf(it) } }
    val selected =
        rememberSaveable(saver = stringStatesSaver) {
            choices.map { mutableStateOf(it.second.first()) }
        }
    AlertDialog(
        onDismissRequest = { if (!busy) close() },
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (description != null) Text(description)
                labels.forEachIndexed { i, label ->
                    Field(label, values[i].value) { values[i].value = it }
                }
                choices.forEachIndexed { i, c ->
                    Choice(c.first, selected[i].value, c.second) { selected[i].value = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = { save(values.map { it.value }, selected.map { it.value }) },
                shape = MaterialTheme.shapes.small,
            ) {
                Text(if (busy) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = close,
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Cancel")
            }
        },
    )
}
