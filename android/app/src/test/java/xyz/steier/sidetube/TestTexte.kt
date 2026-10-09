// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Liest die deutschen `strings.xml` direkt aus dem Quellbaum, damit die ViewModel-Tests ohne
 * Robolectric dieselben Saetze sehen wie die App. Die Kennungen kommen per Reflexion aus `R.string`
 * und `R.plurals`; der Unit-Test-Klassenpfad fuehrt diese Klassen mit.
 */
object TestTexte : Texte {
    private val datei: File = sequenceOf("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml")
        .map(::File).first { it.exists() }
    private val strings = mutableMapOf<String, String>()
    private val plurals = mutableMapOf<String, Map<String, String>>()
    private val stringNamen: Map<Int, String> = R.string::class.java.fields.associate { it.getInt(null) to it.name }
    private val pluralNamen: Map<Int, String> = R.plurals::class.java.fields.associate { it.getInt(null) to it.name }

    init {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(datei)
        val kinder = doc.documentElement.childNodes
        for (i in 0 until kinder.length) {
            val e = kinder.item(i) as? Element ?: continue
            when (e.tagName) {
                "string" -> strings[e.getAttribute("name")] = entschaerfen(e.textContent)
                "plurals" -> {
                    val items = e.getElementsByTagName("item")
                    plurals[e.getAttribute("name")] = (0 until items.length).map { items.item(it) as Element }
                        .associate { it.getAttribute("quantity") to entschaerfen(it.textContent) }
                }
            }
        }
    }

    /** Android-Ressourcen: `\'` und `\"` sind Fluchtsequenzen, Zeilenumbrueche im XML werden zu Leerzeichen gefaltet. */
    private fun entschaerfen(s: String): String {
        val roh = s.replace(Regex("\\s*\n\\s*"), " ")
        // In Anfuehrungszeichen bleiben Leerzeichen am Rand erhalten (wie bei aapt); sonst werden sie abgeschnitten.
        val kern = if (roh.trim().length >= 2 && roh.trim().startsWith("\"") && roh.trim().endsWith("\"")) roh.trim().drop(1).dropLast(1) else roh.trim()
        return kern.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
    }

    override fun get(id: Int, vararg args: Any): String {
        val name = stringNamen[id] ?: error("Unbekannte String-Kennung $id")
        val vorlage = strings[name] ?: error("Kein Text fuer R.string.$name in strings.xml")
        return if (args.isEmpty()) vorlage else String.format(vorlage, *args)
    }

    override fun plural(id: Int, menge: Int, vararg args: Any): String {
        val name = pluralNamen[id] ?: error("Unbekannte Plural-Kennung $id")
        val formen = plurals[name] ?: error("Kein Plural fuer R.plurals.$name in strings.xml")
        val vorlage = (if (menge == 1) formen["one"] else null) ?: formen["other"] ?: error("Plural $name ohne other")
        val a: Array<Any> = if (args.isEmpty()) arrayOf(menge) else arrayOf(*args)
        return String.format(vorlage, *a)
    }
}
