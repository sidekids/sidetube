// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.curation

import kotlinx.serialization.Serializable
import xyz.steier.sidetube.core.model.SensitiveTopic

/** Begriffslisten aus `content/risk-terms.json` – gemeinsam mit der iOS-Fassung. */
@Serializable
data class RiskTerms(
    val version: Int = 1,
    val note: String? = null,
    val hardBlock: List<String> = emptyList(),
    val exceptions: List<String> = emptyList(),
    val topics: Map<String, List<String>> = emptyMap()
)

data class RiskAssessment(
    val hardBlockTerms: List<String> = emptyList(),
    val topics: Set<SensitiveTopic> = emptySet(),
    val matchedTerms: List<String> = emptyList(),
    val isShort: Boolean = false,
    val isLive: Boolean = false
) {
    val isHardBlocked: Boolean get() = hardBlockTerms.isNotEmpty()
    val requiresReview: Boolean get() = topics.isNotEmpty() || isShort || isLive
}

/**
 * Automatische Vorpruefung. Sie darf markieren und ablehnen, aber **nie freigeben** – ein
 * unauffaelliger Titel kann trotzdem ungeeignet sein. Die Entscheidung bleibt bei den Eltern.
 */
class RiskScreen(private val terms: RiskTerms) {

    /**
     * Ein Begriff zaehlt nur am Wortanfang. Sonst trifft "täter" in "Attentäter" und "nackt" in
     * "knackt"; Staemme wie "entführ" sollen dagegen weiter auf "entführt" passen. Fehlalarme
     * sind kein Schoenheitsfehler: Wer sie staendig wegklickt, uebersieht den echten Treffer.
     */
    fun assess(title: String, description: String? = null, durationSeconds: Int? = null): RiskAssessment {
        val haystack = " $title ${description.orEmpty()} ".lowercase()
        val words = haystack.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

        val hardBlocks = terms.hardBlock.filter { matches(it, words, haystack) }
        val topics = mutableSetOf<SensitiveTopic>()
        val matched = mutableListOf<String>()
        for ((key, list) in terms.topics) {
            val topic = SensitiveTopic.from(key) ?: continue
            val hits = list.filter { matches(it, words, haystack) }
            if (hits.isNotEmpty()) { topics += topic; matched += hits }
        }

        return RiskAssessment(
            hardBlockTerms = hardBlocks,
            topics = topics,
            matchedTerms = matched,
            isShort = haystack.contains("#shorts") || haystack.contains("#short ") ||
                (durationSeconds != null && durationSeconds <= 60),
            isLive = haystack.contains("livestream") || haystack.contains(" live ") || haystack.contains("🔴")
        )
    }

    private fun matches(term: String, words: List<String>, haystack: String): Boolean {
        if (term.contains(' ')) return haystack.contains(" $term")
        return words.any { word ->
            word.startsWith(term) && terms.exceptions.none { word.startsWith(it) }
        }
    }

    /** Belastende Themen machen eine Nachricht zur Elternsache. */
    fun newsStatus(assessment: RiskAssessment): xyz.steier.sidetube.core.model.NewsStatus {
        val heavy = setOf(
            SensitiveTopic.WAR, SensitiveTopic.VIOLENCE, SensitiveTopic.DEATH,
            SensitiveTopic.DISASTER, SensitiveTopic.CRIME, SensitiveTopic.FEAR, SensitiveTopic.HORROR
        )
        return when {
            assessment.topics.any { it in heavy } -> xyz.steier.sidetube.core.model.NewsStatus.SENSITIVE
            SensitiveTopic.POLITICS in assessment.topics -> xyz.steier.sidetube.core.model.NewsStatus.PARENT_REVIEW
            else -> xyz.steier.sidetube.core.model.NewsStatus.SAFE
        }
    }
}
