// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import xyz.steier.sidetube.core.elternkanal.Elternkanal
import xyz.steier.sidetube.core.elternkanal.ElternkanalAblage
import xyz.steier.sidetube.core.elternkanal.alsJson
import xyz.steier.sidetube.core.elternkanal.elternkanalAusJson

/**
 * Elternkanal (ADR 0005) verschluesselt wie die PIN, aber in einer eigenen Datei: Das Zuruecksetzen
 * der PIN ([PinStore.clear]) soll die Benachrichtigung nicht still abschalten.
 */
class ElternkanalSpeicher(context: Context) : ElternkanalAblage {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "sidetube-elternkanal",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun lade(): Elternkanal? = prefs.getString(KEY, null)?.let(::elternkanalAusJson)

    override fun speichere(kanal: Elternkanal) { prefs.edit().putString(KEY, kanal.alsJson()).apply() }

    override fun loesche() { prefs.edit().remove(KEY).apply() }

    private companion object {
        const val KEY = "kanal"
    }
}
