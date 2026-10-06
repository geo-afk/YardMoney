package jm.yardmoney

import java.io.IOException
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class UserMessagesTest {
    // Stand-ins named like the framework exceptions; classification is by simple class name.
    private class SQLiteFullException : RuntimeException("database or disk is full")

    private class SQLiteConstraintException :
        RuntimeException("UNIQUE constraint failed: accounts.name")

    private class SQLiteException : RuntimeException("file is not a database")

    private val memory =
        "This photo is too large for this device to process. " +
            "Retake it closer or choose a smaller image."
    private val storage = "Your device is out of storage space. Free some space and try again."

    private fun failure(block: () -> Unit): Throwable = runCatching(block).exceptionOrNull()!!

    @Test
    fun ownValidationMessagesPassThrough() {
        assertEquals(
            "Choose a valid account.",
            userMessage(IllegalArgumentException("Choose a valid account.")),
        )
        assertEquals(
            "Set a screen lock first.",
            userMessage(IllegalStateException("Set a screen lock first.")),
        )
        assertEquals(
            "Use a photo under 15 MB.",
            userMessage(failure { require(false) { "Use a photo under 15 MB." } }),
        )
    }

    @Test
    fun kotlinDefaultTextsAreNeverShown() {
        val entries = "Please check your entries and try again."
        val generic = "Something went wrong. Please try again."
        assertEquals(entries, userMessage(failure { require(false) }))
        assertEquals(generic, userMessage(failure { check(false) }))
        assertEquals(entries, userMessage(failure { requireNotNull<String>(null) }))
    }

    @Test
    fun parsingAndArithmeticFailuresAreExplained() {
        assertEquals("Enter a valid number.", userMessage(failure { "abc".toLong() }))
        assertEquals(
            "Enter a valid date as YYYY-MM-DD.",
            userMessage(failure { LocalDate.parse("2026-13-45") }),
        )
        assertEquals(
            "That amount is too large.",
            userMessage(failure { Math.addExact(Long.MAX_VALUE, 1L) }),
        )
    }

    @Test
    fun memoryAndStorageProblemsGetPlainAdvice() {
        assertEquals(memory, userMessage(OutOfMemoryError("Failed to allocate")))
        assertEquals(memory, userMessage(RuntimeException("decode failed", OutOfMemoryError())))
        assertEquals(storage, userMessage(IOException("write failed: ENOSPC (No space left)")))
        assertEquals(storage, userMessage(SQLiteFullException()))
    }

    @Test
    fun databaseAndFileFailuresHideInternals() {
        assertEquals(
            "That conflicts with something you already saved. Check for duplicates and try again.",
            userMessage(SQLiteConstraintException()),
        )
        assertEquals(
            "The local database reported a problem. " +
                "Try again, and restart YardMoney if it keeps happening.",
            userMessage(SQLiteException()),
        )
        assertEquals(
            "That file could not be read or written. Check the location and try again.",
            userMessage(IOException("EACCES")),
        )
        assertEquals(
            "YardMoney does not have permission to do that.",
            userMessage(SecurityException("uid 10123")),
        )
    }

    @Test
    fun unknownFailuresNeverLeakRawText() {
        val generic = "Something went wrong. Please try again."
        assertEquals(generic, userMessage(RuntimeException("NPE at line 42")))
        assertEquals(generic, userMessage(Exception()))
    }
}
