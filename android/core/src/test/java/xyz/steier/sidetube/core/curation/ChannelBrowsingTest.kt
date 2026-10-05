// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.curation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity

class ChannelBrowsingTest {
    private val profile = KidProfileEntity(id = "profile", name = "Example", ageBand = "kids")
    private val source = CuratedSourceEntity(channelId = "channel", title = "Source", trust = "trustedChildSource")
    private val candidate = WhitelistItemEntity(id = "video", profileId = profile.id,
        type = "VIDEO", contentId = "abcdefghijk", title = "Example", sourceChannelId = source.channelId)

    @Test fun `cached channel results cannot override rejected or pending decisions`() {
        for (status in listOf("rejected", "reviewRequired")) {
            assertThat(ContentPolicy.canBrowseCandidate(candidate, profile, source,
                candidate.copy(approvalStatus = status), RiskAssessment())).isFalse()
        }
    }

    @Test fun `unknown blocked adult and risky channel results remain hidden`() {
        for (trust in listOf("blocked", "parentOnly", "perVideoReview")) {
            assertThat(ContentPolicy.canBrowseCandidate(candidate, profile, source.copy(trust = trust),
                null, RiskAssessment())).isFalse()
        }
        assertThat(ContentPolicy.canBrowseCandidate(candidate, profile, source.copy(defaultAgeMin = 18),
            null, RiskAssessment())).isFalse()
        assertThat(ContentPolicy.canBrowseCandidate(candidate, profile, source,
            null, RiskAssessment(hardBlockTerms = listOf("blocked")))).isFalse()
    }

    @Test fun `trusted harmless browsing is transient and does not approve the stored candidate`() {
        assertThat(ContentPolicy.canBrowseCandidate(candidate, profile, source, null, RiskAssessment())).isTrue()
        assertThat(candidate.approvalStatus).isEqualTo("reviewRequired")
    }
}
