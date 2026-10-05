// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import xyz.steier.sidetube.core.db.WishDao
import xyz.steier.sidetube.core.db.WishEntity

/** Wuensche im Speicher, mit denselben Abfragen wie Room ([WishDao]); fuer ViewModel-Tests. */
internal class FakeWishDao : WishDao {
    val rows = MutableStateFlow<List<WishEntity>>(emptyList())

    override fun observeByProfile(profileId: String) =
        rows.map { all -> all.filter { it.profileId == profileId }.sortedByDescending { it.createdAt } }
    override fun observeOpen(profileId: String) =
        rows.map { all -> all.filter { it.profileId == profileId && it.status in OFFEN }.sortedBy { it.createdAt } }
    override suspend fun countOffen() = rows.value.count { it.status == "offen" }
    override suspend fun countBetween(profileId: String, from: Long, until: Long) =
        rows.value.count { it.profileId == profileId && it.createdAt >= from && it.createdAt < until }
    override suspend fun byKey(profileId: String, dedupeKey: String) =
        rows.value.filter { it.profileId == profileId && it.dedupeKey == dedupeKey }.sortedByDescending { it.createdAt }
    override suspend fun byId(id: String) = rows.value.firstOrNull { it.id == id }
    override suspend fun openFor(profileId: String, contentId: String) = rows.value.filter {
        it.profileId == profileId && it.status in OFFEN &&
            (it.resultContentId == contentId || (it.kind == "neueFolge" && it.videoId == contentId))
    }
    /** Haelt das Speichern an, bis der Test es freigibt (langsame Datenbank). */
    var insertGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun insert(wish: WishEntity) {
        insertGate?.await()
        check(rows.value.none { it.id == wish.id }) { "doppelte Kennung" }
        rows.value += wish
    }
    override suspend fun update(wish: WishEntity) { rows.value = rows.value.map { if (it.id == wish.id) wish else it } }

    private companion object { val OFFEN = setOf("offen", "besprechen") }
}
