// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import xyz.steier.sidetube.core.elternkanal.FluechtigeAblage
import xyz.steier.sidetube.core.elternkanal.HttpPoster
import xyz.steier.sidetube.core.elternkanal.TalkBotMelder

/** Einstellungsseite „Eltern benachrichtigen" (ADR 0005, Phase 4) – dieselben Faelle wie iOS. */
class ElternkanalEinrichtungTest {
    private val schluessel = "0123456789abcdef".repeat(4)
    private val code = """{"v":1,"art":"talk","server":"https://wolke.example.org","gespraech":"abcd2345",""" +
        """"schluessel":"$schluessel","erwaehnen":["anna"]}"""

    private class Poster(private val status: Int) : HttpPoster {
        val bodies = mutableListOf<String>()
        override suspend fun post(url: String, headers: Map<String, String>, body: String): Int { bodies += body; return status }
    }

    @Test fun `einrichten, testen, entfernen`() = runTest {
        val ablage = FluechtigeAblage()
        val poster = Poster(201)
        val einrichtung = ElternkanalEinrichtung(ablage, TalkBotMelder({ ablage.lade() }, poster), xyz.steier.sidetube.TestTexte)
        assertThat(einrichtung.kanal).isNull()

        assertThat(einrichtung.uebernimm("Unsinn")).startsWith("Der Einrichtungscode passt nicht")
        assertThat(einrichtung.kanal).isNull()

        assertThat(einrichtung.uebernimm(code)).startsWith("Eingerichtet.")
        assertThat(einrichtung.kanal?.gespraech).isEqualTo("abcd2345")

        assertThat(einrichtung.teste()).isEqualTo("Gesendet. Die Meldung erscheint in der Nextcloud-App.")
        assertThat(Json.parseToJsonElement(poster.bodies.single()).jsonObject["message"]?.jsonPrimitive?.content)
            .isEqualTo("@anna SideTube: Test der Benachrichtigung")

        // Ein ungueltiger zweiter Code laesst die Einrichtung stehen.
        einrichtung.uebernimm("{}")
        assertThat(einrichtung.kanal).isNotNull()

        assertThat(einrichtung.entferne()).startsWith("Entfernt.")
        assertThat(einrichtung.kanal).isNull()
    }

    @Test fun `abgelehnter Test wird erklaert`() = runTest {
        val ablage = FluechtigeAblage()
        val einrichtung = ElternkanalEinrichtung(ablage, TalkBotMelder({ ablage.lade() }, Poster(401)), xyz.steier.sidetube.TestTexte)
        einrichtung.uebernimm(code)
        assertThat(einrichtung.teste()).isEqualTo("Abgelehnt: Schlüssel oder Bot passen nicht (HTTP 401).")
    }
}
