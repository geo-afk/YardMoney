package jm.yardmoney

import android.content.Context
import android.content.SharedPreferences

/** SharedPreferences file and key names, kept in one place so a typo cannot split a setting. */
internal object Prefs {
    // Files
    const val APPEARANCE = "appearance"
    const val NAVIGATION = "navigation"
    const val SAVED_ITEMS = "saved_items"

    // Keys in APPEARANCE
    const val THEME = "theme"
    const val DYNAMIC = "dynamic"
    const val ACCENT = "accent"
    const val MOTION = "motion"
    const val CORNERS = "corners"
    const val SPACING = "spacing"
    const val LOCK = "lock"
    const val REMINDERS = "reminders"
    const val REMINDER_DAY = "reminder_day"
    const val RECEIPT_LAYOUT = "receiptLayout"
    const val RECEIPT_BACKGROUND = "receiptBackground"
    const val RECEIPT_SYNC = "receiptSync"

    // Keys in NAVIGATION
    const val ACCOUNT_SCOPE = "account_scope"
}

internal fun Context.appearancePrefs(): SharedPreferences =
    getSharedPreferences(Prefs.APPEARANCE, Context.MODE_PRIVATE)

internal fun Context.navigationPrefs(): SharedPreferences =
    getSharedPreferences(Prefs.NAVIGATION, Context.MODE_PRIVATE)

internal fun Context.savedItemsPrefs(): SharedPreferences =
    getSharedPreferences(Prefs.SAVED_ITEMS, Context.MODE_PRIVATE)
