package jm.yardmoney

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import android.content.Intent

/** Lock state that must survive configuration changes (rotation, theme switches). */
class LockSession : ViewModel() {
    // Retain the uninspected intent during authentication and configuration changes.
    // ContentResolver and parsing are only used by the unlocked app composition.
    var pendingCapture by mutableStateOf<Intent?>(null)
        private set
    fun stageCapture(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) pendingCapture = Intent(intent)
    }
    fun consumeCapture(intent: Intent) {
        if (!locked && pendingCapture === intent) pendingCapture = null
    }
    var initialized = false
    var locked by mutableStateOf(false)
    var promptInFlight = false

    /** Set when the person cancels a prompt, so returning to the screen does not re-open it. */
    var autoPrompt = true

    /** Why unlocking failed (shown on the lock screen) instead of silently closing the app. */
    var notice by mutableStateOf<String?>(null)
}
