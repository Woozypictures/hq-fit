package uk.woozy.fit.data.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongRecoveryKeyException : Exception("The recovery key does not open this data")

/**
 * Plain-JVM crypto for the database key.
 *
 * The database key is 32 random bytes. It is stored wrapped twice: once by a phone Keystore key (day to day),
 * and once by a key derived from a recovery key shown to the user once (for a new phone or a lost Keystore).
 * This object does the recovery half and is unit tested on the JVM.
 */
object RecoveryCrypto {
    // No 0, O, 1, I or L, so a key read off a screen and typed back has fewer mistakes.
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val KEY_CHARS = 24
    private const val GROUP = 4
    private const val ITERATIONS = 200_000
    private const val IV_BYTES = 12

    private val random = SecureRandom()

    fun newDbKey(): ByteArray = ByteArray(32).also { random.nextBytes(it) }

    fun newSalt(): ByteArray = ByteArray(16).also { random.nextBytes(it) }

    /** 24 characters from a 31-letter alphabet is about 119 bits, shown as six groups of four. */
    fun newRecoveryKey(): String {
        val chars = CharArray(KEY_CHARS) { ALPHABET[random.nextInt(ALPHABET.length)] }
        return String(chars).chunked(GROUP).joinToString("-")
    }

    /** Upper case, drop spaces and dashes, so "abcd efgh" and "ABCD-EFGH" are the same key. */
    fun normalise(input: String): String = input.uppercase().filter { it in ALPHABET }

    fun isWellFormed(input: String): Boolean = normalise(input).length == KEY_CHARS

    fun wrap(dbKey: ByteArray, recoveryKey: String, salt: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(recoveryKey, salt), GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(dbKey)
    }

    /** Throws [WrongRecoveryKeyException] when the key is wrong or the blob was altered (GCM authenticates both). */
    fun unwrap(blob: ByteArray, recoveryKey: String, salt: ByteArray): ByteArray {
        if (blob.size <= IV_BYTES) throw WrongRecoveryKeyException()
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                derive(recoveryKey, salt),
                GCMParameterSpec(128, blob.copyOfRange(0, IV_BYTES)),
            )
            cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
        } catch (e: GeneralSecurityException) {
            throw WrongRecoveryKeyException()
        }
    }

    private fun derive(recoveryKey: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(normalise(recoveryKey).toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }
}
