package io.github.degipe.youtubewhitelist.feature.parent.ui.whitelist

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.degipe.youtubewhitelist.core.common.model.WhitelistItemType
import io.github.degipe.youtubewhitelist.core.common.result.AppResult
import io.github.degipe.youtubewhitelist.core.data.model.WhitelistItem
import io.github.degipe.youtubewhitelist.core.data.repository.WhitelistRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import io.github.degipe.youtubewhitelist.core.export.content.StarterPack
import io.github.degipe.youtubewhitelist.core.export.content.StarterPackImportResult
import io.github.degipe.youtubewhitelist.core.export.content.StarterPackService
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WhitelistManagerViewModelTest {

    private lateinit var whitelistRepository: WhitelistRepository
    private lateinit var viewModel: WhitelistManagerViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val profileId = "profile-123"

    private val sampleItems = listOf(
        WhitelistItem(
            id = "1", kidProfileId = profileId,
            type = WhitelistItemType.CHANNEL, youtubeId = "UC123",
            title = "Cool Channel", thumbnailUrl = "https://img.youtube.com/1.jpg",
            channelTitle = null, addedAt = 1000L
        ),
        WhitelistItem(
            id = "2", kidProfileId = profileId,
            type = WhitelistItemType.VIDEO, youtubeId = "vid456",
            title = "Fun Video", thumbnailUrl = "https://img.youtube.com/2.jpg",
            channelTitle = "Cool Channel", addedAt = 2000L
        ),
        WhitelistItem(
            id = "3", kidProfileId = profileId,
            type = WhitelistItemType.PLAYLIST, youtubeId = "PL789",
            title = "Learning Playlist", thumbnailUrl = "https://img.youtube.com/3.jpg",
            channelTitle = "Edu Channel", addedAt = 3000L
        )
    )

    private val itemsFlow = MutableStateFlow<List<WhitelistItem>>(emptyList())

    private lateinit var starterPackService: StarterPackService

    private val starterPack = StarterPack(
        fileName = "alter-9-11.json", id = "alter-9-11", title = "Startpaket 9-11 Jahre",
        note = null, videoCount = 40, hasProfilePreset = true
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whitelistRepository = mockk(relaxed = true)
        starterPackService = mockk(relaxed = true)
        coEvery { starterPackService.availablePacks() } returns listOf(starterPack)
        every { whitelistRepository.getItemsByProfile(profileId) } returns itemsFlow
        every { whitelistRepository.getItemsByProfileAndType(any(), any()) } returns itemsFlow
        viewModel = WhitelistManagerViewModel(whitelistRepository, starterPackService, profileId)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Initial State ---

    @Test
    fun `initial state has empty items and no filter`() {
        val state = viewModel.uiState.value
        assertThat(state.items).isEmpty()
        assertThat(state.filterType).isNull()
        assertThat(state.isLoading).isTrue()
        assertThat(state.error).isNull()
        assertThat(state.addUrlDialogVisible).isFalse()
    }

    // --- Loading Items ---

    @Test
    fun `items are loaded from repository`() = runTest(testDispatcher) {
        itemsFlow.value = sampleItems
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.items).hasSize(3)
        assertThat(viewModel.uiState.value.items).isEqualTo(sampleItems)
    }

    @Test
    fun `empty list shows no items`() = runTest(testDispatcher) {
        itemsFlow.value = emptyList()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.items).isEmpty()
    }

    // --- Filtering ---

    @Test
    fun `setFilter updates filter type and re-queries repository`() = runTest(testDispatcher) {
        val channelItems = sampleItems.filter { it.type == WhitelistItemType.CHANNEL }
        val channelFlow = MutableStateFlow(channelItems)
        every {
            whitelistRepository.getItemsByProfileAndType(profileId, WhitelistItemType.CHANNEL)
        } returns channelFlow

        viewModel.setFilter(WhitelistItemType.CHANNEL)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.filterType).isEqualTo(WhitelistItemType.CHANNEL)
        assertThat(viewModel.uiState.value.items).isEqualTo(channelItems)
    }

    @Test
    fun `clearFilter shows all items`() = runTest(testDispatcher) {
        viewModel.setFilter(WhitelistItemType.VIDEO)
        testDispatcher.scheduler.advanceUntilIdle()

        itemsFlow.value = sampleItems
        viewModel.clearFilter()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.filterType).isNull()
        assertThat(viewModel.uiState.value.items).isEqualTo(sampleItems)
    }

    // --- Add from URL ---

    @Test
    fun `showAddUrlDialog sets dialog visible`() {
        viewModel.showAddUrlDialog()
        assertThat(viewModel.uiState.value.addUrlDialogVisible).isTrue()
    }

    @Test
    fun `dismissAddUrlDialog hides dialog`() {
        viewModel.showAddUrlDialog()
        viewModel.dismissAddUrlDialog()
        assertThat(viewModel.uiState.value.addUrlDialogVisible).isFalse()
    }

    @Test
    fun `addFromUrl success hides dialog and shows success message`() = runTest(testDispatcher) {
        val newItem = sampleItems[1]
        coEvery {
            whitelistRepository.addItemFromUrl(profileId, "https://youtube.com/watch?v=vid456")
        } returns AppResult.Success(newItem)

        viewModel.showAddUrlDialog()
        viewModel.addFromUrl("https://youtube.com/watch?v=vid456")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.addUrlDialogVisible).isFalse()
        assertThat(state.isAdding).isFalse()
        assertThat(state.successMessage).isEqualTo("Fun Video added to whitelist")
    }

    @Test
    fun `addFromUrl shows loading state while adding`() = runTest(testDispatcher) {
        coEvery {
            whitelistRepository.addItemFromUrl(profileId, any())
        } returns AppResult.Success(sampleItems[0])

        viewModel.addFromUrl("https://youtube.com/watch?v=test")

        // Before advancing, should be in adding state
        assertThat(viewModel.uiState.value.isAdding).isTrue()

        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.uiState.value.isAdding).isFalse()
    }

    @Test
    fun `addFromUrl error shows error message`() = runTest(testDispatcher) {
        coEvery {
            whitelistRepository.addItemFromUrl(profileId, "https://youtube.com/watch?v=invalid")
        } returns AppResult.Error("Video nicht gefunden")

        viewModel.addFromUrl("https://youtube.com/watch?v=invalid")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.error).isEqualTo("Video nicht gefunden")
        assertThat(state.isAdding).isFalse()
    }

    @Test
    fun `addFromUrl with blank URL does nothing`() = runTest(testDispatcher) {
        viewModel.addFromUrl("")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { whitelistRepository.addItemFromUrl(any(), any()) }
    }

    // --- Remove Item ---

    @Test
    fun `removeItem calls repository`() = runTest(testDispatcher) {
        val item = sampleItems[0]
        viewModel.removeItem(item)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { whitelistRepository.removeItem(item) }
    }

    // --- Message Dismissal ---

    @Test
    fun `dismissError clears error`() {
        // Manually set error state through addFromUrl error
        viewModel.dismissError()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `dismissSuccessMessage clears success message`() {
        viewModel.dismissSuccessMessage()
        assertThat(viewModel.uiState.value.successMessage).isNull()
    }

    // --- Startpakete ---

    @Test
    fun `showStarterPackDialog laedt die verfuegbaren Pakete`() = runTest {
        viewModel.showStarterPackDialog()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.starterPackDialogVisible).isTrue()
        assertThat(viewModel.uiState.value.starterPacks).containsExactly(starterPack)
    }

    @Test
    fun `importStarterPack meldet das Ergebnis und schliesst den Dialog`() = runTest {
        coEvery { starterPackService.import("alter-9-11.json", profileId, true) } returns
            AppResult.Success(StarterPackImportResult(added = 38, skipped = 2, presetApplied = true))

        viewModel.showStarterPackDialog()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.importStarterPack(starterPack)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.starterPackDialogVisible).isFalse()
        assertThat(state.isImportingStarterPack).isFalse()
        assertThat(state.starterPackResult?.added).isEqualTo(38)
        assertThat(state.starterPackResult?.skipped).isEqualTo(2)
    }

    @Test
    fun `ein Fehler beim Startpaket landet in der Fehlermeldung`() = runTest {
        coEvery { starterPackService.import(any(), any(), any()) } returns
            AppResult.Error(message = "kaputt")

        viewModel.importStarterPack(starterPack)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("kaputt")
        assertThat(viewModel.uiState.value.isImportingStarterPack).isFalse()
    }
}
