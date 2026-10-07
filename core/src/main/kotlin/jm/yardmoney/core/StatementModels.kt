package jm.yardmoney.core

import java.time.LocalDate

data class StatementDocument(val records: List<List<String>>, val delimiter: Char, val likelyHeader: Boolean)
data class StatementMapping(val dateColumn: Int = -1, val descriptionColumn: Int = -1,
    val amountColumn: Int = -1, val debitColumn: Int = -1, val creditColumn: Int = -1,
    val balanceColumn: Int = -1, val dateFormat: String = "", val header: Boolean = true)
data class StatementRow(val index: Int, val date: LocalDate?, val signedMinor: Long?,
    val description: String, val hash: String, val duplicate: Boolean, val issues: List<String>)
data class StatementExisting(val accountId: String, val date: LocalDate, val signedMinor: Long, val description: String)
