// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.player

/** Der Cookie-Bestand des Geraets, so weit ihn der Player braucht. */
interface CookieStore {
    fun setAcceptCookie(accept: Boolean)
    fun setAcceptThirdPartyCookies(accept: Boolean)
    fun removeAll()
    fun flush()
}

/**
 * Begrenzt den Cookie-Bestand der Player-WebView auf eine Sitzung.
 *
 * iOS spielt in einem nichtpersistenten WebKit-Datenspeicher: Mit dem Player verschwinden auch
 * dessen Cookies. Android kennt dazu kein Gegenstueck - der `CookieManager` gilt prozessweit und
 * schreibt auf die Platte. Gleichwertig wird das erst, wenn der Bestand zu Beginn *und* zum Ende
 * jeder Sitzung selbst geleert wird. Zu Beginn deshalb, weil eine abgebrochene Vorgaengersitzung
 * (Renderer-Tod, erzwungenes Beenden) ihr Ende nie erreicht hat und ihre Cookies sonst ueberdauern.
 *
 * Rein und geraeteunabhaengig, damit die Reihenfolge ohne Robolectric pruefbar ist; `PlayerBridge`
 * verdrahtet sie mit dem echten `CookieManager`.
 */
object PlayerCookiePolicy {
    /**
     * Drittanbieter-Cookies sind aus.
     *
     * Die Beobachtung, die sie einst erzwang ("ohne sie laedt der Player, spielt aber nicht"),
     * stammte aus einer Einbettung ueber `www.youtube.com`. Mit `www.youtube-nocookie.com` wurde am
     * 08.10.2026 am Emulator SP-01 (API 31, aktuelle WebView) gemessen: Wiedergabe startet und
     * laeuft ohne Drittanbieter-Cookies. Auf einem Geraet mit alter WebView-Fassung steht die
     * Messung noch aus (docs/release/android-device-verification.md).
     */
    const val ACCEPT_THIRD_PARTY = false

    /** Leeren, bevor ueberhaupt etwas geschrieben werden darf. */
    fun beginSession(store: CookieStore) {
        store.removeAll()
        store.setAcceptCookie(true)
        store.setAcceptThirdPartyCookies(ACCEPT_THIRD_PARTY)
    }

    /**
     * Erst sperren, dann leeren, dann den leeren Stand festschreiben. Die Reihenfolge traegt:
     * Wuerde erst geleert und danach gesperrt, koennte die noch laufende Seite in der Luecke
     * dazwischen erneut setzen; ohne `flush` ueberlebte der alte Stand auf der Platte einen
     * Prozessabbruch.
     */
    fun endSession(store: CookieStore) {
        store.setAcceptCookie(false)
        store.removeAll()
        store.flush()
    }
}
