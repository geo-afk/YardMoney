package jm.yardmoney.core

import javax.crypto.AEADBadTagException
import org.junit.Assert.*
import org.junit.Test

class BackupCipherTest {
    private val password = "A long private test password".toCharArray()

    @Test
    fun portableRoundTripPreservesExactSnapshotBytes() {
        val plain = "{\"minor\":100000000000000,\"store\":\"Jamaica\"}".toByteArray()
        val encrypted = BackupCipher.encrypt(plain, password)
        assertArrayEquals(plain, BackupCipher.decrypt(encrypted, password))
        assertFalse(encrypted.contentEquals(plain))
    }

    @Test
    fun eachBackupGetsFreshSaltAndNonce() {
        val plain = "test snapshot".toByteArray()
        assertFalse(
            BackupCipher.encrypt(plain, password)
                .contentEquals(BackupCipher.encrypt(plain, password))
        )
    }

    @Test
    fun wrongPasswordRejected() {
        val bytes = BackupCipher.encrypt("private".toByteArray(), password)
        assertThrows(AEADBadTagException::class.java) {
            BackupCipher.decrypt(bytes, "Wrong password for test".toCharArray())
        }
    }

    @Test
    fun modifiedCiphertextRejected() {
        val bytes = BackupCipher.encrypt("private".toByteArray(), password)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) { BackupCipher.decrypt(bytes, password) }
    }

    @Test
    fun unknownFormatAndTruncationRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCipher.decrypt(ByteArray(100), password)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCipher.decrypt(ByteArray(20), password)
        }
    }

    @Test
    fun shortExportPasswordRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCipher.encrypt(byteArrayOf(1), "short".toCharArray())
        }
    }
}
