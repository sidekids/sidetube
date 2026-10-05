// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test
import xyz.steier.sidetube.core.elternkanal.FluechtigeAblage
import xyz.steier.sidetube.core.elternkanal.TalkBotMelder
import xyz.steier.sidetube.core.elternkanal.UrlConnectionPoster

/**
 * „Test senden" gegen eine echte Nextcloud – wie `ElternkanalUITests` auf iOS. Der Einrichtungscode kommt
 * nur über die Umgebung (`ELTERNKANAL_CODE`); ohne ihn wird der Test übersprungen.
 */
class ElternkanalEchtTest {
    @Test fun `Test senden kommt an`() = runTest {
        val code = System.getenv("ELTERNKANAL_CODE")
        assumeTrue("Kein Einrichtungscode in der Umgebung", !code.isNullOrBlank())
        val ablage = FluechtigeAblage()
        val einrichtung = ElternkanalEinrichtung(ablage, TalkBotMelder({ ablage.lade() }, UrlConnectionPoster()))
        assertThat(einrichtung.uebernimm(code!!)).startsWith("Eingerichtet.")
        assertThat(einrichtung.teste()).isEqualTo("Gesendet. Die Meldung erscheint in der Nextcloud-App.")
    }
}
