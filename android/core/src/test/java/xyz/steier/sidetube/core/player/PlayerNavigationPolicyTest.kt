// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerNavigationPolicyTest {
    private val appOrigin = "https://xyz.steier.sidetube"

    @Test
    fun `Hauptdokument darf zur eigenen Herkunft und zu about-blank`() {
        assertThat(PlayerNavigationPolicy.allowMainFrame(appOrigin, appOrigin)).isTrue()
        assertThat(PlayerNavigationPolicy.allowMainFrame("about:blank", appOrigin)).isTrue()
    }

    @Test
    fun `Hauptdokument darf nicht zu YouTube oder einer fremden Seite`() {
        assertThat(PlayerNavigationPolicy.allowMainFrame("https://www.youtube.com/watch?v=abc", appOrigin)).isFalse()
        assertThat(PlayerNavigationPolicy.allowMainFrame("https://phishing.example/", appOrigin)).isFalse()
    }

    @Test
    fun `Hauptdokument darf nicht ueber Nutzerinfo oder Port abweichen`() {
        assertThat(PlayerNavigationPolicy.allowMainFrame("https://user@xyz.steier.sidetube", appOrigin)).isFalse()
        assertThat(PlayerNavigationPolicy.allowMainFrame("https://xyz.steier.sidetube:8443", appOrigin)).isFalse()
    }

    @Test
    fun `Unterrahmen darf das eigentliche Einbettungsdokument laden`() {
        assertThat(PlayerNavigationPolicy.allowSubFrame("https://www.youtube.com/embed/dQw4w9WgXcQ?autoplay=1")).isTrue()
        assertThat(PlayerNavigationPolicy.allowSubFrame("https://youtube-nocookie.com/embed/dQw4w9WgXcQ")).isTrue()
        assertThat(PlayerNavigationPolicy.allowSubFrame("about:blank")).isTrue()
    }

    @Test
    fun `Unterrahmen darf nicht zur vollen YouTube-Seite wechseln - Host allein reicht nicht`() {
        assertThat(PlayerNavigationPolicy.allowSubFrame("https://www.youtube.com/watch?v=dQw4w9WgXcQ")).isFalse()
        assertThat(PlayerNavigationPolicy.allowSubFrame("https://www.youtube.com/")).isFalse()
        assertThat(PlayerNavigationPolicy.allowSubFrame("https://www.youtube.com/results?search_query=x")).isFalse()
        assertThat(PlayerNavigationPolicy.allowSubFrame("https://accounts.google.com/ServiceLogin")).isFalse()
    }

    @Test
    fun `Unterrahmen darf nicht zu einer fremden Domaene, auch nicht mit YouTube im Pfad`() {
        assertThat(PlayerNavigationPolicy.allowSubFrame("https://phishing.example/embed/dQw4w9WgXcQ")).isFalse()
        assertThat(PlayerNavigationPolicy.allowSubFrame("https://www.youtube.com.evil.example/embed/x")).isFalse()
    }

    @Test
    fun `Grossschreibung im Host aendert nichts an der Entscheidung`() {
        assertThat(PlayerNavigationPolicy.allowSubFrame("https://WWW.YOUTUBE.COM/embed/dQw4w9WgXcQ")).isTrue()
    }

    @Test
    fun `Ungueltige Adressen werden abgelehnt, nicht als Absturz behandelt`() {
        assertThat(PlayerNavigationPolicy.allowMainFrame("not a url", appOrigin)).isFalse()
        assertThat(PlayerNavigationPolicy.allowSubFrame("not a url")).isFalse()
    }
}
