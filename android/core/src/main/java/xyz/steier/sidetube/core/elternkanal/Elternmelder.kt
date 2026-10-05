// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.elternkanal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Ergebnis einer Meldung an die Eltern. Ein Fehlschlag aendert nichts am Wunsch. */
sealed interface Meldung {
    data object Gesendet : Meldung
    data object NichtEingerichtet : Meldung
    /** HTTP-Status, oder null bei Netzfehler. */
    data class Gescheitert(val status: Int?) : Meldung
}

/** Meldet den Eltern, dass ein Wunsch angelegt wurde (ADR 0005). */
interface Elternmelder {
    suspend fun neuerWunsch(offen: Int): Meldung
}

/** Ohne Einrichtung: meldet nichts. */
object KeinMelder : Elternmelder {
    override suspend fun neuerWunsch(offen: Int): Meldung = Meldung.NichtEingerichtet
}

/** Schmaler POST nur fuer den Elternkanal – getrennt vom `HttpClient` der YouTube-Abfragen. */
interface HttpPoster {
    suspend fun post(url: String, headers: Map<String, String>, body: String): Int
}

object TalkBot {
    /** Was in das Gespraech geschrieben wird – ohne Namen des Kindes, ohne Titel oder Thema. */
    fun wunschNachricht(erwaehnen: List<String>, offen: Int): String =
        erwaehnen.joinToString(" ") { erwaehnung(it) } + " SideTube: neuer Wunsch (${maxOf(offen, 1)} offen)"

    /** `@anna`; Namen mit Leer- oder Sonderzeichen in Anfuehrungszeichen, wie Talk sie erwartet. */
    fun erwaehnung(nutzer: String): String =
        if (Regex("[A-Za-z0-9_.\\-]+").matches(nutzer)) "@$nutzer" else "@\"$nutzer\""

    /** HMAC-SHA256(Schluessel, Zufallswert + Nachrichtentext), klein-hex – so prueft Talk Bot-Nachrichten. */
    fun signatur(schluessel: String, zufall: String, nachricht: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(schluessel.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal((zufall + nachricht).toByteArray(Charsets.UTF_8)).toHex()
    }

    /** 32 Byte Zufall als Hex. */
    fun zufall(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }.toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

/**
 * Meldet ueber einen Nextcloud-Talk-Bot. Die Einrichtung wird bei jeder Meldung frisch gelesen,
 * damit „Entfernen" sofort wirkt.
 */
class TalkBotMelder(
    private val kanal: () -> Elternkanal?,
    private val poster: HttpPoster,
    private val zufall: () -> String = TalkBot::zufall
) : Elternmelder {

    override suspend fun neuerWunsch(offen: Int): Meldung {
        val kanal = kanal() ?: return Meldung.NichtEingerichtet
        return sende(TalkBot.wunschNachricht(kanal.erwaehnen, offen), kanal)
    }

    /** Auch fuer „Test senden" in den Einstellungen. */
    suspend fun sende(nachricht: String, kanal: Elternkanal): Meldung {
        val wert = zufall()
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "OCS-APIRequest" to "true",
            "X-Nextcloud-Talk-Bot-Random" to wert,
            "X-Nextcloud-Talk-Bot-Signature" to TalkBot.signatur(kanal.schluessel, wert, nachricht)
        )
        val body = buildJsonObject { put("message", JsonPrimitive(nachricht)) }.toString()
        return try {
            val status = poster.post(kanal.nachrichtenAdresse, headers, body)
            if (status == 201 || status == 200) Meldung.Gesendet else Meldung.Gescheitert(status)
        } catch (e: Exception) {
            Meldung.Gescheitert(null)
        }
    }
}

/**
 * POST ohne Weiterleitungen: Die Signatur soll an keinen anderen Server gehen als den
 * eingetragenen. Antworttext wird nicht gebraucht.
 */
class UrlConnectionPoster(
    private val timeoutMillis: Int = 15_000,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : HttpPoster {
    override suspend fun post(url: String, headers: Map<String, String>, body: String): Int = withContext(dispatcher) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            instanceFollowRedirects = false
            useCaches = false
            doOutput = true
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }
}
