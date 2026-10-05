// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube

import android.app.Application

/**
 * Einstiegspunkt der Anwendung. Haelt den [AppContainer], der die Bausteine von Hand
 * verdrahtet – bei dieser Groesse traegt eine Annotationsbibliothek ihren Aufwand nicht.
 */
class SideTubeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
