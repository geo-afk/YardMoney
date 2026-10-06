package jm.yardmoney.data

/**
 * Stops spreadsheet apps from running imported text as a formula. Descriptions can come from
 * scanned receipts, so they are untrusted: a cell starting with = + - @ (or a tab/CR) gets a
 * leading apostrophe, the standard neutraliser.
 */
internal fun spreadsheetSafe(value: String): String =
    if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
