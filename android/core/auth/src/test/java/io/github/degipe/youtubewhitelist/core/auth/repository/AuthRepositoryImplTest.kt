package io.github.degipe.youtubewhitelist.core.auth.repository

import com.google.common.truth.Truth.assertThat
import io.github.degipe.youtubewhitelist.core.auth.google.GoogleSignInManager
import io.github.degipe.youtubewhitelist.core.auth.token.TokenManager
import io.github.degipe.youtubewhitelist.core.data.model.AuthState
import io.github.degipe.youtubewhitelist.core.database.dao.ParentAccountDao
import io.github.degipe.youtubewhitelist.core.database.entity.ParentAccountEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AuthRepositoryImplTest {

    private lateinit var googleSignInManager: GoogleSignInManager
    private lateinit var tokenManager: TokenManager
    private lateinit var parentAccountDao: ParentAccountDao
    private lateinit var repository: AuthRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        googleSignInManager = mockk(relaxed = true)
        tokenManager = mockk(relaxed = true)
        parentAccountDao = mockk(relaxed = true)

        repository = AuthRepositoryImpl(
            googleSignInManager = googleSignInManager,
            tokenManager = tokenManager,
            parentAccountDao = parentAccountDao,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `createLocalAccount creates a local account without Google data`() = runTest(testDispatcher) {
        coEvery { parentAccountDao.getParentAccountOnce() } returns null
        val accountSlot = slot<ParentAccountEntity>()
        coEvery { parentAccountDao.insert(capture(accountSlot)) } returns Unit

        repository.createLocalAccount()

        assertThat(accountSlot.captured.googleAccountId).isEqualTo("local")
        assertThat(accountSlot.captured.email).isEmpty()
        assertThat(accountSlot.captured.pinHash).isEmpty()
        assertThat(repository.authState.value).isInstanceOf(AuthState.Authenticated::class.java)
    }

    @Test
    fun `createLocalAccount reuses the existing account`() = runTest(testDispatcher) {
        val existing = ParentAccountEntity(
            id = "existing-id",
            googleAccountId = "local",
            email = "",
            pinHash = "salt:hash",
            createdAt = 1000L
        )
        coEvery { parentAccountDao.getParentAccountOnce() } returns existing

        repository.createLocalAccount()

        coVerify(exactly = 0) { parentAccountDao.insert(any()) }
        val state = repository.authState.value
        assertThat(state).isInstanceOf(AuthState.Authenticated::class.java)
        assertThat((state as AuthState.Authenticated).account.id).isEqualTo("existing-id")
    }

    @Test
    fun `signOut clears tokens and updates state`() = runTest(testDispatcher) {
        repository.signOut()

        coVerify { tokenManager.clearTokens() }
        coVerify { googleSignInManager.signOut() }
        coVerify { parentAccountDao.deleteAll() }
        assertThat(repository.authState.value).isEqualTo(AuthState.Unauthenticated)
    }

    @Test
    fun `checkAuthState sets Authenticated when account exists`() = runTest(testDispatcher) {
        val entity = ParentAccountEntity(
            id = "test-id",
            googleAccountId = "google-id",
            email = "test@example.com",
            pinHash = "salt:hash",
            createdAt = 1000L
        )
        coEvery { parentAccountDao.getParentAccountOnce() } returns entity

        repository.checkAuthState()

        assertThat(repository.authState.value).isInstanceOf(AuthState.Authenticated::class.java)
    }

    @Test
    fun `checkAuthState sets Unauthenticated when no account`() = runTest(testDispatcher) {
        coEvery { parentAccountDao.getParentAccountOnce() } returns null

        repository.checkAuthState()

        assertThat(repository.authState.value).isEqualTo(AuthState.Unauthenticated)
    }

    @Test
    fun `initial state is Loading`() {
        assertThat(repository.authState.value).isEqualTo(AuthState.Loading)
    }
}
