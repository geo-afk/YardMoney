package jm.yardmoney.ui

import java.time.LocalDate
import jm.yardmoney.core.*
import jm.yardmoney.data.*

/** First payday after today, following the profile's schedule. */
internal fun nextPayday(p: Profile, today: LocalDate): LocalDate {
    var d = LocalDate.parse(p.nextPayday)
    if (p.frequency == "IRREGULAR") return today.plusDays(14)
    do {
        d =
            PaySchedule.nextAfter(d, PayFrequency.valueOf(p.frequency), p.anchorDay, p.secondDay)
                ?: today.plusDays(14)
    } while (d <= today)
    return d
}

internal fun safe(data: FinanceSnapshot, today: LocalDate): SafeToSpend =
    BudgetEngine.safeToSpend(
        Money.sum(data.ledger.accounts.filter { it.account.included }.map { it.balanceMinor }),
        data.ledger.commitments.map {
            Reserve(
                it.commitment.id,
                it.remainingMinor,
                it.commitment.dueDate?.let(LocalDate::parse),
            )
        },
        today,
        LocalDate.parse(data.ledger.profile!!.nextPayday),
    )
