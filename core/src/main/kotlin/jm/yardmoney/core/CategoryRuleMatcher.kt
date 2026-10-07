package jm.yardmoney.core

import java.util.Locale

object CategoryRuleMatcher {
    fun validate(rule: CategorySuggestion) {
        require(rule.id.isNotBlank() && rule.id.length <= 120) { "Choose a valid category rule." }
        require(rule.pattern.trim().length in 1..240) { "Enter a merchant pattern of up to 240 characters." }
        require(rule.category.trim().length in 1..120) { "Enter a category of up to 120 characters." }
        require(rule.matchType in setOf("EXACT", "CONTAINS")) { "Choose Exact or Contains for matching." }
        require(rule.bucket in setOf("NEEDS", "WANTS", "SAVINGS")) { "Choose a valid budget group." }
        require(rule.createdAt >= 0) { "Choose a valid rule creation date." }
    }

    fun match(merchant: String, accountId: String?, rules: List<CategorySuggestion>): CategorySuggestion? {
        val text = merchant.trim().lowercase(Locale.ROOT)
        if (text.isEmpty()) return null
        return rules.filter { rule ->
            val pattern = rule.pattern.trim().lowercase(Locale.ROOT)
            pattern.isNotEmpty() && (rule.accountId == null || rule.accountId == accountId) &&
                when (rule.matchType) {
                    "EXACT" -> text == pattern
                    "CONTAINS" -> text.contains(pattern)
                    else -> false
                }
        }.sortedWith(compareByDescending<CategorySuggestion> { it.pattern.trim().length }
            .thenByDescending { it.matchType == "EXACT" }.thenByDescending { it.accountId != null }
            .thenByDescending { it.createdAt }
            .thenBy { it.id }).firstOrNull()
    }
}

/** Most frequent combinations first, with recency breaking ties; never includes future records. */
object RepeatExpenses {
    fun suggestions(records: List<RepeatExpense>, today: java.time.LocalDate): List<RepeatExpense> =
        records.filter { it.date in today.minusDays(59)..today && it.amountMinor > 0 }
            .groupBy { listOf(it.description, it.amountMinor, it.category, it.bucket, it.accountId) }
            .values.sortedWith(compareByDescending<List<RepeatExpense>> { it.size }
                .thenByDescending { rows -> rows.maxOf { it.date } }
                .thenBy { it.first().description })
            .take(6).map { rows -> rows.maxBy { it.date } }
}
