package jm.yardmoney

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import jm.yardmoney.ui.YardApp
import jm.yardmoney.ui.YardTheme

class LockSession : ViewModel() {
    var initialized = false
    var locked by mutableStateOf(false)
    var promptInFlight = false
}

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
            if (it.resultCode == Activity.RESULT_OK) locked = false else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        when (getSharedPreferences("appearance", 0).getString("theme", "System")) {
            "Light" -> setTheme(R.style.Theme_YardMoney_Light)
            "Dark" -> setTheme(R.style.Theme_YardMoney_Dark)
            "AMOLED" -> setTheme(R.style.Theme_YardMoney_Black)
        }
        super.onCreate(savedInstanceState)
        if (!lockSession.initialized) {
            locked = getSharedPreferences("appearance", 0).getBoolean("lock", false)
            lockSession.initialized = true
        }
        biometricPrompt = createBiometricPrompt()
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            if (locked)
                YardTheme {
                    Surface(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.safeDrawingPadding().padding(32.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Text(
                                "YardMoney is locked",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text("Confirm your device screen lock to continue.")
                        }
                    }
                }
            else YardApp()
        }
    }

    override fun onResume() {
        super.onResume()
        if (locked && !promptInFlight) unlock()
    }

    override fun onStop() {
        if (
            !isChangingConfigurations &&
                !promptInFlight &&
                getSharedPreferences("appearance", 0).getBoolean("lock", false)
        )
            locked = true
        super.onStop()
    }

    fun setAppLock(enabled: Boolean) {
        require(!enabled || getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            "Set a device screen lock in Android Settings first."
        }
        getSharedPreferences("appearance", 0).edit().putBoolean("lock", enabled).apply()
        if (enabled) {
            locked = true
            unlock()
        }
    }

    private fun useCredential() {
        getSystemService(KeyguardManager::class.java)
            .createConfirmDeviceCredentialIntent(
                "Unlock YardMoney",
                "Confirm your device screen lock",
            )
            ?.let(credential::launch)
            ?: run {
                promptInFlight = false
                finish()
            }
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
                    locked = false
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    if (
                        code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            code == BiometricPrompt.ERROR_LOCKOUT ||
                            code == BiometricPrompt.ERROR_LOCKOUT_PERMANENT
                    )
                        useCredential()
                    else {
                        promptInFlight = false
                        finish()
                    }
                }
            },
        )
}
