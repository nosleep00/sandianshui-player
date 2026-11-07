package com.sandianshui.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SandianshuiColorScheme = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Black,
    background = Black,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
)

@Composable
fun SandianshuiTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // 本应用固定暗色，参数仅保留扩展性。
    MaterialTheme(
        colorScheme = SandianshuiColorScheme,
        content = content,
    )
}
