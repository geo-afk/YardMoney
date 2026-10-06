package jm.yardmoney.ui

import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDate
import jm.yardmoney.data.CommitmentBalance

// Presentation models shared by several screens. Preference models live in UiPreferences.kt and
// shop models in ShopModels.kt.

internal data class MoneyIdentity(val icon: ImageVector, val seed: Long)

internal data class IdentityOption(
    val id: String,
    val label: String,
    val detail: String = "",
    val identity: MoneyIdentity = categoryIdentity(label),
)

internal data class MoneyDestination(
    val label: String,
    val filled: ImageVector,
    val outlined: ImageVector,
)

internal data class ReservationGroup(val key: String, val occurrences: List<CommitmentBalance>) {
    val primary: CommitmentBalance
        get() = occurrences.firstOrNull { it.remainingMinor > 0 } ?: occurrences.last()

    val recurring: Boolean
        get() = key.startsWith("series:")
}

internal data class ActivityBin(
    val start: LocalDate,
    val end: LocalDate,
    val amount: Long,
    val count: Int,
)

internal data class BudgetUsage(val income: Long, val allocation: List<Long>, val used: List<Long>)

internal enum class EditStage {
    Full,
    Half,
    Peek,
}

/** Everything the reports tab charts, computed in one pass over the current period. */
internal data class FinancialSummary(
    val income: Long,
    val spending: Long,
    val categories: List<Pair<String, Long>>,
    val merchants: List<Pair<String, Long>>,
    val months: List<Pair<String, Long>>,
)
