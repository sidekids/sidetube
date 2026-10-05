// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerCookiePolicyTest {
    private class Recording : CookieStore {
        val calls = mutableListOf<String>()
        override fun setAcceptCookie(accept: Boolean) { calls += "accept=$accept" }
        override fun setAcceptThirdPartyCookies(accept: Boolean) { calls += "thirdParty=$accept" }
        override fun removeAll() { calls += "removeAll" }
        override fun flush() { calls += "flush" }
    }

    @Test
    fun `Sitzungsbeginn leert erst den Rest einer abgebrochenen Vorgaengersitzung`() {
        val store = Recording()
        PlayerCookiePolicy.beginSession(store)
        assertThat(store.calls).containsExactly(
            "removeAll", "accept=true", "thirdParty=${PlayerCookiePolicy.ACCEPT_THIRD_PARTY}"
        ).inOrder()
    }

    @Test
    fun `Sitzungsende sperrt vor dem Leeren und schreibt den leeren Stand fest`() {
        val store = Recording()
        PlayerCookiePolicy.endSession(store)
        assertThat(store.calls).containsExactly("accept=false", "removeAll", "flush").inOrder()
    }

    @Test
    fun `eine vollstaendige Sitzung hinterlaesst keinen Bestand`() {
        val store = Recording()
        PlayerCookiePolicy.beginSession(store)
        PlayerCookiePolicy.endSession(store)
        assertThat(store.calls.last()).isEqualTo("flush")
        assertThat(store.calls.indexOf("accept=false")).isLessThan(store.calls.lastIndexOf("removeAll"))
    }
}
