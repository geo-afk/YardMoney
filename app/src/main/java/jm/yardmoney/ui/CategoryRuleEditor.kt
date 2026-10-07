package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jm.yardmoney.AppModel
import jm.yardmoney.core.CategoryRuleMatcher
import jm.yardmoney.data.*

@Composable
internal fun CategoryRuleEditor(model: AppModel, data: FinanceSnapshot, original: CategoryRule?, busy: Boolean, close: () -> Unit) {
    val id = rememberSaveable { original?.id ?: FinanceRepository.id() }
    val createdAt = rememberSaveable { original?.createdAt ?: System.currentTimeMillis() }
    var pattern by rememberSaveable { mutableStateOf(original?.pattern.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(original?.category.orEmpty()) }
    var bucket by rememberSaveable { mutableStateOf(original?.bucket ?: "NEEDS") }
    var type by rememberSaveable { mutableStateOf(original?.matchType ?: "EXACT") }
    var account by rememberSaveable { mutableStateOf(original?.accountId.orEmpty()) }
    val edited = CategoryRule(id, pattern, type, category, bucket, account.takeIf { it.isNotBlank() }, createdAt)
    val canSave = runCatching { CategoryRuleMatcher.validate(edited.suggestion()) }.isSuccess &&
        (account.isBlank() || data.ledger.accounts.any { it.account.id == account })
    MoneyEntrySheet(if (original == null) "Add category rule" else "Edit category rule", "Save rule",
        busy, canSave, { model.act(close) { model.repo.saveCategoryRule(edited) } }, close,
        dirty = edited != original) {
        MoneyEntrySection("Merchant and category") {
            Field("Merchant pattern", pattern) { pattern = it }
            Field("Category", category) { category = it }
            DropdownField("Matching", type, mapOf("EXACT" to "Exact match", "CONTAINS" to "Contains")) { type = it }
            Choice("Budget group", bucket, listOf("NEEDS", "WANTS", "SAVINGS")) { bucket = it }
            DropdownField("Apply to account", account, mapOf("" to "All accounts") +
                data.ledger.accounts.associate { it.account.id to it.account.name }) { account = it }
            Text("Matching ignores letter case. A longer matching pattern takes priority.")
        }
        if (original != null) TextButton(onClick = { model.act(close, "Rule removed") { model.repo.deleteCategoryRule(id) } },
            enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete rule") }
    }
}
