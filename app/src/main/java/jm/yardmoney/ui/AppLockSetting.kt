package jm.yardmoney.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import jm.yardmoney.MainActivity
import jm.yardmoney.Prefs
import jm.yardmoney.appearancePrefs

@Composable
internal fun AppLockSetting() {
    val activity = LocalActivity.current as? MainActivity ?: return
    var enabled by remember {
        mutableStateOf(activity.appearancePrefs().getBoolean(Prefs.LOCK, false))
    }
    var error by remember { mutableStateOf<String?>(null) }
    Text("App lock", style = MaterialTheme.typography.titleLarge)
    Tick("Require device unlock when returning to YardMoney", enabled) { value ->
        runCatching {
            activity.setAppLock(value)
            enabled = value
        }
            .onFailure { error = it.message }
    }
    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
}
