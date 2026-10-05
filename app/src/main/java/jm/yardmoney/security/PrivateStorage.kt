package jm.yardmoney.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore wraps the DB passphrase and encrypts receipt files. Loss of keys never silently resets
 * data.
 */
class PrivateStorage(private val context: Context) {
    private val alias = "yardmoney-local-v1"

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let {
            return it
        }
        check(!File(context.noBackupFilesDir, "database-key.bin").exists()) {
            "Your encryption key is missing. Restore a portable backup; do not overwrite existing data."
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    fun encrypt(bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return byteArrayOf(1, cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(bytes)
    }

    fun decrypt(bytes: ByteArray): ByteArray {
        require(bytes.size > 30 && bytes[0] == 1.toByte() && bytes[1] == 12.toByte()) {
            "Invalid encrypted file."
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(2, 14)))
        return cipher.doFinal(bytes.copyOfRange(14, bytes.size))
    }

    @Synchronized
    fun databasePassphrase(): ByteArray {
        val file = AtomicFile(File(context.noBackupFilesDir, "database-key.bin"))
        if (file.baseFile.exists()) return decrypt(file.readFully())
        // Existing DB without its wrapper key must be recovered, never opened with a new key.
        check(!context.getDatabasePath("yardmoney.db").exists()) {
            "The database key is missing. Recovery is required."
        }
        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val stream = file.startWrite()
        try {
            stream.write(encrypt(random))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
        return random
    }

    fun saveReceipt(id: String, bytes: ByteArray): String {
        require(Regex("[a-zA-Z0-9-]+").matches(id))
        require(bytes.isNotEmpty() && bytes.size <= 15_000_000) {
            "Receipt image is empty or too large."
        }
        val dir = File(context.noBackupFilesDir, "receipts").apply { mkdirs() }
        val file = AtomicFile(File(dir, "$id.bin"))
        val out = file.startWrite()
        try {
            out.write(encrypt(bytes))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
        return "$id.bin"
    }

    fun readReceipt(ref: String): ByteArray {
        require(Regex("[a-zA-Z0-9-]+\\.bin").matches(ref))
        val file = File(context.noBackupFilesDir, "receipts/$ref")
        require(file.length() <= 15_000_030) { "Stored receipt image is too large." }
        return decrypt(file.readBytes())
    }

    fun deleteReceipt(ref: String) {
        require(Regex("[a-zA-Z0-9-]+\\.bin").matches(ref))
        File(context.noBackupFilesDir, "receipts/$ref").delete()
    }

    fun removeExpiredTempReceipts(nowMillis: Long = System.currentTimeMillis()) {
        // Interrupted scans may leave plaintext cache photos; keep active drafts and unrelated
        // exports.
        val cutoff = nowMillis - 24L * 60 * 60 * 1000
        File(context.cacheDir, "exports")
            .listFiles()
            ?.filter {
                it.isFile &&
                    Regex("receipt-[a-zA-Z0-9-]+\\.jpg").matches(it.name) &&
                    it.lastModified() < cutoff
            }
            ?.forEach { it.delete() }
    }

    fun deleteAllReceiptFiles() {
        File(context.noBackupFilesDir, "receipts")
            .listFiles()
            ?.filter { it.isFile && Regex("[a-zA-Z0-9-]+\\.bin").matches(it.name) }
            ?.forEach { check(it.delete()) { "A receipt file could not be deleted." } }
        File(context.cacheDir, "exports")
            .listFiles()
            ?.filter { it.isFile && it.name.startsWith("receipt-") }
            ?.forEach { it.delete() }
    }

    fun removeUnusedReceiptFiles(keep: Set<String>) {
        File(context.noBackupFilesDir, "receipts")
            .listFiles()
            ?.filter {
                it.isFile && Regex("[a-zA-Z0-9-]+\\.bin").matches(it.name) && it.name !in keep
            }
            ?.forEach { it.delete() }
    }
}
