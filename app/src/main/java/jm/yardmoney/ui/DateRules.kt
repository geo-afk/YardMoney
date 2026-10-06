package jm.yardmoney.ui

import java.time.LocalDate

/** Which dates a date field accepts; mirrors the checks the repository makes when saving. */
internal enum class DateWindow {
    /** No restriction (bill due dates may be in the past or the future). */
    Any,

    /** Purchases and received money cannot be dated in the future. */
    UpToToday,

    /** Paydays are today or later, within the five years the plan can look ahead. */
    FromToday;

    fun allows(date: LocalDate, today: LocalDate): Boolean =
        when (this) {
            Any -> true
            UpToToday -> date <= today
            FromToday -> date >= today && date <= today.plusYears(5)
        }

    fun allowsYear(year: Int, today: LocalDate): Boolean =
        when (this) {
            Any -> true
            UpToToday -> year <= today.year
            FromToday -> year in today.year..today.plusYears(5).year
        }
}

/** Picks the window from a field's label, the same convention Field uses to choose its widget. */
internal fun dateWindowFor(label: String): DateWindow =
    when {
        label.startsWith("Next payday", ignoreCase = true) -> DateWindow.FromToday
        label.contains("due", ignoreCase = true) -> DateWindow.Any
        label.contains("date", ignoreCase = true) -> DateWindow.UpToToday
        else -> DateWindow.Any
    }
