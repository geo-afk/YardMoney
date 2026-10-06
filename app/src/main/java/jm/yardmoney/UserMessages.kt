package jm.yardmoney

import java.io.IOException
import java.time.format.DateTimeParseException

// Kotlin's own default texts for require/check/requireNotNull; useless in a dialog.
private val kotlinDefaultMessages =
    setOf("Failed requirement.", "Check failed.", "Required value was null.")

/**
 * Turns any failure into one sentence a person can act on.
 *
 * The app's own validation messages (require/check/error with a message) are written for people and
 * pass through unchanged. Framework and library failures get a plain-language replacement instead
 * of raw exception text such as "For input string" or "UNIQUE constraint failed".
 */
internal fun userMessage(failure: Throwable): String {
    val chain = generateSequence(failure) { it.cause }.take(6).toList()
    val text = failure.message?.trim().orEmpty()
    val deliberate =
        (failure is IllegalArgumentException || failure is IllegalStateException) &&
            failure !is NumberFormatException &&
            text.isNotEmpty() &&
            text !in kotlinDefaultMessages
    return when {
        chain.any { it is OutOfMemoryError } ->
            "This photo is too large for this device to process. Retake it closer or choose a smaller image."
        deliberate -> text
        failure is DateTimeParseException -> "Enter a valid date as YYYY-MM-DD."
        failure is NumberFormatException -> "Enter a valid number."
        failure is ArithmeticException -> "That amount is too large."
        failure is IllegalArgumentException -> "Please check your entries and try again."
        chain.any(::isStorageFull) ->
            "Your device is out of storage space. Free some space and try again."
        chain.any { it.javaClass.simpleName == "SQLiteConstraintException" } ->
            "That conflicts with something you already saved. Check for duplicates and try again."
        chain.any { it.javaClass.simpleName.startsWith("SQLite") } ->
            "The local database reported a problem. Try again, and restart YardMoney if it keeps happening."
        failure is IOException ->
            "That file could not be read or written. Check the location and try again."
        failure is SecurityException -> "YardMoney does not have permission to do that."
        else -> "Something went wrong. Please try again."
    }
}

private fun isStorageFull(e: Throwable): Boolean {
    val text = e.message.orEmpty()
    return e.javaClass.simpleName == "SQLiteFullException" ||
        text.contains("No space left", ignoreCase = true) ||
        text.contains("disk is full", ignoreCase = true)
}
