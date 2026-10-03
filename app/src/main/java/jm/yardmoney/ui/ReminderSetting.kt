package jm.yardmoney.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import jm.yardmoney.reminders.BillReminder

@Composable
internal fun ReminderSetting() {
    val context = LocalContext.current
    var enabled by remember {
        mutableStateOf(context.getSharedPreferences("appearance", 0).getBoolean("reminders", false))
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            enabled = allowed
            BillReminder.setEnabled(context, allowed)
        }
    Text("Bill reminders", style = MaterialTheme.typography.titleLarge)
    Tick("Remind me to review upcoming bills", enabled) { value ->
        if (value && android.os.Build.VERSION.SDK_INT >= 33)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else {
            enabled = value
            BillReminder.setEnabled(context, value)
        }
    }
    Text(
        "Daily checks are approximate and depend on Android background scheduling. Notifications show no balances or bill names.",
        style = MaterialTheme.typography.bodySmall,
    )
}
