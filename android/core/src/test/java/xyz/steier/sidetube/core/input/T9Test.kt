// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.input

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Ziffernsuche fuer das SidePhone SP-01 – dieselbe Belegung wie in SidePlay. */
class T9Test {

    @Test
    fun `Buchstaben liegen auf den Tasten wie am Telefon`() {
        assertThat(T9.ziffern("bunny")).isEqualTo("28669")
        assertThat(T9.ziffern("Big Buck Bunny")).isEqualTo("24428252866 9".replace(" ", ""))
        assertThat(T9.ziffern("pqrs wxyz")).isEqualTo("77779999")
        assertThat(T9.buchstaben('2')).isEqualTo("abc")
        assertThat(T9.buchstaben('9')).isEqualTo("wxyz")
        assertThat(T9.buchstaben('0')).isEmpty()
    }

    @Test
    fun `Umlaute liegen auf ihrem Grundbuchstaben`() {
        assertThat(T9.ziffern("ä")).isEqualTo("2")
        assertThat(T9.ziffern("ö")).isEqualTo("6")
        assertThat(T9.ziffern("Ü")).isEqualTo("8")
        assertThat(T9.ziffern("ß")).isEqualTo("7")
        assertThat(T9.ziffern("Bär")).isEqualTo("227")
    }

    @Test
    fun `Umlaute passen auch aufgeloest wie in SidePlay`() {
        assertThat(T9.passt("227", "Der Bär")).isTrue()    // b-ä-r
        assertThat(T9.passt("2237", "Der Bär")).isTrue()   // b-a-e-r
        assertThat(T9.passt("787273", "Straße")).isTrue()  // s-t-r-a-ß-e
        assertThat(T9.passt("7872773", "Straße")).isTrue() // s-t-r-a-s-s-e
        assertThat(T9.passt("6873", "Möwe")).isFalse()
        assertThat(T9.passt("6693", "Möwe")).isTrue()      // m-ö-w-e
        assertThat(T9.passt("66393", "Möwe")).isTrue()     // m-o-e-w-e
    }

    @Test
    fun `bunny findet Big Buck Bunny am Wortanfang`() {
        assertThat(T9.passt("28669", "Big Buck Bunny")).isTrue()
        assertThat(T9.passt("2", "Big Buck Bunny")).isTrue()
        assertThat(T9.passt("282", "Big Buck Bunny")).isTrue()   // buc…
        assertThat(T9.passt("8669", "Big Buck Bunny")).isFalse()  // „unny" ist kein Wortanfang
        assertThat(T9.passt("999", "Big Buck Bunny")).isFalse()
    }

    @Test
    fun `die 0 trennt Woerter`() {
        assertThat(T9.passt("244028", "Big Buck Bunny")).isTrue()    // big bu…
        assertThat(T9.passt("2820286", "Big Buck Bunny")).isTrue()   // bu… bun…
        assertThat(T9.passt("28669024", "Big Buck Bunny")).isFalse() // nach Bunny kommt nichts
        assertThat(T9.passt("244", "Sintel")).isFalse()
    }

    @Test
    fun `ohne Luecke passt auch der Anfang des ganzen Namens`() {
        assertThat(T9.passt("24428", "Big Buck Bunny")).isTrue()   // „bigbu"
    }

    @Test
    fun `Satzzeichen trennen Woerter, Ziffern stehen fuer sich`() {
        assertThat(T9.passt("2337", "Elephants-Dream: Beer")).isTrue()
        assertThat(T9.passt("11", "Apollo 11")).isTrue()
    }

    @Test
    fun `Ziffernfolge und Text werden unterschieden`() {
        assertThat(T9.istZiffernfolge("28669")).isTrue()
        assertThat(T9.istZiffernfolge("bunny")).isFalse()
        assertThat(T9.istZiffernfolge("2a")).isFalse()
        assertThat(T9.istZiffernfolge("")).isFalse()
        assertThat(T9.hatInhalt("000")).isFalse()
        assertThat(T9.hatInhalt("02")).isTrue()
    }
}
