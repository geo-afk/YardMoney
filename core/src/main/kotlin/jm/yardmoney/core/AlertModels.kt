package jm.yardmoney.core

import java.time.LocalDate

data class AlertPreview(val amountMinor: Long?, val merchant: String?, val kind: String?,
    val date: LocalDate?, val issues: List<String>)
