package jm.yardmoney.data

import androidx.room.*
import jm.yardmoney.core.StatementMapping

@Entity(tableName = "import_batches", foreignKeys = [ForeignKey(entity = Account::class,
    parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)], indices = [Index("accountId")])
data class ImportBatch(@PrimaryKey val id: String, val accountId: String, val createdAt: Long, val rowCount: Int)

@Entity(tableName = "import_records", foreignKeys = [
    ForeignKey(entity = ImportBatch::class, parentColumns = ["id"], childColumns = ["batchId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = MoneyTransaction::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["batchId", "rowHash"], unique = true)])
data class ImportRecord(@PrimaryKey val transactionId: String, val batchId: String, val rowHash: String)

@Entity(tableName = "import_mappings", foreignKeys = [ForeignKey(entity = Account::class,
    parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)])
data class ImportMapping(@PrimaryKey val accountId: String, val dateColumn: Int, val descriptionColumn: Int,
    val amountColumn: Int, val debitColumn: Int, val creditColumn: Int, val balanceColumn: Int,
    val dateFormat: String, val header: Boolean)

fun ImportMapping.mapping() = StatementMapping(dateColumn, descriptionColumn, amountColumn, debitColumn,
    creditColumn, balanceColumn, dateFormat, header)
fun StatementMapping.persisted(accountId: String) = ImportMapping(accountId, dateColumn, descriptionColumn,
    amountColumn, debitColumn, creditColumn, balanceColumn, dateFormat, header)
data class ImportState(val batches: List<ImportBatch>, val mappings: List<ImportMapping>)
