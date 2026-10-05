// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.steier.sidetube.core.repo.WhitelistRepository
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class AuthorizationFlowTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val profile = KidProfileEntity("p", "Synthetic", bedtimeEnabled = false)
    private fun item(id: String, channel: String? = null) = WhitelistItemEntity(
        id, profile.id, "VIDEO", contentId = id, title = "Synthetic $id",
        approvalStatus = "approved", sourceChannelId = channel
    )

    private suspend fun Channel<List<WhitelistItemEntity>>.awaitIds(vararg ids: String): List<WhitelistItemEntity> =
        withTimeout(10_000) {
            receiveAsFlow().first { rows -> rows.map { it.contentId }.toSet() == ids.toSet() }
        }

    @Test fun `live Room observer removes revoked and deleted queue items`() = runBlocking {
        val name = "authorization-${UUID.randomUUID()}.db"
        val db = SideTubeDatabase.open(context, name)
        var collector: Job? = null
        try {
            db.kidProfiles().insert(profile)
            val a = item("a"); val b = item("b")
            db.whitelist().insert(a); db.whitelist().insert(b)
            val snapshots = Channel<List<WhitelistItemEntity>>(Channel.UNLIMITED)
            val repository = WhitelistRepository(db.whitelist(), db.sources())
            collector = launch(Dispatchers.Default) { repository.observeVisible(profile).collect { snapshots.send(it) } }
            snapshots.awaitIds("a", "b")
            db.whitelist().update(b.copy(approvalStatus = "rejected"))
            assertThat(snapshots.awaitIds("a").single().id).isEqualTo(a.id)
            assertThat(db.whitelist().find(profile.id, "b")?.approvalStatus).isEqualTo("rejected")
            db.whitelist().delete(a)
            assertThat(snapshots.awaitIds()).isEmpty()
        } finally {
            collector?.cancelAndJoin()
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun `live Room source changes remove and restore only explicitly approved content`() = runBlocking {
        val name = "authorization-source-${UUID.randomUUID()}.db"
        val db = SideTubeDatabase.open(context, name)
        var collector: Job? = null
        try {
            db.kidProfiles().insert(profile)
            val source = CuratedSourceEntity("channel", title = "Synthetic", trust = "perVideoReview")
            db.sources().insertMissing(listOf(source))
            db.whitelist().insert(item("approved", source.channelId))
            db.whitelist().insert(item("rejected", source.channelId).copy(approvalStatus = "rejected"))
            val snapshots = Channel<List<WhitelistItemEntity>>(Channel.UNLIMITED)
            val repository = WhitelistRepository(db.whitelist(), db.sources())
            collector = launch(Dispatchers.Default) { repository.observeVisible(profile).collect { snapshots.send(it) } }
            snapshots.awaitIds("approved")
            db.sources().update(source.copy(trust = "blocked"))
            assertThat(snapshots.awaitIds()).isEmpty()
            db.sources().update(source)
            assertThat(snapshots.awaitIds("approved").single().approvalStatus).isEqualTo("approved")
            assertThat(db.whitelist().find(profile.id, "rejected")?.approvalStatus).isEqualTo("rejected")
        } finally {
            collector?.cancelAndJoin()
            db.close()
            context.deleteDatabase(name)
        }
    }
}
