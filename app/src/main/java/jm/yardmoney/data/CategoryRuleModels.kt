package jm.yardmoney.data

import androidx.room.*
import jm.yardmoney.core.CategorySuggestion

@Entity(tableName = "category_rules",
    foreignKeys = [ForeignKey(entity = Account::class, parentColumns = ["id"],
        childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("accountId")])
data class CategoryRule(
    @PrimaryKey val id: String, val pattern: String, val matchType: String,
    val category: String, val bucket: String, val accountId: String?, val createdAt: Long,
)

fun CategoryRule.suggestion() = CategorySuggestion(id, pattern, matchType, category, bucket, accountId, createdAt)
