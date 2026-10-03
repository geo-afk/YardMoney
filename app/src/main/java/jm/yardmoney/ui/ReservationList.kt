package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jm.yardmoney.core.Money
import jm.yardmoney.data.Commitment
import jm.yardmoney.data.CommitmentBalance

internal data class ReservationGroup(val key: String, val occurrences: List<CommitmentBalance>) {
    val primary: CommitmentBalance
        get() = occurrences.firstOrNull { it.remainingMinor > 0 } ?: occurrences.last()

    val recurring: Boolean
        get() = key.startsWith("series:")
}

internal fun reservationGroups(rows: List<CommitmentBalance>): List<ReservationGroup> =
    rows
        .groupBy {
            if (it.commitment.occurrenceKey.contains('@'))
                "series:" + it.commitment.occurrenceKey.substringBefore('@')
            else "once:" + it.commitment.id
        }
        .map { (key, occurrences) ->
            ReservationGroup(
                key,
                occurrences.sortedWith(
                    compareBy<CommitmentBalance> { it.commitment.dueDate ?: "" }
                        .thenBy { it.commitment.id }
                ),
            )
        }
        .sortedWith(
            compareBy<ReservationGroup> { it.primary.commitment.dueDate ?: "" }.thenBy { it.key }
        )

@Composable
internal fun ReservationList(
    rows: List<CommitmentBalance>,
    pay: (Commitment) -> Unit,
    edit: (Commitment) -> Unit,
) {
    val groups = reservationGroups(rows)
    if (groups.isEmpty()) Text("No bills or reservations yet.")
    groups.forEach { group ->
        key(group.key) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            val next = group.primary
            val other = group.occurrences.filter { it.commitment.id != next.commitment.id }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReservationRow(next, group.recurring, pay, edit)
                if (group.recurring && other.isNotEmpty()) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            if (expanded) "Hide scheduled payments"
                            else
                                "Show ${other.size} other scheduled ${if (other.size == 1) "payment" else "payments"}"
                        )
                    }
                    if (expanded) {
                        Text(
                            "Other dates in this recurring bill",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        other.forEach { ReservationRow(it, false, pay, edit) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationRow(
    row: CommitmentBalance,
    recurring: Boolean,
    pay: (Commitment) -> Unit,
    edit: (Commitment) -> Unit,
) {
    val c = row.commitment
    Record(
        c.name,
        "${if (recurring) "Recurring · " else ""}${c.dueDate ?: "No due date"} · ${if (row.remainingMinor == 0L) "Paid" else "Reserved"}",
        Money.format(row.remainingMinor),
        if (row.remainingMinor > 0) ({ pay(c) }) else null,
    )
    TextButton(onClick = { edit(c) }, shape = MaterialTheme.shapes.small) {
        Text(if (recurring) "Edit this payment" else "Edit reservation")
    }
}
