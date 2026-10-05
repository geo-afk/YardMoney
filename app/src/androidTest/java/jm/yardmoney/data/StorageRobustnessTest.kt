package jm.yardmoney.data

import androidx.test.platform.app.InstrumentationRegistry
import jm.yardmoney.YardMoneyApplication
import jm.yardmoney.security.PortableBackup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StorageRobustnessTest {
    private val app
        get() =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as YardMoneyApplication

    @Test
    fun abandonedScanCleanupKeepsActivePhotosAndOtherExports() {
        val directory = java.io.File(app.cacheDir, "exports").apply { mkdirs() }
        val id = System.nanoTime()
        val old =
            java.io.File(directory, "receipt-old-$id.jpg").apply {
                writeBytes(byteArrayOf(1))
                setLastModified(1L)
            }
        val active =
            java.io.File(directory, "receipt-active-$id.jpg").apply { writeBytes(byteArrayOf(1)) }
        val other =
            java.io.File(directory, "other-$id.csv").apply {
                writeText("fictional")
                setLastModified(1L)
            }
        try {
            app.storage.removeExpiredTempReceipts()
            assertFalse(old.exists())
            assertTrue(active.exists())
            assertTrue(other.exists())
        } finally {
            old.delete()
            active.delete()
            other.delete()
        }
    }

    @Test
    fun invalidAndInterruptedBackupsWipePasswordsBeforeReturning() = runBlocking {
        val backup = PortableBackup(app)
        val short = "short".toCharArray()
        assertTrue(runCatching { backup.export(short) }.isFailure)
        assertTrue(short.all { it == '\u0000' })
        val password = "long-enough-test-password".toCharArray()
        assertTrue(runCatching { backup.restore(byteArrayOf(1, 2), password) }.isFailure)
        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun receiptStorageRejectsEmptyImagesTraversalAndTampering() {
        val storage = app.storage
        assertTrue(runCatching { storage.saveReceipt("test-empty", byteArrayOf()) }.isFailure)
        for (ref in
            listOf("../database-key.bin", "/receipt.bin", "folder/receipt.bin", "receipt.jpg")) {
            assertTrue(runCatching { storage.readReceipt(ref) }.isFailure)
        }
        val plain = "fictional-receipt".toByteArray()
        val encrypted = storage.encrypt(plain)
        assertArrayEquals(plain, storage.decrypt(encrypted))
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertTrue(runCatching { storage.decrypt(encrypted) }.isFailure)
        val ref = storage.saveReceipt("test-roundtrip-${System.nanoTime()}", plain)
        try {
            assertArrayEquals(plain, storage.readReceipt(ref))
        } finally {
            storage.deleteReceipt(ref)
        }
    }
}
