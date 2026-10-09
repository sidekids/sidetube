// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import xyz.steier.sidetube.PinResult
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.ReviewEventEntity
import xyz.steier.sidetube.core.model.AgeBand
import xyz.steier.sidetube.core.model.SourceTrust

/** Reine Logik des Elternbereichs: Profilmaske, PIN-Wechsel, Pruefdialog, Bezeichnungen. */
class ParentEditorLogicTest {

    private val profile = KidProfileEntity("p", "Mila")

    // --- Profilmaske ---

    @Test fun `limit steps by five and stays within 5 to 300`() {
        val d = ProfileDraft.from(profile).copy(limitEnabled = true, limitMinutes = 5)
        assertThat(d.stepLimit(-1).limitMinutes).isEqualTo(5)
        assertThat(d.stepLimit(1).limitMinutes).isEqualTo(10)
        assertThat(d.copy(limitMinutes = 300).stepLimit(1).limitMinutes).isEqualTo(300)
    }

    @Test fun `an off-grid stored value snaps to the next grid step`() {
        assertThat(ProfileDraft.step(62, 1, 5, ProfileDraft.LIMIT_RANGE)).isEqualTo(65)
        assertThat(ProfileDraft.step(62, -1, 5, ProfileDraft.LIMIT_RANGE)).isEqualTo(60)
        assertThat(ProfileDraft.step(60, -1, 5, ProfileDraft.LIMIT_RANGE)).isEqualTo(55)
    }

    @Test fun `bedtime bounds follow iOS`() {
        val d = ProfileDraft.from(profile)
        assertThat(d.copy(bedtimeStartMinutes = 23 * 60).stepBedtimeStart(1).bedtimeStartMinutes).isEqualTo(23 * 60)
        assertThat(d.copy(bedtimeStartMinutes = 17 * 60).stepBedtimeStart(-1).bedtimeStartMinutes).isEqualTo(17 * 60)
        assertThat(d.copy(bedtimeEndMinutes = 9 * 60).stepBedtimeEnd(1).bedtimeEndMinutes).isEqualTo(9 * 60)
        assertThat(d.copy(bedtimeEndMinutes = 6 * 60 + 30).stepBedtimeEnd(-1).bedtimeEndMinutes).isEqualTo(6 * 60 + 15)
        assertThat(d.copy(bedtimeWeekendOffsetMinutes = 120).stepWeekendOffset(1).bedtimeWeekendOffsetMinutes).isEqualTo(120)
        assertThat(d.copy(bedtimeWeekendOffsetMinutes = 0).stepWeekendOffset(-1).bedtimeWeekendOffsetMinutes).isEqualTo(0)
    }

    @Test fun `anime and manga only sticks for twelve and up with manga allowed`() {
        val d = ProfileDraft.from(profile).copy(allowMangaEntertainment = true, allowManga = true)
        assertThat(d.copy(ageBand = AgeBand.KIDS).applyTo(profile).allowMangaEntertainment).isFalse()
        assertThat(d.copy(ageBand = AgeBand.TWEEN, allowManga = false).applyTo(profile).allowMangaEntertainment).isFalse()
        assertThat(d.copy(ageBand = AgeBand.TWEEN).applyTo(profile).allowMangaEntertainment).isTrue()
    }

    @Test fun `clearing the exception in the editor removes it`() {
        val withSkip = profile.copy(bedtimeSkipUntil = 5L)
        assertThat(ProfileDraft.from(withSkip).clearBedtimeSkip().applyTo(withSkip).bedtimeSkipUntil).isNull()
        assertThat(ProfileDraft.from(withSkip).applyTo(withSkip).bedtimeSkipUntil).isEqualTo(5L)
    }

    @Test fun `round trip keeps an untouched profile unchanged`() {
        val p = profile.copy(dailyLimitMinutes = 45, ageBand = "early", allowNews = false)
        assertThat(ProfileDraft.from(p).applyTo(p)).isEqualTo(p)
    }

    // --- PIN aendern ---

    private class FakePins(var pin: String = "1234", var locked: Boolean = false) {
        fun verify(p: String): PinResult = when {
            locked -> PinResult.LockedOut(30)
            p == pin -> PinResult.Success
            else -> PinResult.Wrong(4)
        }
    }

    @Test fun `pin change in three steps stores the new pin`() {
        val pins = FakePins()
        val flow = PinChangeFlow(xyz.steier.sidetube.TestTexte, pins::verify) { pins.pin = it }
        assertThat(flow.title).isEqualTo("Aktuelle PIN")
        assertThat(flow.handle("1234")).isFalse()
        assertThat(flow.title).isEqualTo("Neue PIN")
        assertThat(flow.handle("5678")).isFalse()
        assertThat(flow.title).isEqualTo("Neue PIN wiederholen")
        assertThat(flow.handle("5678")).isTrue()
        assertThat(pins.pin).isEqualTo("5678")
    }

    @Test fun `mismatched repeat goes back to the new pin and stores nothing`() {
        val pins = FakePins()
        val flow = PinChangeFlow(xyz.steier.sidetube.TestTexte, pins::verify) { pins.pin = it }
        flow.handle("1234"); flow.handle("5678")
        assertThat(flow.handle("5679")).isFalse()
        assertThat(flow.step).isEqualTo(PinChangeFlow.Step.NEW)
        assertThat(flow.message).contains("stimmte nicht überein")
        assertThat(pins.pin).isEqualTo("1234")
    }

    @Test fun `wrong or locked current pin restarts and stores nothing`() {
        val pins = FakePins()
        val flow = PinChangeFlow(xyz.steier.sidetube.TestTexte, pins::verify) { pins.pin = it }
        flow.handle("0000"); flow.handle("5678")
        assertThat(flow.handle("5678")).isFalse()
        assertThat(flow.step).isEqualTo(PinChangeFlow.Step.OLD)
        assertThat(flow.message).isEqualTo("Aktuelle PIN falsch (noch 4 Versuche).")

        pins.locked = true
        flow.handle("1234"); flow.handle("5678")
        assertThat(flow.handle("5678")).isFalse()
        assertThat(flow.message).isEqualTo("Gesperrt für 30 s.")
        assertThat(pins.pin).isEqualTo("1234")
    }

    // --- Pruefdialog ---

    @Test fun `maximum age is optional and never below the effective minimum`() {
        assertThat(ReviewInput(6, false, 12, null, "").toApproval().ageMax).isNull()
        assertThat(ReviewInput(6, true, 10, null, "").toApproval().ageMax).isEqualTo(10)
        // Anime & Manga hebt das Mindestalter auf 12; ein Hoechstalter darunter waere ein leeres Fenster.
        val a = ReviewInput(6, true, 10, "animeManga", " ").toApproval()
        assertThat(a.ageMin).isEqualTo(12)
        assertThat(a.ageMax).isEqualTo(12)
        assertThat(a.parentNotes).isNull()
    }

    // --- Bezeichnungen ---

    @Test fun `trust levels are German like iOS, never raw ids`() {
        assertThat(xyz.steier.sidetube.TestTexte.get(ParentLabels.trustRes(SourceTrust.TRUSTED_CHILD_SOURCE))).isEqualTo("Vertrauenswürdige Kinderquelle")
        assertThat(xyz.steier.sidetube.TestTexte.get(ParentLabels.trustRes(SourceTrust.TRUSTED_SERIES))).isEqualTo("Vertrauenswürdige Reihe")
        assertThat(xyz.steier.sidetube.TestTexte.get(ParentLabels.trustRes(SourceTrust.PER_VIDEO_REVIEW))).isEqualTo("Nur einzeln geprüfte Videos")
        assertThat(xyz.steier.sidetube.TestTexte.get(ParentLabels.trustRes(SourceTrust.PARENT_ONLY))).isEqualTo("Nur für Eltern")
        assertThat(xyz.steier.sidetube.TestTexte.get(ParentLabels.trustRes(SourceTrust.BLOCKED))).isEqualTo("Gesperrt")
        SourceTrust.entries.forEach { assertThat(xyz.steier.sidetube.TestTexte.get(ParentLabels.trustRes(it))).isNotEqualTo(it.id) }
    }

    @Test fun `history lines read in German`() {
        val event = ReviewEventEntity(contentId = "x", profileId = "p", decision = "deferred", actor = "Eltern",
            at = 0L, note = null)
        assertThat(ParentLabels.eventHeadline(event, xyz.steier.sidetube.TestTexte)).isEqualTo("Zurückgestellt · Eltern")
        assertThat(ParentLabels.eventTime(event, java.time.ZoneOffset.UTC)).isEqualTo("01.01.1970 00:00")
        assertThat(ParentLabels.decision("approved", xyz.steier.sidetube.TestTexte)).isEqualTo("Freigegeben")
        assertThat(ParentLabels.decision("somethingNew", xyz.steier.sidetube.TestTexte)).isEqualTo("somethingNew")
    }

    @Test fun `age bands and summary`() {
        assertThat(AgeBand.entries.map { xyz.steier.sidetube.TestTexte.get(ParentLabels.ageBandRes(it)) })
            .containsExactly("Vorschule (3–5)", "Jüngere Kinder (6–8)", "Kinder (9–11)", "Ab 12").inOrder()
        assertThat(profileSummary(profile.copy(dailyLimitMinutes = 30), xyz.steier.sidetube.TestTexte)).isEqualTo("30 Minuten am Tag · Ruhezeit ab 20:00")
        assertThat(profileSummary(profile.copy(bedtimeEnabled = false), xyz.steier.sidetube.TestTexte)).isEqualTo("ohne Tageslimit · ohne Ruhezeit")
    }
}
