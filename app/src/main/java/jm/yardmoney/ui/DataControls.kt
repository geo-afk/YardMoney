package jm.yardmoney.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import jm.yardmoney.AppModel
import jm.yardmoney.core.*
import jm.yardmoney.reminders.BillReminder

@Composable
internal fun DataControls(model: AppModel) {
    var erase by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf("") }
    var export by remember { mutableStateOf(false) }
    val busy by model.busy.collectAsState()
    val file =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri
            ->
            if (uri != null)
                model.act {
                    val csv = buildString {
                        append("date,type,description,category,budget_group,amount,currency\r\n")
                        model.repo.dao.readTransactions().forEach { t ->
                            append(
                                listOf(t.date, t.kind, t.description, t.category, t.bucket)
                                    .joinToString(",", transform = Csv::text)
                            )
                            append(',')
                            append(Money.input(t.amountMinor))
                            append(",JMD\r\n")
                        }
                    }
                    model.app.contentResolver
                        .openOutputStream(uri, "w")
                        ?.bufferedWriter(Charsets.UTF_8)
                        ?.use { it.write(csv) } ?: error("Could not write the CSV.")
                }
        }
    Text("Your data", style = MaterialTheme.typography.titleLarge)
    OutlinedButton(
        enabled = !busy,
        onClick = { export = true },
        shape = MaterialTheme.shapes.small,
    ) {
        Text("Export transaction CSV")
    }
    TextButton(
        enabled = !busy,
        onClick = {
            erase = true
            confirm = ""
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Text("Delete local records")
    }
    if (export)
        AlertDialog(
            onDismissRequest = { export = false },
            title = { Text("Export transactions?") },
            text = {
                Text(
                    "The CSV includes dates, descriptions, categories and amounts. It is unencrypted. Save it somewhere private. Transfers are labelled separately; receipt images and raw text are excluded."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        export = false
                        file.launch("YardMoney-transactions-${model.repo.today}.csv")
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Choose file")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { export = false },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Cancel")
                }
            },
        )
    if (erase)
        AlertDialog(
            onDismissRequest = { if (!busy) erase = false },
            title = { Text("Delete all local records?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This deletes your accounts, transactions, budgets, bills, goals, shopping lists, receipts and price history on this device. Backups you saved elsewhere remain. Save an encrypted backup first if you want to restore them."
                    )
                    Field("Type DELETE to confirm", confirm) { confirm = it }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && confirm == "DELETE",
                    onClick = {
                        model.act({ erase = false }) {
                            model.app.database.openHelper.writableDatabase.execSQL(
                                "PRAGMA secure_delete=ON"
                            )
                            model.app.database.clearAllTables()
                            model.app.storage.deleteAllReceiptFiles()
                            BillReminder.setEnabled(model.app, false)
                            model.app
                                .getSharedPreferences("appearance", 0)
                                .edit()
                                .putBoolean("lock", false)
                                .apply()
                        }
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(if (busy) "Deleting…" else "Delete local records")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { erase = false },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Cancel")
                }
            },
        )
}
