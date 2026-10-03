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
import jm.yardmoney.AppModel
import jm.yardmoney.security.PortableBackup

@Composable
internal fun BackupPanel(model: AppModel, restoreOnly: Boolean = false) {
    var mode by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }
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
                    model.app.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                        ?: error("Could not write backup.")
                    bytes.fill(0)
                }
            }
        }
    val load =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                val pass = password.toCharArray()
                password = ""
                model.act {
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
                    PortableBackup(model.app).restore(bytes, pass)
                    bytes.fill(0)
                }
            } else password = ""
        }
    Text("Encrypted file backup", style = MaterialTheme.typography.titleLarge)
    Text(
        "Save your records and receipt photos using a password you keep. A lost password cannot be recovered. Restore replaces the current ledger after validation."
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
        AlertDialog(
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
                    if (mode == "Restore")
                        Tick("Replace current data with this backup", acknowledged) {
                            acknowledged = it
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
                    onClick = {
                        mode = null
                        password = ""
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Cancel")
                }
            },
        )
}
