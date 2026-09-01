package io.github.degipe.youtubewhitelist.feature.parent.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.degipe.youtubewhitelist.core.common.input.CollectSideInput
import io.github.degipe.youtubewhitelist.core.common.input.SideFocusState
import io.github.degipe.youtubewhitelist.core.common.input.SideInputAction
import io.github.degipe.youtubewhitelist.core.common.input.SideInputChannel
import io.github.degipe.youtubewhitelist.core.common.input.rememberSideFocus
import io.github.degipe.youtubewhitelist.core.common.input.sideFocusBorder
import io.github.degipe.youtubewhitelist.core.data.model.KidProfile
import androidx.compose.ui.res.stringResource
import io.github.degipe.youtubewhitelist.feature.parent.R
import androidx.annotation.StringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    viewModel: ParentDashboardViewModel,
    sideInput: SideInputChannel,
    onBackToKidMode: (profileId: String) -> Unit,
    onChangePin: () -> Unit,
    onOpenWhitelistManager: (profileId: String) -> Unit,
    onOpenChannelSearch: (profileId: String) -> Unit,
    onOpenBrowser: (profileId: String) -> Unit,
    onOpenSleepMode: (profileId: String) -> Unit,
    onEditProfile: (profileId: String) -> Unit,
    onWatchStats: (profileId: String) -> Unit,
    onExportImport: (parentAccountId: String) -> Unit,
    onCreateProfile: () -> Unit,
    onAbout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = dashboardActions(
        uiState = uiState,
        onOpenWhitelistManager = onOpenWhitelistManager,
        onOpenChannelSearch = onOpenChannelSearch,
        onOpenBrowser = onOpenBrowser,
        onOpenSleepMode = onOpenSleepMode,
        onEditProfile = onEditProfile,
        onWatchStats = onWatchStats,
        onExportImport = onExportImport,
        onCreateProfile = onCreateProfile,
        onChangePin = onChangePin,
        onAbout = onAbout
    )
    val focus = rememberSideFocus(actions.size)

    CollectSideInput(sideInput) { action ->
        when (action) {
            SideInputAction.Up, SideInputAction.Previous -> focus.moveUp()
            SideInputAction.Down, SideInputAction.Next -> focus.moveDown()
            SideInputAction.Select ->
                actions.getOrNull(focus.index)?.takeIf { it.enabled }?.onClick?.invoke()
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.parent_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { uiState.selectedProfileId?.let { onBackToKidMode(it) } },
                        enabled = uiState.selectedProfileId != null
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.parent_back_to_kid_mode)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingContent(modifier = Modifier.padding(padding))
        } else {
            DashboardContent(
                uiState = uiState,
                actions = actions,
                focus = focus,
                onProfileSelected = viewModel::selectProfile,
                onBackToKidMode = {
                    uiState.selectedProfileId?.let { onBackToKidMode(it) }
                },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DashboardContent(
    uiState: ParentDashboardUiState,
    actions: List<DashboardAction>,
    focus: SideFocusState,
    onProfileSelected: (String) -> Unit,
    onBackToKidMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile selector section
        Text(
            text = stringResource(R.string.parent_profiles),
            style = MaterialTheme.typography.titleMedium
        )

        if (uiState.profiles.isEmpty()) {
            Text(
                text = stringResource(R.string.parent_no_profiles),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ProfileSelector(
                profiles = uiState.profiles,
                selectedProfileId = uiState.selectedProfileId,
                onProfileSelected = onProfileSelected
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action cards
        Text(
            text = stringResource(R.string.parent_actions),
            style = MaterialTheme.typography.titleMedium
        )

        actions.forEachIndexed { index, action ->
            ActionCard(action = action, focused = focus.isFocused(index))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onBackToKidMode,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.parent_back_to_kid_mode))
        }
    }
}

private data class DashboardAction(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/** Reihenfolge der Elternaktionen — Fokus und Anzeige nutzen dieselbe Liste. */
private fun dashboardActions(
    uiState: ParentDashboardUiState,
    onOpenWhitelistManager: (profileId: String) -> Unit,
    onOpenChannelSearch: (profileId: String) -> Unit,
    onOpenBrowser: (profileId: String) -> Unit,
    onOpenSleepMode: (profileId: String) -> Unit,
    onEditProfile: (profileId: String) -> Unit,
    onWatchStats: (profileId: String) -> Unit,
    onExportImport: (parentAccountId: String) -> Unit,
    onCreateProfile: () -> Unit,
    onChangePin: () -> Unit,
    onAbout: () -> Unit,
): List<DashboardAction> {
    val profileId = uiState.selectedProfileId
    val accountId = uiState.parentAccountId

    return listOf(
        DashboardAction(
            icon = Icons.AutoMirrored.Filled.List,
            title = R.string.parent_action_whitelist,
            subtitle = R.string.parent_action_whitelist_sub,
            enabled = profileId != null,
            onClick = { profileId?.let(onOpenWhitelistManager) }
        ),
        DashboardAction(
            icon = Icons.Default.PersonSearch,
            title = R.string.parent_action_channel_search,
            subtitle = R.string.parent_action_channel_search_sub,
            enabled = profileId != null,
            onClick = { profileId?.let(onOpenChannelSearch) }
        ),
        DashboardAction(
            icon = Icons.Default.Search,
            title = R.string.parent_action_browser,
            subtitle = R.string.parent_action_browser_sub,
            enabled = profileId != null,
            onClick = { profileId?.let(onOpenBrowser) }
        ),
        DashboardAction(
            icon = Icons.Default.Bedtime,
            title = R.string.parent_action_sleep,
            subtitle = R.string.parent_action_sleep_sub,
            enabled = profileId != null,
            onClick = { profileId?.let(onOpenSleepMode) }
        ),
        DashboardAction(
            icon = Icons.Default.Settings,
            title = R.string.parent_action_edit_profile,
            subtitle = R.string.parent_action_edit_profile_sub,
            enabled = profileId != null,
            onClick = { profileId?.let(onEditProfile) }
        ),
        DashboardAction(
            icon = Icons.Default.QueryStats,
            title = R.string.parent_action_stats,
            subtitle = R.string.parent_action_stats_sub,
            enabled = profileId != null,
            onClick = { profileId?.let(onWatchStats) }
        ),
        DashboardAction(
            icon = Icons.Default.SwapHoriz,
            title = R.string.parent_action_backup,
            subtitle = R.string.parent_action_backup_sub,
            enabled = accountId != null,
            onClick = { accountId?.let(onExportImport) }
        ),
        DashboardAction(
            icon = Icons.Default.PersonAdd,
            title = R.string.parent_action_create_profile,
            subtitle = R.string.parent_action_create_profile_sub,
            enabled = true,
            onClick = onCreateProfile
        ),
        DashboardAction(
            icon = Icons.Default.Lock,
            title = R.string.parent_action_pin,
            subtitle = R.string.parent_action_pin_sub,
            enabled = true,
            onClick = onChangePin
        ),
        DashboardAction(
            icon = Icons.Default.Info,
            title = R.string.parent_action_about,
            subtitle = R.string.parent_action_about_sub,
            enabled = true,
            onClick = onAbout
        ),
    )
}

@Composable
private fun ProfileSelector(
    profiles: List<KidProfile>,
    selectedProfileId: String?,
    onProfileSelected: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(profiles, key = { it.id }) { profile ->
            ProfileChip(
                profile = profile,
                isSelected = profile.id == selectedProfileId,
                onClick = { onProfileSelected(profile.id) }
            )
        }
    }
}

@Composable
private fun ProfileChip(
    profile: KidProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = profile.name,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ActionCard(
    action: DashboardAction,
    focused: Boolean,
) {
    val icon = action.icon
    val enabled = action.enabled

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = action.onClick)
            .sideFocusBorder(focused),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (enabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(action.title),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = stringResource(action.subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
