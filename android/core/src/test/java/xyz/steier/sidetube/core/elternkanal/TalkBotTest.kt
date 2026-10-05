// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.elternkanal

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/** Elternkanal (ADR 0005): dieselben Faelle wie `ParentChannelTests` auf iOS. */
class TalkBotTest {
    private val schluessel = "0123456789abcdef".repeat(4)

    private fun code(vararg aenderungen: Pair<String, JsonElement>): String {
        val felder = mutableMapOf<String, JsonElement>(
            "v" to JsonPrimitive(1), "art" to JsonPrimitive("talk"), "server" to JsonPrimitive("https://wolke.example.org"),
            "gespraech" to JsonPrimitive("abcd2345"), "schluessel" to JsonPrimitive(schluessel),
            "erwaehnen" to JsonArray(listOf(JsonPrimitive("anna")))
        )
        felder.putAll(aenderungen)
        return JsonObject(felder).toString()
    }

    @Test
    fun `gueltiger Code wird gelesen`() {
        val kanal = Elternkanal.lies(code("server" to JsonPrimitive("https://wolke.example.org/")))
        assertThat(kanal.server).isEqualTo("https://wolke.example.org")
        assertThat(kanal.erwaehnen).containsExactly("anna")
        assertThat(kanal.nachrichtenAdresse)
            .isEqualTo("https://wolke.example.org/ocs/v2.php/apps/spreed/api/v1/bot/abcd2345/message")
        assertThat(kanal.toString()).doesNotContain(schluessel)
    }

    @Test
    fun `Unterordner bleibt erhalten`() {
        val kanal = Elternkanal.lies(code("server" to JsonPrimitive("https://example.org/nextcloud")))
        assertThat(kanal.nachrichtenAdresse)
            .isEqualTo("https://example.org/nextcloud/ocs/v2.php/apps/spreed/api/v1/bot/abcd2345/message")
    }

    @Test
    fun `ungueltige Codes werden abgelehnt`() {
        listOf(
            "v" to JsonPrimitive(2), "art" to JsonPrimitive("ntfy"),
            "server" to JsonPrimitive("http://wolke.example.org"), "server" to JsonPrimitive("https://user:pw@wolke.example.org"),
            "gespraech" to JsonPrimitive("../x"), "schluessel" to JsonPrimitive("kurz"),
            "schluessel" to JsonPrimitive("a".repeat(129)), "erwaehnen" to JsonArray(emptyList()),
            "erwaehnen" to JsonArray(listOf(JsonPrimitive("anna\nben")))
        ).forEach { aenderung ->
            assertThrows(aenderung.toString(), UngueltigerCode::class.java) { Elternkanal.lies(code(aenderung)) }
        }
        assertThrows(UngueltigerCode::class.java) { Elternkanal.lies("hallo") }
    }

    @Test
    fun `Ablage haelt den Kanal und Entfernen wirkt sofort`() = runTest {
        val kanal = Elternkanal.lies(code())
        assertThat(elternkanalAusJson(kanal.alsJson())).isEqualTo(kanal)
        val ablage = FluechtigeAblage(kanal)
        val poster = MerkendePoster(201)
        val melder = TalkBotMelder({ ablage.lade() }, poster)
        assertThat(melder.neuerWunsch(1)).isEqualTo(Meldung.Gesendet)
        ablage.loesche()
        assertThat(melder.neuerWunsch(1)).isEqualTo(Meldung.NichtEingerichtet)
        assertThat(poster.anfragen).hasSize(1)
    }

    @Test
    fun `Nachricht erwaehnt die Eltern ohne Daten des Kindes`() {
        assertThat(TalkBot.wunschNachricht(listOf("anna"), 2)).isEqualTo("@anna SideTube: neuer Wunsch (2 offen)")
        assertThat(TalkBot.wunschNachricht(listOf("anna", "ben beispiel"), 1))
            .isEqualTo("@anna @\"ben beispiel\" SideTube: neuer Wunsch (1 offen)")
    }

    /** Pruefwerte gemeinsam mit iOS (`ParentChannelTests`), berechnet mit Python `hmac`. */
    @Test
    fun `Signatur stimmt mit den gemeinsamen Pruefwerten`() {
        assertThat(TalkBot.signatur(schluessel, "00".repeat(32), "@anna SideTube: neuer Wunsch (2 offen)"))
            .isEqualTo("c39c69b26169f30a08482fe29dca8a9a17c47a618ce24d02dcdb1146a6cdcdfa")
        assertThat(TalkBot.signatur(schluessel, "ff".repeat(32), "@anna @\"ben beispiel\" SideTube: neuer Wunsch (1 offen)"))
            .isEqualTo("caa79f952fc25b29f16781e909af5731046655d5fb134575753b56a7a9124411")
    }

    @Test
    fun `Zufall ist 64 Hex-Zeichen`() {
        val wert = TalkBot.zufall()
        assertThat(wert).matches("[0-9a-f]{64}")
        assertThat(wert).isNotEqualTo(TalkBot.zufall())
    }

    @Test
    fun `Melder sendet signierte Anfrage`() = runTest {
        val kanal = Elternkanal.lies(code())
        val poster = MerkendePoster(201)
        val melder = TalkBotMelder({ kanal }, poster) { "00".repeat(32) }
        assertThat(melder.neuerWunsch(2)).isEqualTo(Meldung.Gesendet)
        val anfrage = poster.anfragen.single()
        assertThat(anfrage.url).isEqualTo(kanal.nachrichtenAdresse)
        assertThat(anfrage.headers["OCS-APIRequest"]).isEqualTo("true")
        assertThat(anfrage.headers["X-Nextcloud-Talk-Bot-Signature"])
            .isEqualTo("c39c69b26169f30a08482fe29dca8a9a17c47a618ce24d02dcdb1146a6cdcdfa")
        assertThat(Json.parseToJsonElement(anfrage.body).jsonObject["message"]?.jsonPrimitive?.content)
            .isEqualTo("@anna SideTube: neuer Wunsch (2 offen)")
    }

    @Test
    fun `Melder meldet Fehler und fehlende Einrichtung`() = runTest {
        val kanal = Elternkanal.lies(code())
        assertThat(TalkBotMelder({ null }, MerkendePoster(201)).neuerWunsch(1)).isEqualTo(Meldung.NichtEingerichtet)
        assertThat(TalkBotMelder({ kanal }, MerkendePoster(401)).neuerWunsch(1)).isEqualTo(Meldung.Gescheitert(401))
        assertThat(TalkBotMelder({ kanal }, MerkendePoster(fehler = IOException("offline"))).neuerWunsch(1))
            .isEqualTo(Meldung.Gescheitert(null))
    }
}

/** Merkt sich POSTs statt sie zu senden. */
class MerkendePoster(private val status: Int = 201, private val fehler: Exception? = null) : HttpPoster {
    data class Anfrage(val url: String, val headers: Map<String, String>, val body: String)
    val anfragen = mutableListOf<Anfrage>()
    override suspend fun post(url: String, headers: Map<String, String>, body: String): Int {
        anfragen += Anfrage(url, headers, body)
        fehler?.let { throw it }
        return status
    }
}
