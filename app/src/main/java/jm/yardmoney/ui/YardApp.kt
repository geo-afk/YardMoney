package jm.yardmoney.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import jm.yardmoney.AppModel
import jm.yardmoney.Prefs
import jm.yardmoney.core.*
import jm.yardmoney.data.*
import jm.yardmoney.navigationPrefs

@Composable
fun YardApp(model: AppModel = viewModel()) {
    val data by model.snapshot.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    // Delegated State cannot be smart-cast; read once so the branches below are null-safe.
    val current: FinanceSnapshot? = data
    val message: String? = error
    YardTheme {
        CompositionLocalProvider(LocalSaving provides busy) {
            Surface(Modifier.fillMaxSize()) {
                when {
                    current == null ->
                        Column(
                            Modifier.safeDrawingPadding().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Text(
                                "YardMoney",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (message == null) LoadingBudget()
                            else DataUnavailable(model, message)
                        }
                    current.ledger.profile == null -> SetupPage(model, busy)
                    else -> MainPages(model, current, busy)
                }
                if (message != null && current != null)
                    AlertDialog(
                        onDismissRequest = { model.error.value = null },
                        icon = { Icon(Icons.Default.ErrorOutline, null) },
                        title = { Text("Please check this") },
                        text = { Text(message) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainPages(
    model: AppModel,
    data: FinanceSnapshot,
    busy: Boolean,
) {
    var tab by rememberSaveable { mutableStateOf("Home") }
    // Back from another tab returns to Home before leaving the app. Registered first, so the
    // shopping page's own handlers (declared later) take priority while they are active.
    BackHandler(enabled = tab != "Home") { tab = "Home" }
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    var draftId by rememberSaveable { mutableStateOf<String?>(null) }
    var payCommitId by rememberSaveable { mutableStateOf<String?>(null) }
    var quickAdd by rememberSaveable { mutableStateOf(false) }
    var txKind by rememberSaveable { mutableStateOf("EXPENSE") }
    var camera by remember { mutableStateOf(false) }
    var cropUri by rememberSaveable { mutableStateOf<String?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) cropUri = uri.toString()
        }
    val scanProgress by model.scanProgress.collectAsStateWithLifecycle()
    val prefs = LocalContext.current.navigationPrefs()
    var selectedAccount by rememberSaveable {
        mutableStateOf(prefs.getString(Prefs.ACCOUNT_SCOPE, null))
    }
    val scope = selectedAccount?.takeIf { id -> data.ledger.accounts.any { it.account.id == id } }
    val view = remember(data, scope) { scopedFinance(data, scope) }
    fun selectAccount(id: String?) {
        selectedAccount = id
        prefs.edit { putString(Prefs.ACCOUNT_SCOPE, id) }
    }
    val today = model.repo.today
    // Transition content may retain a callback while a database snapshot changes.
    val latestData by rememberUpdatedState(data)
    val safe =
        remember(view.ledger.accounts, view.ledger.commitments, view.ledger.profile, today) {
            safe(view, today)
        }
    val snack = rememberSuccessSnackbar(model.success)
    scanProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Reading receipt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(progress)
                }
            },
            confirmButton = {},
        )
    }
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
                MoneyNavigation(tab, { tab = it }, { quickAdd = true }, addEnabled = !busy)
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
                    moneyDestinations.forEach { destination ->
                        val label = destination.label
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
                                        destination.filled,
                                        destination.outlined,
                                        focused,
                                    )
                                },
                                label = { NavigationSelectionLabel(label, tab == label) },
                            )
                        }
                    }
                }
            MenuTransition(destination = tab, modifier = Modifier.weight(1f)) { destination ->
                fun openPage(route: String) {
                    when {
                        route == "accounts" -> tab = "Accounts"
                        route == "activity" -> tab = "Activity"
                        route.startsWith("pay:") -> {
                            payCommitId = route.substringAfter(':')
                            val commitment =
                                latestData.ledger.commitments
                                    .firstOrNull { it.commitment.id == payCommitId }
                                    ?.commitment
                            if (commitment == null) {
                                form = "missingRecord"
                                payCommitId = null
                                return
                            }
                            txKind = if (commitment.kind == "SAVINGS") "TRANSFER" else "EXPENSE"
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

    if (quickAdd)
        MoneyQuickAddSheet(
            dismiss = { quickAdd = false },
            choose = { action ->
                quickAdd = false
                if (action == "scan") form = "scan"
                else {
                    txKind = action
                    payCommitId = null
                    form = "transaction"
                }
            },
        )

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
    if (form == "transaction") {
        val payable = data.ledger.commitments.find { it.commitment.id == payCommitId }?.commitment
        // A cancelled bill must not re-open as an unrelated expense after state restoration.
        if (payCommitId != null && payable == null)
            MissingRecordNotice {
                form = null
                payCommitId = null
            }
        else
            TransactionForm(
                model,
                data,
                txKind,
                payable,
                busy,
                initialAccountId = scope,
            ) {
                form = null
                payCommitId = null
            }
    }
    MainForms(model, data, scope, busy, form) { form = it }
}
