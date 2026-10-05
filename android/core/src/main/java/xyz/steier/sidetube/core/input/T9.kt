// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.input

/**
 * Suchen mit Ziffern – 2 = abc, 3 = def, 4 = ghi …
 *
 * Das SidePhone SP-01 hat keine Buchstabentasten, nur 0–9, * und #. Ein Kind tippt fuer
 * „Bunny" also 2-8-6-6-9, und gefunden wird jedes Wort, dessen Anfang so getippt wird.
 * Rechtschreibung braucht es dafuer nicht, nur den Anfang eines Wortes.
 *
 * Belegung wie in SidePlay (`SidePlayKern/Suche/T9.swift`), damit sich beide Apps gleich
 * anfuehlen. Unterschied bei den Umlauten: Hier gilt die Telefonbelegung (ä → 2, ö → 6,
 * ü → 8, ß → 7) – so steht es auf den Tasten. Die aufgeloeste Schreibweise (ä → ae) wie in
 * SidePlay passt zusaetzlich, „Bär" ist also mit 2-2-7 und mit 2-2-3-7 zu finden.
 *
 * Die **0 ist die Wortgrenze**: 2-4-4-0-2-8 heisst „big bu…" und verlangt zwei aufeinander
 * folgende Wortanfaenge.
 *
 * Bewusst ohne Android-Abhaengigkeit, damit es im Kern pruefbar bleibt.
 */
object T9 {

    /** Die Buchstaben auf einer Zifferntaste, fuer die Anzeige; `0` ist die Luecke. */
    fun buchstaben(ziffer: Char): String = GRUPPEN[ziffer].orEmpty()

    /** Besteht die Eingabe nur aus Ziffern? Dann ist sie eine T9-Folge, kein Text. */
    fun istZiffernfolge(eingabe: String): Boolean =
        eingabe.isNotEmpty() && eingabe.all { it in '0'..'9' }

    /** Hat die Folge ausser Wortgrenzen ueberhaupt etwas, wonach gesucht werden kann? */
    fun hatInhalt(eingabe: String): Boolean = eingabe.any { it in '1'..'9' }

    /**
     * Uebersetzt ein Wort in seine Ziffernfolge (Telefonbelegung, ä → 2).
     * Zeichen ohne Taste fallen weg; Ziffern im Text stehen fuer sich selbst.
     */
    fun ziffern(text: String): String = buildString {
        for (zeichen in text.lowercase()) TAFEL[zeichen]?.let(::append)
    }

    /**
     * Passt die Ziffernfolge zum Namen?
     *
     * Verglichen wird **wortweise am Anfang** – wer 2-8-6-6-9 tippt, meint „Bunny", auch wenn
     * es das dritte Wort im Titel ist. Wie in SidePlay passt ausserdem der Anfang des ganzen
     * Namens ohne Luecken (2-4-4-2 trifft „Big Buck").
     */
    fun passt(eingabe: String, name: String): Boolean {
        val teile = eingabe.split('0').filter { it.isNotEmpty() }
        if (teile.isEmpty()) return eingabe.isEmpty()
        val worte = worte(name).map(::varianten)
        if (worte.isEmpty()) return false

        for (start in worte.indices) {
            if (start + teile.size > worte.size) break
            val trifft = teile.indices.all { k -> worte[start + k].any { it.startsWith(teile[k]) } }
            if (trifft) return true
        }
        if ('0' !in eingabe) {
            val ganz = listOf(worte.joinToString("") { it[0] }, worte.joinToString("") { it[1] })
            if (ganz.any { it.startsWith(eingabe) }) return true
        }
        return false
    }

    /** Die beiden Lesarten eines Wortes: Telefonbelegung und aufgeloeste Umlaute. */
    private fun varianten(wort: String): List<String> =
        listOf(ziffern(wort), ziffern(aufgeloest(wort)))

    private fun aufgeloest(wort: String): String =
        wort.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")

    /** Alles, was kein Buchstabe und keine Ziffer ist, trennt Woerter. */
    private fun worte(name: String): List<String> =
        name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private val GRUPPEN = mapOf(
        '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
        '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz",
    )

    private val TAFEL: Map<Char, Char> = buildMap {
        for ((ziffer, buchstaben) in GRUPPEN) for (b in buchstaben) put(b, ziffer)
        put('ä', '2'); put('ö', '6'); put('ü', '8'); put('ß', '7')
        for (z in '0'..'9') put(z, z)
    }
}
