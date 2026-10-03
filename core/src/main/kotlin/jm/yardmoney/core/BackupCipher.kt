package jm.yardmoney.core

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCipher {
    private val header = "YARD01".toByteArray(Charsets.US_ASCII)

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 600_000, 256)
        return try {
            val bytes =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try {
                SecretKeySpec(bytes, "AES")
            } finally {
                bytes.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(clear: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 12) { "Use at least 12 characters." }
        require(clear.size <= 50_000_000)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt))
        return header + salt + cipher.iv + cipher.doFinal(clear)
    }

    fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(bytes.size in 50..50_000_050 && bytes.copyOfRange(0, 6).contentEquals(header)) {
            "Not a supported YardMoney backup."
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(password, bytes.copyOfRange(6, 22)),
            GCMParameterSpec(128, bytes.copyOfRange(22, 34)),
        )
        return cipher.doFinal(bytes.copyOfRange(34, bytes.size))
    }
}
