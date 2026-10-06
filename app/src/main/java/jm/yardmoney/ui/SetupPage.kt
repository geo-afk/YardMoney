package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import java.time.LocalDate
import jm.yardmoney.AppModel
import jm.yardmoney.core.*
import jm.yardmoney.data.*

@Composable
internal fun SetupPage(model: AppModel, busy: Boolean) {
    var name by rememberSaveable { mutableStateOf("") }
    var income by rememberSaveable { mutableStateOf("") }
    var opening by rememberSaveable { mutableStateOf("0") }
    var payday by rememberSaveable { mutableStateOf(model.repo.today.plusDays(14).toString()) }
    var frequency by rememberSaveable { mutableStateOf("FORTNIGHTLY") }
    var first by rememberSaveable { mutableStateOf("15") }
    var second by rememberSaveable { mutableStateOf("30") }
    Page(applySystemInsets = true) {
        Text(
            "YardMoney",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        IdentityBadge(MoneyIdentity(Icons.Default.Savings, 0xFF00865A))
        Text("A little clarity before payday.", style = MaterialTheme.typography.headlineLarge)
        var appearanceExpanded by rememberSaveable { mutableStateOf(false) }
        TextButton(
            onClick = { appearanceExpanded = !appearanceExpanded },
            shape = MaterialTheme.shapes.small,
        ) {
            Text(if (appearanceExpanded) "Hide appearance settings" else "Personalise appearance")
        }
        if (appearanceExpanded) AppearancePanel()
        Text(
            "Your budget works offline. All amounts are Jamaican dollars. Start with cash; add your other accounts later."
        )
        Field("What should we call you?", name) { name = it }
        Field("Typical take-home pay (J$; optional)", income) { income = it }
        Text(
            "This guides your plan; it does not add money to your account.",
            style = MaterialTheme.typography.bodySmall,
        )
        Choice("How often are you paid?", frequency, PayFrequency.entries.map { it.name }) {
            frequency = it
        }
        Field("Next payday (YYYY-MM-DD)", payday) { payday = it }
        if (frequency == "MONTHLY" || frequency == "TWICE_MONTHLY")
            Field("Monthly pay day (1–31)", first) { first = it }
        if (frequency == "TWICE_MONTHLY")
            Field("Second monthly pay day (1–31)", second) { second = it }
        Field("Cash you have right now (J$)", opening) { opening = it }
        Text("Begin with 50% needs, 30% wants and 20% savings. You can adjust this in Plan.")
        Button(
            enabled = !busy,
            onClick = {
                model.act {
                    val date = LocalDate.parse(payday)
                    require(date >= model.repo.today) { "Choose today or a future payday." }
                    model.repo.onboard(
                        Profile(
                            name = name.trim(),
                            typicalNetMinor = Money.parse(income.ifBlank { "0" }),
                            frequency = frequency,
                            nextPayday = date.toString(),
                            anchorDay = first.toInt(),
                            secondDay = second.toInt(),
                            needsBp = 5000,
                            wantsBp = 3000,
                            savingsBp = 2000,
                            periodStart = model.repo.today.toString(),
                            budgetIncomeMinor = 0,
                        ),
                        Money.parse(opening),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(if (busy) "Saving…" else "Make my plan")
        }
        BackupPanel(model, restoreOnly = true)
    }
}
