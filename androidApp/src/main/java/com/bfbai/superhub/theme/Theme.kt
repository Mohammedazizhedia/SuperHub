// Built with BFB.AI
package com.bfbai.superhub.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
private val LightColors = lightColorScheme(
    primary = PrimaryLight, secondary = SecondaryLight, tertiary = AccentLight,
    background = BackgroundLight, surface = SurfaceLight, error = ErrorLight,
    onPrimary = SurfaceLight, onSecondary = SurfaceLight, onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight, onError = SurfaceLight
)
private val DarkColors = darkColorScheme(
    primary = PrimaryDark, secondary = SecondaryDark, tertiary = AccentDark,
    background = BackgroundDark, surface = SurfaceDark, error = ErrorDark,
    onPrimary = BackgroundDark, onSecondary = BackgroundDark, onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark, onError = SurfaceLight
)
@Composable
fun SuperHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SuperHubTypography,
        shapes = SuperHubShapes,
        content = content
    )
}
