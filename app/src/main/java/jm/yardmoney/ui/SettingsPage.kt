package jm.yardmoney.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import jm.yardmoney.AppModel
import jm.yardmoney.core.Money
import jm.yardmoney.data.FinanceSnapshot

@Composable
internal fun SettingsPage(model: AppModel, data: FinanceSnapshot, open: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    Page {
        Text("Make it yours", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Your look, your money, your preferences.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        fun toggle(title: String) {
            expanded = if (expanded == title) null else title
        }
        SettingsGroup(
            "Appearance",
            "Theme, corners, spacing and animation",
            Icons.Default.Palette,
            expanded == "Appearance",
            { toggle("Appearance") },
        ) {
            AppearancePanel()
        }
        SettingsGroup(
            "Accounts",
            "Individual accounts or one combined view",
            Icons.Default.AccountBalanceWallet,
            expanded == "Accounts",
            { toggle("Accounts") },
        ) {
            FilledTonalButton(onClick = { open("accounts") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AccountBalanceWallet, null)
                Spacer(Modifier.width(8.dp))
                Text("Open accounts overview")
            }
            data.ledger.accounts.forEach { entry ->
                Record(
                    entry.account.name,
                    "${entry.account.kind.lowercase()} · ${if(entry.account.included) "Spendable" else "Protected"}",
                    Money.format(entry.balanceMinor),
                ) {
                    open("editAccount:${entry.account.id}")
                }
            }
            FilledTonalButton(
                onClick = { open("account") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add account")
            }
        }
        SettingsGroup(
            "Insights & search",
            "Spending, trends and saved records",
            Icons.Default.BarChart,
            expanded == "Insights & search",
            { toggle("Insights & search") },
        ) {
            ReportsPanel(model, data)
        }
        SettingsGroup(
            "Receipts",
            "Scan, import and review purchases",
            Icons.AutoMirrored.Filled.ReceiptLong,
            expanded == "Receipts",
            { toggle("Receipts") },
        ) {
            Text("Scans stay private drafts until you review and confirm them.")
            FilledTonalButton(
                onClick = { open("scan") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Default.DocumentScanner, null)
                Spacer(Modifier.width(8.dp))
                Text("Scan or import receipt")
            }
            Text(
                "Open Activity for saved receipts and unfinished drafts.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SettingsGroup(
            "Notifications",
            "Private reminders for upcoming bills",
            Icons.Default.Notifications,
            expanded == "Notifications",
            { toggle("Notifications") },
        ) {
            ReminderSetting()
        }
        SettingsGroup(
            "Security & privacy",
            "App lock and on-device protection",
            Icons.Default.Shield,
            expanded == "Security & privacy",
            { toggle("Security & privacy") },
        ) {
            AppLockSetting()
            Text(
                "Your database and receipt photos are encrypted on this device. Receipt recognition runs offline. No YardMoney usage tracking, bank connections or account required."
            )
            Text(
                "Scanning uses Google's ML Kit. Review its privacy information alongside YardMoney's notices.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "https://developers.google.com/ml-kit/android-data-disclosure"
                            ),
                        )
                    )
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Receipt-scanning privacy information")
            }
        }
        SettingsGroup(
            "Backup & data",
            "Encrypted backup, export and deletion",
            Icons.Default.Backup,
            expanded == "Backup & data",
            { toggle("Backup & data") },
        ) {
            BackupPanel(model)
            DataControls(model)
        }
        SettingsGroup(
            "About YardMoney",
            "Version and account availability",
            Icons.Default.Info,
            expanded == "About YardMoney",
            { toggle("About YardMoney") },
        ) {
            Text(
                "YardMoney · ${jm.yardmoney.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Optional accounts and cloud backup are planned for the first public release. This pilot supports encrypted file backups; no remote account is connected."
            )
        }
    }
}

@Composable
internal fun SettingsGroup(
    title: String,
    summary: String,
    icon: ImageVector,
    expanded: Boolean,
    toggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = LocalMotion.current
    val rotation by
        animateFloatAsState(
            if (expanded) 180f else 0f,
            animationSpec = motion.floatSpec(),
            label = "Settings chevron",
        )
    Card(
        Modifier.fillMaxWidth().animateContentSize(tween(motion.duration())),
        shape = RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Surface(
            onClick = toggle,
            color =
                if (expanded) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor =
                if (expanded) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier.fillMaxWidth().semantics {
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
        ) {
            Row(
                Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(24.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(summary, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.ExpandMore, null, Modifier.rotate(rotation))
            }
        }
        AnimatedVisibility(
            expanded,
            enter = fadeIn(tween(motion.duration())) + expandVertically(tween(motion.duration())),
            exit = fadeOut(tween(motion.duration())) + shrinkVertically(tween(motion.duration())),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(LocalLayoutSpacing.current),
                content = content,
            )
        }
    }
}
