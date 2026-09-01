package io.github.degipe.youtubewhitelist.core.common.input

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class T9ComposerTest {

    @Test
    fun `repeated digit cycles through its characters`() = runTest {
        val composer = T9Composer(this)

        var query = composer.digit("", 2)
        assertThat(query).isEqualTo("a")
        query = composer.digit(query, 2)
        assertThat(query).isEqualTo("b")
        query = composer.digit(query, 2)
        assertThat(query).isEqualTo("c")
        query = composer.digit(query, 2)
        assertThat(query).isEqualTo("a")

        composer.commit()
    }

    @Test
    fun `a different digit commits the previous character`() = runTest {
        val composer = T9Composer(this)

        var query = composer.digit("", 4)
        query = composer.digit(query, 4)
        query = composer.digit(query, 6)

        assertThat(query).isEqualTo("hm")
        composer.commit()
    }

    @Test
    fun `same digit starts a new character after the commit delay`() = runTest {
        val composer = T9Composer(this)

        var query = composer.digit("", 2)
        advanceTimeBy(801)
        query = composer.digit(query, 2)

        assertThat(query).isEqualTo("aa")
        composer.commit()
    }

    @Test
    fun `backspace removes the last character and commits`() = runTest {
        val composer = T9Composer(this)

        var query = composer.digit("", 2)
        query = composer.digit(query, 3)
        query = composer.backspace(query)

        assertThat(query).isEqualTo("a")

        query = composer.digit(query, 2)
        assertThat(query).isEqualTo("aa")
        composer.commit()
    }

    @Test
    fun `unknown digit leaves the query untouched`() = runTest {
        val composer = T9Composer(this)

        assertThat(composer.digit("abc", 42)).isEqualTo("abc")
    }
}
