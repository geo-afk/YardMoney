package jm.yardmoney.core

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CategoryRuleMatcherTest {
    private fun rule(id: String, pattern: String, type: String = "CONTAINS", account: String? = null) =
        CategorySuggestion(id, pattern, type, "Groceries", "NEEDS", account)
    @Test fun mostSpecificCaseInsensitiveMatchWins() {
        val rules = listOf(rule("short", "Hi"), rule("long", "Hi-Lo"), rule("exact", "Hi-Lo", "EXACT"))
        assertEquals("exact", CategoryRuleMatcher.match(" HI-LO ", "cash", rules)?.id)
        assertEquals("long", CategoryRuleMatcher.match("Hi-Lo Kingston", "cash", rules)?.id)
        assertNull(CategoryRuleMatcher.match("Other", "cash", rules))
    }
    @Test fun emptyUnsupportedAndAccountSpecificRulesAreBounded() {
        val rules = listOf(rule("blank", ""), rule("bad", "Hi-Lo", "REGEX"), rule("cash", "Hi-Lo", account="cash"))
        assertNull(CategoryRuleMatcher.match("Hi-Lo", "card", rules))
        assertNull(CategoryRuleMatcher.match("Hi-Lo", null, rules))
        assertEquals("cash", CategoryRuleMatcher.match("Hi-Lo", "cash", rules)?.id)
        assertNull(CategoryRuleMatcher.match("", "cash", rules))
    }
    @Test fun repeatsRespectWindowFrequencyRecencyAndLimit() {
        val today = LocalDate.of(2026,10,6)
        fun row(name: String, days: Long) = RepeatExpense(name,60000,"Transport","NEEDS","cash",today.minusDays(days))
        val records = listOf(row("taxi",2),row("taxi",3),row("lunch",0),row("old",60),row("future",-1))
        assertEquals(listOf("taxi","lunch"), RepeatExpenses.suggestions(records,today).map { it.description })
        assertEquals(6, RepeatExpenses.suggestions((0L..9).map { row("item $it",it) },today).size)
    }
    @Test fun validationCoversBoundariesAndUnknownTypes() {
        val valid = rule("rule", "Store", "EXACT")
        val invalid = listOf(valid.copy(id = ""), valid.copy(pattern = " "),
            valid.copy(pattern = "x".repeat(241)), valid.copy(category = ""),
            valid.copy(category = "x".repeat(121)), valid.copy(matchType = "REGEX"),
            valid.copy(bucket = "UNKNOWN"), valid.copy(createdAt = -1))
        invalid.forEach { assertTrue(it.toString(), runCatching { CategoryRuleMatcher.validate(it) }.isFailure) }
        listOf(valid, valid.copy(pattern = "x".repeat(240), category = "x".repeat(120)))
            .forEach { assertTrue(runCatching { CategoryRuleMatcher.validate(it) }.isSuccess) }
    }
    @Test fun accountSpecificRuleBreaksAnOtherwiseEqualTie() {
        val shared = rule("shared", "Store", "EXACT")
        val cash = rule("cash", "Store", "EXACT", "cash")
        assertEquals("cash", CategoryRuleMatcher.match("Store", "cash", listOf(shared,cash))?.id)
        assertEquals("shared", CategoryRuleMatcher.match("Store", "card", listOf(shared,cash))?.id)
    }

}
