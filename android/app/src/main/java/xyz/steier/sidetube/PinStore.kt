// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import xyz.steier.sidetube.core.security.PinHasher
import xyz.steier.sidetube.core.security.PinLockoutPolicy

/** Ergebnis eines Eingabeversuchs. */
sealed interface PinResult {
    data object Success : PinResult
    data class Wrong(val attemptsRemaining: Int) : PinResult
    data class LockedOut(val secondsRemaining: Long) : PinResult
}

/**
 * Ablage der Eltern-PIN. Die PIN selbst wird nie gespeichert, nur ihre Ableitung; der Zaehler
 * fuer Fehlversuche liegt daneben, damit ein Neustart die Sperre nicht aufhebt.
 */
class PinStore(context: Context, private val now: () -> Long = System::currentTimeMillis) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "sidetube-parent",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val isConfigured: Boolean get() = prefs.getString(KEY_HASH, null) != null

    fun set(pin: String) {
        prefs.edit()
            .putString(KEY_HASH, PinHasher.hash(pin).encode())
            .remove(KEY_FAILURES).remove(KEY_LOCKED_UNTIL)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    fun verify(pin: String): PinResult {
        lockoutRemaining()?.let { return PinResult.LockedOut(it) }
        val stored = prefs.getString(KEY_HASH, null) ?: return PinResult.Wrong(PinLockoutPolicy.THRESHOLD)

        if (PinHasher.verify(pin, stored)) {
            prefs.edit().remove(KEY_FAILURES).remove(KEY_LOCKED_UNTIL).apply()
            return PinResult.Success
        }

        val failures = PinLockoutPolicy.nextFailureCount(prefs.getInt(KEY_FAILURES, 0))
        val editor = prefs.edit().putInt(KEY_FAILURES, failures)
        val lockout = PinLockoutPolicy.lockoutSeconds(failures)
        if (lockout != null) editor.putLong(KEY_LOCKED_UNTIL, now() + lockout * 1000)
        editor.apply()

        return if (lockout != null) PinResult.LockedOut(lockout)
        else PinResult.Wrong(PinLockoutPolicy.attemptsRemaining(failures))
    }

    fun lockoutRemaining(): Long? {
        val until = prefs.getLong(KEY_LOCKED_UNTIL, 0)
        if (until <= now()) return null
        return (until - now() + 999) / 1000
    }

    private companion object {
        const val KEY_HASH = "pin"
        const val KEY_FAILURES = "failures"
        const val KEY_LOCKED_UNTIL = "lockedUntil"
    }
}
