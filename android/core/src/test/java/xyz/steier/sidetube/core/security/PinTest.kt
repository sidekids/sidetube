// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PinHasherTest {

    @Test
    fun `die richtige PIN wird erkannt, eine falsche nicht`() {
        val encoded = PinHasher.hash("1234").encode()

        assertThat(PinHasher.verify("1234", encoded)).isTrue()
        assertThat(PinHasher.verify("1235", encoded)).isFalse()
        assertThat(PinHasher.verify("", encoded)).isFalse()
        assertThat(PinHasher.verify("12345", encoded)).isFalse()
    }

    @Test
    fun `dieselbe PIN ergibt zweimal verschiedene Ableitungen`() {
        val a = PinHasher.hash("1234")
        val b = PinHasher.hash("1234")

        assertThat(a.key).isNotEqualTo(b.key)
        assertThat(a.salt).isNotEqualTo(b.salt)
        assertThat(PinHasher.verify("1234", a.encode())).isTrue()
        assertThat(PinHasher.verify("1234", b.encode())).isTrue()
    }

    @Test
    fun `die PIN steht nirgends im Klartext`() {
        assertThat(PinHasher.hash("1234").encode()).doesNotContain("1234")
    }

    @Test
    fun `beschaedigte Ablagen fuehren zu einer Ablehnung, nicht zu einem Absturz`() {
        for (kaputt in listOf("", "kein-doppelpunkt", "zz:zz", "abc:", ":abc", "a:b:c", ":", "00:00",
            "00".repeat(16) + ":", ":" + "00".repeat(32), "00".repeat(17) + ":" + "00".repeat(32))) {
            assertThat(PinHasher.verify("1234", kaputt)).isFalse()
        }
    }
}

class PinLockoutPolicyTest {

    @Test
    fun `normal persisted counts keep the existing five attempt cadence`() {
        for (stored in 0..1000) {
            val next = PinLockoutPolicy.nextFailureCount(stored)
            assertThat(next).isEqualTo(stored + 1)
            assertThat(PinLockoutPolicy.lockoutSeconds(next) != null).isEqualTo(next % 5 == 0)
        }
    }

    @Test
    fun `corrupt or overflowing counts stay on a maximum lockout boundary`() {
        for (stored in listOf(Int.MIN_VALUE, -1, Int.MAX_VALUE - 3, Int.MAX_VALUE - 2,
                              Int.MAX_VALUE - 1, Int.MAX_VALUE)) {
            var count = stored
            repeat(10) {
                count = PinLockoutPolicy.nextFailureCount(count)
                assertThat(count).isAtLeast(0)
                assertThat(PinLockoutPolicy.lockoutSeconds(count)).isEqualTo(3600L)
                assertThat(PinLockoutPolicy.attemptsRemaining(count)).isIn(1..5)
            }
        }
    }

    @Test
    fun `hohe Fehlversuchszahlen koennen die Sperre nicht durch Ueberlauf aufheben`() {
        for (n in listOf(40, 320, 325, 1000, 2_147_483_645)) {
            assertThat(PinLockoutPolicy.lockoutSeconds(n)).isEqualTo(3600L)
        }
    }

    @Test
    fun `vor dem fuenften Fehlversuch wird nicht gesperrt`() {
        for (n in 0..4) assertThat(PinLockoutPolicy.lockoutSeconds(n)).isNull()
    }

    @Test
    fun `die Sperre verdoppelt sich mit jeder Stufe`() {
        assertThat(PinLockoutPolicy.lockoutSeconds(5)).isEqualTo(30)
        assertThat(PinLockoutPolicy.lockoutSeconds(10)).isEqualTo(60)
        assertThat(PinLockoutPolicy.lockoutSeconds(15)).isEqualTo(120)
        assertThat(PinLockoutPolicy.lockoutSeconds(20)).isEqualTo(240)
    }

    @Test
    fun `zwischen den Stufen wird nicht erneut gesperrt`() {
        for (n in listOf(6, 7, 8, 9, 11, 12)) assertThat(PinLockoutPolicy.lockoutSeconds(n)).isNull()
    }

    @Test
    fun `die verbleibenden Versuche zaehlen bis zur naechsten Sperre herunter`() {
        assertThat(PinLockoutPolicy.attemptsRemaining(0)).isEqualTo(5)
        assertThat(PinLockoutPolicy.attemptsRemaining(1)).isEqualTo(4)
        assertThat(PinLockoutPolicy.attemptsRemaining(4)).isEqualTo(1)
        assertThat(PinLockoutPolicy.attemptsRemaining(5)).isEqualTo(5)
    }
}
