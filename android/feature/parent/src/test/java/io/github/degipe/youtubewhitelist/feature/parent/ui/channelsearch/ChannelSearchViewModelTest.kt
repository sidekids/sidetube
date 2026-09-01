package io.github.degipe.youtubewhitelist.feature.parent.ui.channelsearch

import com.google.common.truth.Truth.assertThat
import io.github.degipe.youtubewhitelist.core.common.R as CommonR
import io.github.degipe.youtubewhitelist.core.common.model.WhitelistItemType
import io.github.degipe.youtubewhitelist.core.common.result.AppResult
import io.github.degipe.youtubewhitelist.core.common.ui.UiMessage
import io.github.degipe.youtubewhitelist.core.data.model.WhitelistItem
import io.github.degipe.youtubewhitelist.core.data.model.YouTubeMetadata
import io.github.degipe.youtubewhitelist.core.data.repository.WhitelistRepository
import io.github.degipe.youtubewhitelist.core.data.repository.YouTubeApiRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelSearchViewModelTest {

    private lateinit var youTubeApiRepository: YouTubeApiRepository
    private lateinit var whitelistRepository: WhitelistRepository
    private val testDispatcher = StandardTestDispatcher()

    private val checker = YouTubeMetadata.Channel(
        youtubeId = "UCQtsd17U8NOM1VRI8oxdwiQ",
        title = "CHECKER WELT",
        thumbnailUrl = "https://img/checker.jpg",
        description = "Checker Tobi und Checker Julian",
        subscriberCount = null,
        videoCount = null,
        uploadsPlaylistId = null
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        youTubeApiRepository = mockk()
        whitelistRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ChannelSearchViewModel(
        youTubeApiRepository = youTubeApiRepository,
        whitelistRepository = whitelistRepository,
        profileId = "profile-1"
    )

    @Test
    fun `search returns channels for the typed name`() = runTest(testDispatcher) {
        coEvery { youTubeApiRepository.searchChannels("Checker") } returns
            AppResult.Success(listOf(checker))

        val viewModel = createViewModel()
        viewModel.onQueryChanged("Checker")
        viewModel.search()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.results).hasSize(1)
        assertThat(viewModel.uiState.value.results[0].title).isEqualTo("CHECKER WELT")
        assertThat(viewModel.uiState.value.hasSearched).isTrue()
        assertThat(viewModel.uiState.value.isSearching).isFalse()
    }

    @Test
    fun `blank query does not spend quota`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onQueryChanged("   ")
        viewModel.search()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { youTubeApiRepository.searchChannels(any()) }
    }

    @Test
    fun `search error is shown as a translatable message`() = runTest(testDispatcher) {
        coEvery { youTubeApiRepository.searchChannels(any()) } returns
            AppResult.Error("search failed", uiMessage = UiMessage(CommonR.string.error_search_failed))

        val viewModel = createViewModel()
        viewModel.onQueryChanged("Checker")
        viewModel.search()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.error?.resId).isEqualTo(CommonR.string.error_search_failed)
        assertThat(viewModel.uiState.value.results).isEmpty()
    }

    @Test
    fun `approving a channel adds it through the canonical channel url`() = runTest(testDispatcher) {
        coEvery { youTubeApiRepository.searchChannels(any()) } returns AppResult.Success(listOf(checker))
        coEvery {
            whitelistRepository.addItemFromUrl(
                "profile-1",
                "https://www.youtube.com/channel/UCQtsd17U8NOM1VRI8oxdwiQ"
            )
        } returns AppResult.Success(
            WhitelistItem(
                id = "wl-1",
                kidProfileId = "profile-1",
                type = WhitelistItemType.CHANNEL,
                youtubeId = checker.youtubeId,
                title = checker.title,
                thumbnailUrl = checker.thumbnailUrl,
                channelTitle = null,
                addedAt = 1000L
            )
        )

        val viewModel = createViewModel()
        viewModel.onQueryChanged("Checker")
        viewModel.search()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.approve(checker)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.approvedIds).contains(checker.youtubeId)
        assertThat(viewModel.uiState.value.addingId).isNull()
    }

    @Test
    fun `a duplicate is reported instead of silently added`() = runTest(testDispatcher) {
        coEvery { whitelistRepository.addItemFromUrl(any(), any()) } returns
            AppResult.Error(
                "already whitelisted",
                uiMessage = UiMessage(CommonR.string.error_already_whitelisted)
            )

        val viewModel = createViewModel()
        viewModel.approve(checker)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.error?.resId)
            .isEqualTo(CommonR.string.error_already_whitelisted)
        assertThat(viewModel.uiState.value.approvedIds).isEmpty()
    }
}
