package jm.yardmoney.ui

internal data class QuickAddDraft(
    val amount: String = "", val description: String = "", val category: String = "",
    val bucket: String = "NEEDS", val accountId: String = "", val date: String = "",
) {
    fun savedValues() = listOf(amount, description, category, bucket, accountId, date)
    companion object {
        fun fromSaved(values: List<String>) = QuickAddDraft(values[0], values[1], values[2], values[3], values[4], values[5])
    }
}
