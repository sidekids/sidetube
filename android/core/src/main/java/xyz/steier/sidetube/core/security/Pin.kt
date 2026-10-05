// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.security

import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Die PIN ist die einzige Schranke zwischen Kind und Elternbereich. Sie wird nie im Klartext
 * abgelegt, sondern als Ableitung mit Zufallssalz; das Vergleichen laeuft in konstanter Zeit.
 */
object PinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    data class Hashed(val salt: ByteArray, val key: ByteArray) {
        /** Zum Ablegen: Salz und Ableitung in einer Zeile, durch Doppelpunkt getrennt. */
        fun encode(): String = "${salt.toHex()}:${key.toHex()}"

        override fun equals(other: Any?): Boolean =
            other is Hashed && salt.contentEquals(other.salt) && key.contentEquals(other.key)

        override fun hashCode(): Int = 31 * salt.contentHashCode() + key.contentHashCode()
    }

    fun hash(pin: String): Hashed {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return Hashed(salt, derive(pin, salt))
    }

    fun verify(pin: String, encoded: String): Boolean {
        val parts = encoded.split(':')
        if (parts.size != 2) return false
        if (parts[0].length != SALT_BYTES * 2 || parts[1].length != KEY_LENGTH_BITS / 4) return false
        val salt = parts[0].fromHex() ?: return false
        val expected = parts[1].fromHex() ?: return false
        return MessageDigest.isEqual(derive(pin, salt), expected)
    }

    private fun derive(pin: String, salt: ByteArray, lengthBytes: Int = KEY_LENGTH_BITS / 8): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, lengthBytes * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? {
        if (length % 2 != 0) return null
        return runCatching {
            chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }.getOrNull()
    }
}

/**
 * Bremse gegen Durchprobieren: Nach je fuenf Fehlversuchen wird gesperrt, und die Sperre
 * verdoppelt sich mit jeder Stufe. Vier Ziffern sind schnell geraten, wenn man beliebig oft darf.
 */
object PinLockoutPolicy {
    const val THRESHOLD = 5
    const val BASE_SECONDS = 30L
    // Saturate at a lockout boundary, not Int.MAX_VALUE (which is between boundaries).
    private const val MAX_FAILURES = Int.MAX_VALUE - Int.MAX_VALUE % THRESHOLD

    /** A corrupt/overflowing persisted count must never restart the free-attempt window. */
    fun nextFailureCount(stored: Int): Int =
        if (stored < 0 || stored >= MAX_FAILURES) MAX_FAILURES else stored + 1

    /** Sperrdauer in Sekunden, wenn nach [failures] Fehlversuchen eine faellig ist, sonst `null`. */
    fun lockoutSeconds(failures: Int): Long? {
        if (failures < THRESHOLD || failures % THRESHOLD != 0) return null
        val tier = failures / THRESHOLD
        // Bound before shifting: JVM shifts wrap modulo 64 and can otherwise disable lockout.
        return (BASE_SECONDS shl (tier - 1).coerceAtMost(7)).coerceAtMost(3600L)
    }

    fun attemptsRemaining(failures: Int): Int = THRESHOLD - (failures % THRESHOLD)
}
