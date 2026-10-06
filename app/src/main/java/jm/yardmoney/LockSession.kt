package jm.yardmoney

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** Lock state that must survive configuration changes (rotation, theme switches). */
class LockSession : ViewModel() {
    var initialized = false
    var locked by mutableStateOf(false)
    var promptInFlight = false

    /** Set when the person cancels a prompt, so returning to the screen does not re-open it. */
    var autoPrompt = true

    /** Why unlocking failed (shown on the lock screen) instead of silently closing the app. */
    var notice by mutableStateOf<String?>(null)
}
