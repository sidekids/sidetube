// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube

import android.content.Context

/** Abstraktion, damit `KidViewModel` sie in Tests ohne echten `Context`/Robolectric ersetzen kann. */
interface ProfilePreferenceStore {
    var lastProfileId: String?
}

/** Merkt sich das zuletzt gewaehlte Kinderprofil - keine Geheimnisse, deshalb kein PinStore. */
class ProfilePreferences(context: Context) : ProfilePreferenceStore {
    private val prefs = context.applicationContext.getSharedPreferences("sidetube", Context.MODE_PRIVATE)

    override var lastProfileId: String?
        get() = prefs.getString(KEY_LAST_PROFILE, null)
        set(value) { prefs.edit().putString(KEY_LAST_PROFILE, value).apply() }

    private companion object {
        const val KEY_LAST_PROFILE = "kid.lastProfileId"
    }
}
