package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jm.yardmoney.core.CategorySuggestion

@Composable
internal fun CategoryRulesPage(rules: List<CategorySuggestion>, edit: (String) -> Unit, add: () -> Unit) {
    SectionHeading("Category rules", subtitle = "Your merchant choices, applied locally")
    Text("A longer matching pattern takes priority. Exact matches break ties. Account-specific rules only apply to that account.")
    Button(onClick = add, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Default.Add, null)
        Spacer(Modifier.width(8.dp))
        Text("Add category rule")
    }
    if (rules.isEmpty()) EmptyState("No category rules", "Choose a merchant and category to reduce repeat typing.", icon = Icons.AutoMirrored.Filled.Rule)
    rules.forEach { rule ->
        MoneyCard(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { edit(rule.id) }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IdentityBadge(categoryIdentity(rule.category))
                Column(Modifier.weight(1f)) {
                    Text(rule.pattern, style = MaterialTheme.typography.titleMedium)
                    Text("${rule.category} · ${rule.bucket.lowercase()} · ${rule.matchType.lowercase()}")
                }
                Icon(Icons.Default.ChevronRight, "Edit category rule")
            }
        }
    }
}
