package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import jm.yardmoney.AppModel
import jm.yardmoney.core.*
import jm.yardmoney.data.*

/**
 * Shown in place of the app while the local data cannot be opened. Retry covers passing glitches;
 * "Start over" is the way out when the key or database is permanently unreadable, so a portable
 * backup can be restored afterwards.
 */
@Composable
internal fun DataUnavailable(model: AppModel, message: String) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    val busy by model.busy.collectAsState()
    Text(message, color = MaterialTheme.colorScheme.error)
    OutlinedButton(onClick = { model.retryOpenData() }, shape = MaterialTheme.shapes.small) {
        Text("Retry")
    }
    TextButton(
        onClick = {
            typed = ""
            confirming = true
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Text("Start over…")
    }
    if (confirming)
        AlertDialog(
            onDismissRequest = { if (!busy) confirming = false },
            title = { Text("Erase unreadable data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Try Retry first. Starting over permanently deletes the data on this device that YardMoney cannot open, together with its encryption key. Backups you saved elsewhere are not touched, and you can restore one afterwards."
                    )
                    Field("Type DELETE to confirm", typed) { typed = it }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && typed == "DELETE",
                    onClick = {
                        confirming = false
                        model.startOver()
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Erase and start over")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { confirming = false },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Cancel")
                }
            },
        )
}
