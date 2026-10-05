// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.input

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RadsucheTest {

    private val suche = Radsuche(
        listOf(
            listOf("Big Buck Bunny", "Blender"),
            listOf("Sintel", "Blender"),
            listOf("Mondlandung", "NASA"),
            listOf("Märchen für Kinder"),
            listOf("Straße der Mäuse"),
        )
    )

    private fun Radsuche.buchstaben(eingabe: String) = angebot(eingabe).zeichen.map { it.wert }.joinToString("")

    @Test fun `leer bietet jeden Wortanfang an, sonst nichts`() {
        // big buck bunny blender sintel mondlandung nasa märchen für kinder straße der mäuse
        assertThat(suche.buchstaben("")).isEqualTo("bdfkmns")
        assertThat(suche.angebot("").luecke).isFalse()
        assertThat(suche.treffer("")).isEmpty()
    }

    @Test fun `nur Buchstaben, mit denen ein Name weitergeht`() {
        assertThat(suche.buchstaben("b")).isEqualTo("ilu")       // big, blender, buck/bunny
        assertThat(suche.buchstaben("bu")).isEqualTo("cn")
        assertThat(suche.buchstaben("bunn")).isEqualTo("y")
        assertThat(suche.buchstaben("bunny")).isEmpty()
        assertThat(suche.treffer("bunny")).containsExactly(0)
    }

    @Test fun `Wortanfang zaehlt, die Wortmitte nicht`() {
        assertThat(suche.treffer("bu")).containsExactly(0)
        assertThat(suche.treffer("uck")).isEmpty()
        assertThat(suche.buchstaben("u")).isEmpty()
    }

    @Test fun `der Kanalname sucht mit`() {
        assertThat(suche.treffer("bl")).containsExactly(0, 1).inOrder()
        assertThat(suche.treffer("nas")).containsExactly(2)
    }

    @Test fun `Luecke nur, wenn das Wort fertig ist und ein weiteres folgt`() {
        assertThat(suche.angebot("bi").luecke).isFalse()
        assertThat(suche.angebot("big").luecke).isTrue()
        assertThat(suche.buchstaben("big")).isEmpty()
        assertThat(suche.buchstaben("big ")).isEqualTo("b")
        assertThat(suche.treffer("big bu")).containsExactly(0)
        // „bunny" ist das letzte Wort: dahinter kommt nichts mehr.
        assertThat(suche.angebot("bunny").luecke).isFalse()
        assertThat(suche.angebot("big ").luecke).isFalse()
    }

    @Test fun `Umlaute zaehlen als Grundbuchstabe, die Schreibweise bleibt erhalten`() {
        assertThat(suche.buchstaben("m")).isEqualTo("ao")         // märchen, mäuse, mondlandung
        val a = suche.angebot("m").zeichen.first { it.wert == 'a' }
        assertThat(a.schreibweisen).isEqualTo("ä")
        assertThat(a.anzeige).isEqualTo('ä')
        assertThat(a.beschriftung).isEqualTo("Ä")
        assertThat(suche.treffer("mar")).containsExactly(3)
        assertThat(suche.treffer("Mär")).containsExactly(3)
        assertThat(suche.treffer("fur")).containsExactly(3)
    }

    @Test fun `die Schreibweise kommt aus den Treffern, wo sie eindeutig ist`() {
        assertThat(suche.schreibweise("ma", "ma")).isEqualTo("mä")     // Märchen, Mäuse
        assertThat(suche.schreibweise("big bu", "big bu")).isEqualTo("big bu")
        val gemischt = Radsuche(listOf(listOf("Mama"), listOf("Märchen")))
        assertThat(gemischt.schreibweise("ma", "ma")).isEqualTo("ma")
        assertThat(gemischt.schreibweise("mar", "mar")).isEqualTo("mär")
        assertThat(gemischt.schreibweise("xy", "xy")).isEqualTo("xy")    // ohne Treffer: wie getippt
    }

    @Test fun `ss ist ein s, und die Anzeige zeigt das scharfe s`() {
        val s = suche.angebot("stra").zeichen.single()
        assertThat(s.wert).isEqualTo('s')
        assertThat(s.anzeige).isEqualTo('ß')
        assertThat(s.beschriftung).isEqualTo("ß")
        assertThat(suche.treffer("strase")).containsExactly(4)
    }

    @Test fun `gemischte Schreibweisen stehen beide auf dem Feld`() {
        val gemischt = Radsuche(listOf(listOf("Mama"), listOf("Märchen")))
        val a = gemischt.angebot("m").zeichen.single()
        assertThat(a.beschriftung).isEqualTo("A/Ä")
        assertThat(a.anzeige).isEqualTo('a')
        // Andere Akzente stehen nicht auf dem Feld.
        assertThat(Radsuche(listOf(listOf("Åsa"), listOf("Anna"))).angebot("").zeichen.single().beschriftung).isEqualTo("A")
        assertThat(Radsuche(listOf(listOf("Åsa"))).angebot("").zeichen.single().beschriftung).isEqualTo("Å")
    }

    @Test fun `Satzzeichen trennen Woerter, Ziffern kommen nach den Buchstaben`() {
        val s = Radsuche(listOf(listOf("Bibi-Geschichte"), listOf("1/2 Lovesong"), listOf("Folge 12: Ärger")))
        assertThat(s.treffer("gesch")).containsExactly(0)
        assertThat(s.buchstaben("")).isEqualTo("abfgl12")
        assertThat(s.treffer("2")).containsExactly(1)
        assertThat(s.treffer("folge 12 ar")).containsExactly(2)
    }

    @Test fun `getippte Eingabe geht durch dieselbe Umrechnung`() {
        assertThat(Radsuche.eingabe("Big  Bu")).isEqualTo("big bu")
        assertThat(Radsuche.eingabe("Mä ")).isEqualTo("ma ")
        assertThat(Radsuche.eingabe(" -x")).isEqualTo("x")
    }

    @Test fun `Radtasten - Buchstaben, Luecke, Loeschen`() {
        assertThat(Radtasten.fuer(suche.angebot(""), "")).doesNotContain(RadTaste.Loeschen)
        val tasten = Radtasten.fuer(suche.angebot("big"), "big")
        assertThat(tasten).containsExactly(RadTaste.Luecke, RadTaste.Loeschen).inOrder()
    }

    @Test fun `Radtasten - der Fokus bleibt in der Naehe`() {
        val vorher = Radtasten.fuer(suche.angebot(""), "")
        val b = vorher.first { (it as? RadTaste.Buchstabe)?.zeichen?.wert == 'b' }
        val nachher = Radtasten.fuer(suche.angebot("b"), "b")      // i l u ⌫
        assertThat(Radtasten.fokusNach(b, nachher)).isEqualTo(0)  // i ist der erste nach b
        val u = nachher[2]
        val danach = Radtasten.fuer(suche.angebot("bu"), "bu")     // c n ⌫
        assertThat(Radtasten.fokusNach(u, danach)).isEqualTo(1)   // hinter u nichts mehr: n
        assertThat(Radtasten.fokusNach(RadTaste.Loeschen, danach)).isEqualTo(2)
        assertThat(Radtasten.fokusNach(null, emptyList())).isEqualTo(0)
    }
}
