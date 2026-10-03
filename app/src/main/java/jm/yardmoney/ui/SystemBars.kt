package jm.yardmoney.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect

@Composable
internal fun ConfigureSystemBars(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity
    SideEffect {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(0, 0) { dark },
            navigationBarStyle =
                SystemBarStyle.auto(0xFFE6FFFFFF.toInt(), 0x801B1B1B.toInt()) { dark },
        )
    }
}
