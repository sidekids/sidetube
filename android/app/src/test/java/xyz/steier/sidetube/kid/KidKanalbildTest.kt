// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import xyz.steier.sidetube.core.db.WhitelistItemEntity

/** Kanaele aus Startpaketen kommen ohne Bild; es wird einmal nachgeholt, nicht gespeichert. */
@OptIn(ExperimentalCoroutinesApi::class)
class KidKanalbildTest {

    private fun kanal(id: String) = WhitelistItemEntity(
        id, "p", "CHANNEL", contentId = id, title = "Kanal $id", approvalStatus = "approved", provider = "youtube"
    )

    @Test fun `ein Kanal ohne Bild bekommt es nachgeholt, ohne die Freigaben anzufassen`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = KidFixture()
        f.items.value = listOf(kanal("UCx"))
        val gefragt = mutableListOf<String>()
        val vm = f.model(kanalbild = { id -> gefragt += id; "https://yt3.ggpht.com/$id" })
        try {
            runCurrent()
            val zeile = vm.state.value.rows.single { it.isChannel }
            assertThat(zeile.thumbnailUrl).isEqualTo("https://yt3.ggpht.com/UCx")
            assertThat(gefragt).containsExactly("UCx")
            // Keine Datenbank-Aenderung: Die haette den Player geschlossen.
            assertThat(f.items.value.single().thumbnailUrl).isEmpty()
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun `NASA und ESA nehmen das kuratierte Ersatzbild ohne Netz`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = KidFixture()
        f.items.value = listOf(kanal("UCIBaDdAbGlFDeS33shmlD0A"))
        val vm = f.model(kanalbild = { error("kein Netz erwartet") })
        try {
            runCurrent()
            assertThat(vm.state.value.rows.single { it.isChannel }.thumbnailUrl)
                .isEqualTo("https://i.ytimg.com/vi/PqJpgizriFM/hqdefault.jpg")
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun `ein fehlgeschlagener Abruf wird je App-Start nicht wiederholt`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = KidFixture()
        f.items.value = listOf(kanal("UCy"))
        val gefragt = mutableListOf<String>()
        val vm = f.model(kanalbild = { id -> gefragt += id; error("offline") })
        try {
            runCurrent()
            // Neue Emission der Freigaben (etwa ein weiteres Video): kein zweiter Abruf.
            f.items.value = f.items.value + WhitelistItemEntity("v", "p", "VIDEO", contentId = "v", title = "V", approvalStatus = "approved")
            runCurrent()
            assertThat(gefragt).containsExactly("UCy")
            assertThat(vm.state.value.rows.single { it.isChannel }.thumbnailUrl).isNull()
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
