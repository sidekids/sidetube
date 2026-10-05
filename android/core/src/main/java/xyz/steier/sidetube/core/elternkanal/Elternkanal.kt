// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.elternkanal

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URI

/**
 * Elternkanal (ADR 0005): wohin SideTube neue Wuensche meldet – ein Nextcloud-Talk-Gespraech, in
 * dem ein Bot schreiben darf. Kommt als Einrichtungscode aus `scripts/talk-wunschkanal.sh`.
 * Dieselben Regeln wie `ParentChannel` auf iOS.
 */
@Serializable
data class Elternkanal(
    /** Wurzel der Nextcloud, immer https, ohne Schraegstrich am Ende. */
    val server: String,
    /** Gespraechs-Token in Talk. */
    val gespraech: String,
    /** Gemeinsamer Schluessel des Bots. Nie anzeigen, nie loggen. */
    val schluessel: String,
    /** Nextcloud-Nutzer, die in jeder Meldung erwaehnt werden. */
    val erwaehnen: List<String>
) {
    val nachrichtenAdresse: String get() = "$server/ocs/v2.php/apps/spreed/api/v1/bot/$gespraech/message"

    /** Ohne Schluessel, falls das Objekt doch einmal in einem Text landet. */
    override fun toString(): String = "Elternkanal(server=$server, gespraech=$gespraech, erwaehnen=$erwaehnen)"

    companion object {
        private val GESPRAECH = Regex("[A-Za-z0-9]{4,32}")
        private val NUTZER = Regex("[A-Za-z0-9._@ \\-]{1,64}")

        /** Liest den Einrichtungscode (JSON, Version 1, Art „talk") und prueft jedes Feld. */
        fun lies(code: String): Elternkanal {
            val objekt = runCatching { Json.parseToJsonElement(code.trim()).jsonObject }
                .getOrElse { throw UngueltigerCode("kein Einrichtungscode") }
            if ((objekt["v"] as? JsonPrimitive)?.intOrNull != 1) throw UngueltigerCode("unbekannte Version")
            if (objekt.text("art") != "talk") throw UngueltigerCode("unbekannte Art")

            val server = objekt.text("server")?.let(::pruefeServer)
                ?: throw UngueltigerCode("Server muss mit https:// beginnen")
            val gespraech = objekt.text("gespraech")?.takeIf { GESPRAECH.matches(it) }
                ?: throw UngueltigerCode("Gespräch fehlt")
            val schluessel = objekt.text("schluessel")
                ?.takeIf { it.length in 40..128 && it.all { c -> c.code in 0x21..0x7e } }
                ?: throw UngueltigerCode("Schlüssel fehlt oder ist zu kurz")
            val erwaehnen = (objekt["erwaehnen"] as? JsonArray)
                ?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                ?.takeIf { it.isNotEmpty() && it.size <= 8 && it.all { n -> n != null && NUTZER.matches(n) } }
                ?.filterNotNull()
                ?: throw UngueltigerCode("keine gültigen Nutzernamen zum Erwähnen")
            return Elternkanal(server, gespraech, schluessel, erwaehnen)
        }

        private fun JsonObject.text(name: String): String? =
            (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun pruefeServer(text: String): String? {
            val uri = runCatching { URI(text) }.getOrNull() ?: return null
            if (uri.scheme != "https" || uri.host.isNullOrEmpty() || uri.userInfo != null ||
                uri.query != null || uri.fragment != null
            ) return null
            return text.trimEnd('/')
        }
    }
}

/** Ablage der Einrichtung. Der Schluessel gehoert in verschluesselten Speicher, nicht in die Datenbank. */
interface ElternkanalAblage {
    fun lade(): Elternkanal?
    fun speichere(kanal: Elternkanal)
    fun loesche()
}

/** Nur im Speicher – fuer Tests und Vorschauen. */
class FluechtigeAblage(private var kanal: Elternkanal? = null) : ElternkanalAblage {
    @Synchronized override fun lade() = kanal
    @Synchronized override fun speichere(kanal: Elternkanal) { this.kanal = kanal }
    @Synchronized override fun loesche() { kanal = null }
}

/** Gespeicherte Form (nicht der Einrichtungscode): genau die vier Felder. */
fun Elternkanal.alsJson(): String = Json.encodeToString(Elternkanal.serializer(), this)

fun elternkanalAusJson(text: String): Elternkanal? =
    runCatching { Json.decodeFromString(Elternkanal.serializer(), text) }.getOrNull()

class UngueltigerCode(grund: String) : IllegalArgumentException("Der Einrichtungscode passt nicht: $grund")
