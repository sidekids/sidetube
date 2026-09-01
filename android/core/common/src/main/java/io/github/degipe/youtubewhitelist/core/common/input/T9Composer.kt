package io.github.degipe.youtubewhitelist.core.common.input

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Multi-tap T9 text entry for the Sidephone number keys.
 *
 * Pressing the same key again cycles through its characters; a different key or
 * [COMMIT_DELAY_MS] of silence commits the current character.
 */
class T9Composer(private val scope: CoroutineScope) {

    private var pendingDigit: Int? = null
    private var pendingCharacterIndex = 0
    private var commitJob: Job? = null

    fun digit(current: String, digit: Int): String {
        val characters = T9_CHARACTERS[digit] ?: return current

        val next = if (pendingDigit == digit && current.isNotEmpty()) {
            pendingCharacterIndex = (pendingCharacterIndex + 1) % characters.length
            current.dropLast(1) + characters[pendingCharacterIndex]
        } else {
            pendingCharacterIndex = 0
            current + characters.first()
        }

        pendingDigit = digit
        commitJob?.cancel()
        commitJob = scope.launch {
            delay(COMMIT_DELAY_MS)
            commit()
        }
        return next
    }

    fun backspace(current: String): String {
        commit()
        return current.dropLast(1)
    }

    fun commit() {
        pendingDigit = null
        pendingCharacterIndex = 0
        commitJob?.cancel()
        commitJob = null
    }

    private companion object {
        const val COMMIT_DELAY_MS = 800L

        val T9_CHARACTERS = mapOf(
            0 to " ",
            1 to "1",
            2 to "abc",
            3 to "def",
            4 to "ghi",
            5 to "jkl",
            6 to "mno",
            7 to "pqrs",
            8 to "tuv",
            9 to "wxyz",
        )
    }
}
