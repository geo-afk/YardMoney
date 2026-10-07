package jm.yardmoney.core

import java.time.LocalDate

data class CaptureAccount(val id: String, val name: String) {
    internal val tokenPattern = Regex("(?<!\\S)${Regex.escape(name)}(?!\\S)", RegexOption.IGNORE_CASE)
}
data class QuickAddPreview(
    val amountMinor: Long?, val description: String?, val accountId: String?,
    val date: LocalDate?, val issues: List<String> = emptyList(),
)
data class CategorySuggestion(
    val id: String, val pattern: String, val matchType: String,
    val category: String, val bucket: String, val accountId: String? = null,
    val createdAt: Long = 0,
)
data class RepeatExpense(
    val description: String, val amountMinor: Long, val category: String,
    val bucket: String, val accountId: String, val date: LocalDate,
)
