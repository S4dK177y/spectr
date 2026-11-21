package app.spectr.mood.security

import app.spectr.mood.domain.model.StoredPinCredential
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2-HMAC-SHA256 hashing and constant-time verification for the optional local App PIN.
 *
 * Pure JVM implementation without Android dependencies so it runs directly in fast unit tests.
 */
object PinHashManager {

    const val PIN_LENGTH = 4
    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256
    private const val PBKDF2_ITERATIONS = 100_000
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    private val secureRandom = SecureRandom()

    fun isValidPinFormat(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it.isDigit() }

    /**
     * Derives a new salted [StoredPinCredential] from a validated 4-digit [pin].
     */
    fun createCredential(pin: String): StoredPinCredential {
        require(isValidPinFormat(pin)) { "PIN must consist of $PIN_LENGTH digits" }
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val hash = deriveHash(pin, salt)
        return StoredPinCredential(
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            hashBase64 = Base64.getEncoder().encodeToString(hash)
        )
    }

    /**
     * Verifies [candidatePin] against [credential] using constant-time byte comparison.
     */
    fun verifyPin(candidatePin: String, credential: StoredPinCredential): Boolean {
        if (!isValidPinFormat(candidatePin)) {
            return false
        }
        val salt = runCatching { Base64.getDecoder().decode(credential.saltBase64) }.getOrNull() ?: return false
        val expectedHash = runCatching { Base64.getDecoder().decode(credential.hashBase64) }.getOrNull()
            ?: return false
        val actualHash = deriveHash(candidatePin, salt)
        return MessageDigest.isEqual(actualHash, expectedHash)
    }

    private fun deriveHash(pin: String, salt: ByteArray): ByteArray {
        val chars = pin.toCharArray()
        val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }
}
