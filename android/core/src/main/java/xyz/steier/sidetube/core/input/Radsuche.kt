// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.input

import java.text.Normalizer

/**
 * Suchen mit dem Rad: Buchstabe für Buchstabe, aber nur die, mit denen es weitergeht.
 *
 * Das SidePhone SP-01 hat weder Buchstaben- noch Zifferntasten, nur den Ring. Das Rad bietet
 * deshalb die Buchstaben zur Auswahl an – und zwar nur solche, mit denen ein Name aus [eintraege]
 * weitergeht. Ein Kind kann sich so nicht vertippen, und es sieht nach jedem Schritt, was bleibt.
 *
 * Regeln (gleich in SidePlay, `kern/Radsuche.kt`; SideUI ADR 0018: Regeln teilen, keinen Code):
 * - Gesucht wird **am Wortanfang**: „bu" trifft „Big Buck Bunny", „uck" nicht.
 * - Eine **Lücke** verlangt, dass das Wort davor fertig ist und ein weiteres folgt: „big bu" trifft
 *   „Big Buck Bunny". Angeboten wird sie nur, wenn ein Name so weitergeht.
 * - **Umlaute zählen als Grundbuchstabe** (ä = a, ö = o, ü = u, é = e, ß = s), damit das Rad nicht
 *   doppelt so viele Felder bekommt. Welche Schreibweisen hinter einem Feld stehen, steht in
 *   [Zeichen.schreibweisen] – so kann die Anzeige „Mä" zeigen, wo es nur „Mä…" gibt.
 * - Alles, was kein Buchstabe und keine Ziffer ist, trennt Wörter.
 *
 * Ohne Android-Abhängigkeit, damit es im Kern prüfbar bleibt.
 *
 * @param eintraege je Eintrag die Texte, in denen gesucht wird (Titel, Kanalname …).
 */
class Radsuche(eintraege: List<List<String>>) {

    /** Ein Feld am Rad: der Grundbuchstabe und die Schreibweisen, die er hier vertritt. */
    data class Zeichen(val wert: Char, val schreibweisen: String) {
        /**
         * Die Aufschrift: „A", „Ä" oder „A/Ä". Neben dem Grundbuchstaben stehen nur
         * die deutschen Umlaute und ß — „A/Å/Á" waere fuer ein Kind nur Rauschen.
         */
        val beschriftung: String
            get() {
                val formen = if (schreibweisen.length == 1) schreibweisen.toList()
                else (listOf(wert) + schreibweisen.filter { it in "äöüß" }.toList()).distinct()
                return formen.joinToString("/") { if (it == 'ß') "ß" else it.uppercaseChar().toString() }
            }

        /** Was in der Eingabe erscheint: die eine Schreibweise, sonst der Grundbuchstabe. */
        val anzeige: Char get() = if (schreibweisen.length == 1) schreibweisen[0] else wert
    }

    /** Was das Rad nach [eingabe] anbieten darf. */
    data class Angebot(val zeichen: List<Zeichen>, val luecke: Boolean)

    private val texte: List<List<Normalform>> = eintraege.map { it.map(::normalform) }

    /** Die Einträge (als Index), die zur Eingabe passen; leer bei leerer Eingabe. */
    fun treffer(eingabe: String): List<Int> {
        val suche = eingabe(eingabe)
        if (suche.isBlank()) return emptyList()
        return texte.indices.filter { i -> texte[i].any { anfaenge(it.text, suche).any() } }
    }

    /** Welche Zeichen und ob eine Lücke nach [eingabe] noch zu einem Treffer führen. */
    fun angebot(eingabe: String): Angebot {
        val suche = eingabe(eingabe)
        val schreibweisen = sortedMapOf<Char, MutableSet<Char>>(REIHENFOLGE)
        var luecke = false
        for (form in texte.asSequence().flatten()) {
            for (start in anfaenge(form.text, suche)) {
                val stelle = start + suche.length
                if (stelle >= form.text.length) continue
                val zeichen = form.text[stelle]
                if (zeichen == ' ') luecke = true
                else schreibweisen.getOrPut(zeichen) { sortedSetOf() } += form.original[stelle]
            }
        }
        // Eine Lücke am Anfang oder doppelt hintereinander trennt nichts.
        if (suche.isEmpty() || suche.endsWith(' ')) luecke = false
        val zeichen = schreibweisen.map { (wert, formen) -> Zeichen(wert, formen.joinToString("")) }
        return Angebot(zeichen, luecke)
    }

    /**
     * Wie die Eingabe in den passenden Namen geschrieben ist: an jeder Stelle, an der alle
     * Treffer dasselbe Zeichen haben, dieses (aus „ma" wird „mä", sobald nur noch Märchen
     * passen); sonst das getippte Zeichen aus [getippt].
     */
    fun schreibweise(eingabe: String, getippt: String): String {
        val suche = eingabe(eingabe)
        val formen = List(suche.length) { mutableSetOf<Char>() }
        for (form in texte.asSequence().flatten()) {
            for (start in anfaenge(form.text, suche)) {
                for (i in suche.indices) formen[i] += form.original[start + i]
            }
        }
        return buildString {
            for (i in suche.indices) append(formen[i].singleOrNull() ?: getippt.getOrElse(i) { suche[i] })
        }
    }

    /** Wo in [text] ein Wort beginnt, das mit [suche] anfängt. */
    private fun anfaenge(text: String, suche: String): Sequence<Int> =
        text.indices.asSequence()
            .filter { it == 0 || text[it - 1] == ' ' }
            .filter { text.startsWith(suche, it) }

    /** Ein Text in Suchform und daneben, Zeichen für Zeichen, wie er geschrieben war. */
    private class Normalform(val text: String, val original: String)

    companion object {
        /**
         * Der Grundbuchstabe eines Zeichens, klein; `null` für alles, was Wörter trennt.
         * Buchstaben ohne lateinische Grundform (ø, æ, Kyrillisch …) bleiben, wie sie sind.
         */
        fun grundzeichen(zeichen: Char): Char? {
            val klein = zeichen.lowercaseChar()
            if (klein == 'ß') return 's'
            if (klein in 'a'..'z' || klein in '0'..'9') return klein
            if (!klein.isLetterOrDigit()) return null
            val basis = Normalizer.normalize(klein.toString(), Normalizer.Form.NFD).first()
            return if (basis in 'a'..'z') basis else klein
        }

        /**
         * Eine Eingabe in Suchform – getippte Zeichen von einer Tastatur gehen durch dieselbe
         * Umrechnung wie die Namen. Eine Lücke am Ende bleibt stehen: Sie ist Teil der Eingabe.
         */
        fun eingabe(text: String): String = buildString {
            for (zeichen in text) {
                val grund = grundzeichen(zeichen) ?: ' '
                if (grund == ' ' && (isEmpty() || last() == ' ')) continue
                append(grund)
            }
        }

        private fun normalform(text: String): Normalform {
            val suche = StringBuilder()
            val original = StringBuilder()
            for (zeichen in text) {
                val grund = grundzeichen(zeichen)
                if (grund == null) {
                    if (suche.isNotEmpty() && suche.last() != ' ') { suche.append(' '); original.append(' ') }
                } else {
                    suche.append(grund); original.append(zeichen.lowercaseChar())
                }
            }
            if (suche.endsWith(' ')) { suche.setLength(suche.length - 1); original.setLength(original.length - 1) }
            return Normalform(suche.toString(), original.toString())
        }

        /** Erst die Buchstaben a–z, dann andere Buchstaben, zuletzt die Ziffern. */
        private val REIHENFOLGE = compareBy<Char>({ if (it.isDigit()) 2 else if (it in 'a'..'z') 0 else 1 }, { it })
    }
}
