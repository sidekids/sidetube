package io.github.degipe.youtubewhitelist.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SideBackground = Color(0xFF101418)
private val SideSurface = Color(0xFF1B2129)
private val SideSurfaceRaised = Color(0xFF262E38)
private val SideYellow = Color(0xFFFFB74D)
private val SideText = Color(0xFFF2F4F7)
private val SideTextMuted = Color(0xFFA8B1BD)

private val SideColorScheme = darkColorScheme(
    primary = SideYellow,
    onPrimary = Color(0xFF2A1A00),
    secondary = SideYellow,
    background = SideBackground,
    onBackground = SideText,
    surface = SideSurface,
    onSurface = SideText,
    surfaceVariant = SideSurfaceRaised,
    onSurfaceVariant = SideTextMuted,
    outline = SideTextMuted,
)

private val SideTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp),
)

@Composable
fun YouTubeWhitelistTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SideColorScheme,
        typography = SideTypography,
        content = content
    )
}

object SidePhoneDimensions {
    val card = 148.dp
    val edge = 8.dp
    val focusBorder = 3.dp
}
