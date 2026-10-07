package jm.yardmoney

import android.app.Activity
import android.content.Intent
import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import jm.yardmoney.ui.LockScreen
import jm.yardmoney.ui.YardApp

class MainActivity : FragmentActivity() {
    private val lockSession by viewModels<LockSession>()
    private var locked: Boolean
        get() = lockSession.locked
        set(value) {
            lockSession.locked = value
        }

    private var promptInFlight: Boolean
        get() = lockSession.promptInFlight
        set(value) {
            lockSession.promptInFlight = value
        }

    private lateinit var biometricPrompt: BiometricPrompt
    private val credential =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            promptInFlight = false
            if (it.resultCode == Activity.RESULT_OK) markUnlocked()
            // Cancelled: stay on the lock screen (Unlock retries) instead of closing the app, and
            // do not re-open the prompt the moment this screen resumes.
            else lockSession.autoPrompt = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        when (appearancePrefs().getString(Prefs.THEME, "System")) {
            "Light" -> setTheme(R.style.Theme_YardMoney_Light)
            "Dark" -> setTheme(R.style.Theme_YardMoney_Dark)
            "AMOLED" -> setTheme(R.style.Theme_YardMoney_Black)
        }
        super.onCreate(savedInstanceState)
        if (!lockSession.initialized) {
            locked = appearancePrefs().getBoolean(Prefs.LOCK, false)
            lockSession.initialized = true
        }
        if (savedInstanceState?.getBoolean("captureConsumed", false) != true)
            lockSession.stageCapture(intent)
        biometricPrompt = createBiometricPrompt()
        enableEdgeToEdge()
        // The isolated fictional demo allows design verification; real financial screens stay
        // protected.
        if (!BuildConfig.APPLICATION_ID.endsWith(".demo"))
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            // Locking removes the app from composition; the holder keeps half-filled forms, the
            // selected tab and scroll positions, so unlocking returns to where the person was.
            val screens = rememberSaveableStateHolder()
            if (locked) LockScreen(notice = lockSession.notice, onUnlock = ::unlockByRequest)
            else screens.SaveableStateProvider("app") {
                YardApp(pendingCapture = lockSession.pendingCapture, captureConsumed = lockSession::consumeCapture)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lockSession.stageCapture(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("captureConsumed", lockSession.pendingCapture == null)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (locked && !promptInFlight && lockSession.autoPrompt) unlock()
    }

    override fun onStop() {
        if (
            !isChangingConfigurations &&
                !promptInFlight &&
                appearancePrefs().getBoolean(Prefs.LOCK, false)
        )
            locked = true
        lockSession.autoPrompt = true
        super.onStop()
    }

    fun setAppLock(enabled: Boolean) {
        require(!enabled || getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            "Set a device screen lock in Android Settings first."
        }
        appearancePrefs().edit().putBoolean(Prefs.LOCK, enabled).apply()
        if (enabled) {
            locked = true
            unlock()
        }
    }

    private fun markUnlocked() {
        lockSession.notice = null
        locked = false
    }

    private fun unlockByRequest() {
        lockSession.autoPrompt = true
        unlock()
    }

    private fun useCredential() {
        val intent =
            getSystemService(KeyguardManager::class.java)
                .createConfirmDeviceCredentialIntent(
                    "Unlock YardMoney",
                    "Confirm your device screen lock",
                )
        if (intent == null) {
            // The device screen lock was removed after App lock was switched on. Stay locked and
            // say what to do rather than closing with no explanation.
            promptInFlight = false
            lockSession.notice = "Set a device screen lock in Android Settings, then tap Unlock."
        } else credential.launch(intent)
    }

    private fun unlock() {
        if (promptInFlight) return
        promptInFlight = true
        if (
            BiometricManager.from(this)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) !=
                BiometricManager.BIOMETRIC_SUCCESS
        ) {
            useCredential()
            return
        }
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock YardMoney")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Use screen lock")
                .build()
        )
    }

    private fun createBiometricPrompt() =
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    promptInFlight = false
                    markUnlocked()
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    when (code) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> useCredential()
                        else -> {
                            // Dismissed, timed out or hardware hiccup: stay locked so Unlock can
                            // retry. Only real failures are explained; a cancel is not an error.
                            promptInFlight = false
                            val cancelled =
                                code == BiometricPrompt.ERROR_USER_CANCELED ||
                                    code == BiometricPrompt.ERROR_CANCELED
                            if (code == BiometricPrompt.ERROR_USER_CANCELED)
                                lockSession.autoPrompt = false
                            lockSession.notice = if (cancelled) null else message.toString()
                        }
                    }
                }
            },
        )
}
