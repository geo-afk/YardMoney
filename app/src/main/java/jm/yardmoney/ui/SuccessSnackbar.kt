package jm.yardmoney.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
internal fun rememberSuccessSnackbar(success: MutableStateFlow<String?>): SnackbarHostState {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(success, snack) {
        // Consuming a message must not restart the effect and cancel its visible snackbar.
        success.collect { message ->
            if (message != null) {
                success.value = null
                snack.showSnackbar(message)
            }
        }
    }
    return snack
}

internal suspend fun SnackbarHostState.showShopUndo(message: String): SnackbarResult {
    // Undo always belongs to the latest action, even if earlier feedback is still visible.
    currentSnackbarData?.dismiss()
    return showSnackbar(message, "Undo", duration = SnackbarDuration.Long)
}
