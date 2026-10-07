package jm.yardmoney.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import jm.yardmoney.AppModel
import jm.yardmoney.security.PortableBackup

// Retain the password across rotation in memory, without putting it in saved bundles or
// preferences.
internal class BackupFormViewModel : ViewModel() {
    val mode = mutableStateOf<String?>(null)
    val password = mutableStateOf("")
    val acknowledged = mutableStateOf(false)

    override fun onCleared() {
        password.value = ""
    }
}

@Composable
internal fun BackupPanel(model: AppModel, restoreOnly: Boolean = false) {
    val form: BackupFormViewModel = viewModel()
    var mode by form.mode
    var password by form.password
    var acknowledged by form.acknowledged
    val busy by model.busy.collectAsState()
    val save =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri: Uri? ->
            if (uri != null) {
                val pass = password.toCharArray()
                password = ""
                model.act {
                    val bytes = PortableBackup(model.app).export(pass)
                    // Failed/cancelled document writes must wipe the in-memory backup too.
                    try {
                        model.app.contentResolver.openOutputStream(uri, "w")?.use {
                            it.write(bytes)
                        } ?: error("Could not write backup.")
                    } finally {
                        bytes.fill(0)
                        pass.fill('\u0000')
                    }
                }
            } else password = ""
        }
    val load =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                val pass = password.toCharArray()
                password = ""
                model.act {
                    // A provider may fail before decoding; clear the password on that path as well.
                    try {
                        val bytes =
                            model.app.contentResolver.openInputStream(uri)?.use { input ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                var count = 0
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    count += n
                                    require(count <= 50_000_064) { "Backup exceeds 50 MB." }
                                    output.write(buffer, 0, n)
                                }
                                output.toByteArray()
                            } ?: error("Could not read backup.")
                        try {
                            PortableBackup(model.app).restore(bytes, pass)
                        } finally {
                            bytes.fill(0)
                        }
                    } finally {
                        pass.fill('\u0000')
                    }
                }
            } else password = ""
        }
    Text("Encrypted file backup", style = MaterialTheme.typography.titleLarge)
    Text(
        "Save your records and receipt previews using a password you keep. Photos are not included or restored from older backups. A lost password cannot be recovered. Restore replaces the current ledger after validation."
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!restoreOnly)
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    mode = "Save"
                    password = ""
                    acknowledged = false
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Save backup")
            }
        OutlinedButton(
            enabled = !busy,
            onClick = {
                mode = "Restore"
                password = ""
                acknowledged = false
            },
            shape = MaterialTheme.shapes.small,
        ) {
            Text("Restore")
        }
    }
    if (mode != null)
        EditFormSheet(
            titleText = "$mode encrypted backup",
            busy = busy,
            dirty = password.isNotBlank() || acknowledged,
            keyValue = if (password.isBlank()) "Password not specified" else "Password entered",
            onDismissRequest = {
                mode = null
                password = ""
            },
            title = { Text("${mode} encrypted backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        password,
                        { password = it },
                        label = { Text("Backup password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    )
                    if (mode == "Restore") {
                        Text("Receipt photos from older backups are not restored. Their saved text and items remain available in receipt previews.")
                        Tick("Replace current data with this backup", acknowledged) {
                            acknowledged = it
                        }
                    }
                    else Text("Use at least 12 characters and store this password safely.")
                }
            },
            confirmButton = {
                TextButton(
                    enabled =
                        password.isNotBlank() &&
                            (mode == "Restore" && acknowledged ||
                                mode == "Save" && password.length >= 12),
                    onClick = {
                        val action = mode
                        mode = null
                        if (action == "Save")
                            save.launch("YardMoney-${model.repo.today}.yardbackup")
                        else load.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Choose file")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = LocalEditDismiss.current,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Cancel")
                }
            },
        )
}
