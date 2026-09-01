package io.github.degipe.youtubewhitelist.core.auth.repository

import io.github.degipe.youtubewhitelist.core.auth.google.GoogleSignInManager
import io.github.degipe.youtubewhitelist.core.auth.repository.ParentAccountRepositoryImpl.Companion.toDomain
import io.github.degipe.youtubewhitelist.core.auth.token.TokenManager
import io.github.degipe.youtubewhitelist.core.common.di.IoDispatcher
import io.github.degipe.youtubewhitelist.core.data.model.AuthState
import io.github.degipe.youtubewhitelist.core.data.repository.AuthRepository
import io.github.degipe.youtubewhitelist.core.database.dao.ParentAccountDao
import io.github.degipe.youtubewhitelist.core.database.entity.ParentAccountEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val googleSignInManager: GoogleSignInManager,
    private val tokenManager: TokenManager,
    private val parentAccountDao: ParentAccountDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override suspend fun createLocalAccount() = withContext(ioDispatcher) {
        val existingAccount = parentAccountDao.getParentAccountOnce()
        val account = existingAccount ?: ParentAccountEntity(
            id = UUID.randomUUID().toString(),
            googleAccountId = "local",
            email = "",
            pinHash = "",
            createdAt = System.currentTimeMillis(),
        ).also { parentAccountDao.insert(it) }
        _authState.value = AuthState.Authenticated(account.toDomain())
    }

    override suspend fun signOut() = withContext(ioDispatcher) {
        tokenManager.clearTokens()
        googleSignInManager.signOut()
        parentAccountDao.deleteAll()
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun checkAuthState() = withContext(ioDispatcher) {
        val account = parentAccountDao.getParentAccountOnce()
        _authState.value = if (account != null) {
            AuthState.Authenticated(account.toDomain())
        } else {
            AuthState.Unauthenticated
        }
    }
}
