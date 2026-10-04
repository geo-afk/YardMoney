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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
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
                            LoadingBudget()
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
                        icon = { Icon(Icons.Default.ErrorOutline, null) },
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
        IdentityBadge(MoneyIdentity(Icons.Default.Savings, 0xFF00865A))
        Text("A little clarity before payday.", style = MaterialTheme.typography.headlineLarge)
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
    val prefs = LocalContext.current.getSharedPreferences("navigation", 0)
    var selectedAccount by rememberSaveable {
        mutableStateOf(prefs.getString("account_scope", null))
    }
    val scope = selectedAccount?.takeIf { id -> data.ledger.accounts.any { it.account.id == id } }
    val view = remember(data, scope) { scopedFinance(data, scope) }
    fun selectAccount(id: String?) {
        selectedAccount = id
        prefs.edit { putString("account_scope", id) }
    }
    val safe = safe(view, model.repo.today)
    val snack = rememberSuccessSnackbar(model.success)
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
    val expanded =
        with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() >= 600.dp }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Column {
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
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IdentityPicker(
                        "Viewing",
                        scope ?: "",
                        listOf(
                            IdentityOption(
                                "",
                                "All accounts",
                                "Combined view",
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
                        Modifier.weight(1f),
                    ) {
                        selectAccount(it.takeIf { it.isNotBlank() })
                    }
                    IconButton(onClick = { tab = "Accounts" }) {
                        Icon(Icons.Default.AccountBalanceWallet, "Account details")
                    }
                }
            }
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
                    // Short landscape windows still expose every destination by scrolling the rail.
                    modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
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
                fun openPage(route: String) {
                    when {
                        route == "accounts" -> tab = "Accounts"
                        route == "activity" -> tab = "Activity"
                        route.startsWith("pay:") -> {
                            payCommitId = route.substringAfter(':')
                            txKind =
                                if (
                                    data.ledger.commitments
                                        .first { it.commitment.id == payCommitId }
                                        .commitment
                                        .kind == "SAVINGS"
                                )
                                    "TRANSFER"
                                else "EXPENSE"
                            form = "transaction"
                        }
                        route == "transaction" -> {
                            payCommitId = null
                            txKind = "EXPENSE"
                            form = route
                        }
                        else -> form = route
                    }
                }
                key(scope, destination) {
                    when (destination) {
                        "Home" ->
                            DashboardPage(view, model.repo.today, scope, ::openPage) {
                                selectAccount(it)
                                tab = "Accounts"
                            }
                        "Activity" ->
                            ActivityPage(view, model.repo.today, ::openPage) { draftId = it }
                        "Plan" -> PlanPage(view, model.repo.today, scope, ::openPage)
                        "Accounts" ->
                            AccountsPage(view, model.repo.today, scope, ::openPage, ::selectAccount)
                        "Shop" -> ShoppingPage(model, view, safe, busy, snack)
                        else -> SettingsPage(model, view) { openPage(it) }
                    }
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
                    initialAccountId = scope,
                )
            }
    if (form == "transaction")
        TransactionForm(
            model,
            data,
            txKind,
            data.ledger.commitments.find { it.commitment.id == payCommitId }?.commitment,
            busy,
            initialAccountId = scope,
        ) {
            form = null
            payCommitId = null
        }
    val close = { form = null }
    key(form) {
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
                        model.repo.addAccount(
                            v[0],
                            c[0],
                            Money.parse(v[1], true),
                            c[1] == "Included",
                        )
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
            "commitment" -> ReservationEditor(model, data, null, scope, busy, close)
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
            "limit" -> LimitEditor(model, data, null, scope, busy, close)
        }
        if (form?.startsWith("editLimit:") == true)
            data.limits
                .find { it.id == form!!.substringAfter(':') }
                ?.let { LimitEditor(model, data, it, scope, busy, close) }

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
                .find { it.commitment.id == form!!.substringAfter(':') }
                ?.let { ReservationEditor(model, data, it, scope, busy, close) }
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
        label.contains("J$") -> MoneyField(label, value, change = change)
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
        Row(
            Modifier.padding(YardSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YardSpace.md),
        ) {
            IdentityBadge(categoryIdentity(title))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(YardSpace.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(amount.replace(",", ",\u200B"), style = MaterialTheme.typography.titleLarge)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (click != null) Icon(Icons.Default.ChevronRight, null)
        }
    }
    if (click != null)
        Card(
            onClick = click,
            modifier = Modifier.fillMaxWidth(),
            shape = YardShape.card,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
        ) {
            body()
        }
    else
        Card(
            Modifier.fillMaxWidth(),
            shape = YardShape.card,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
        ) {
            body()
        }
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
internal fun SafeCard(safe: SafeToSpend, payday: String) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.AccountBalanceWallet, null)
                Text("Safe to spend", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                Money.format(safe.safeMinor).replace(",", ",\u200B"),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (safe.days <= 0) "Payday has arrived. Update your plan."
                else "${safe.days} days to payday · $payday"
            )
            val color = MaterialTheme.colorScheme.onPrimary
            val base = MaterialTheme.colorScheme.onPrimary.copy(alpha = .20f)
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
    val values =
        rememberSaveable(title, initial, saver = stringStatesSaver) {
            initial.map { mutableStateOf(it) }
        }
    val selected =
        rememberSaveable(title, choices, saver = stringStatesSaver) {
            choices.map { mutableStateOf(it.second.first()) }
        }
    AlertDialog(
        onDismissRequest = { if (!busy) close() },
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
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
