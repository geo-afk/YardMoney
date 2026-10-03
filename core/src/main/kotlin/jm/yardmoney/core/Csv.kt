package jm.yardmoney.core

object Csv {
    /** User text is quoted and spreadsheet formula prefixes are neutralised. */
    fun text(value: String): String {
        val first = value.trimStart().firstOrNull()
        val safe =
            if (
                first in listOf('=', '+', '-', '@') ||
                    value.startsWith('\t') ||
                    value.startsWith('\r')
            )
                "'$value"
            else value
        return "\"${safe.replace("\"","\"\"")}\""
    }

    fun row(fields: List<String>) = fields.joinToString(",", transform = ::text) + "\r\n"
}
