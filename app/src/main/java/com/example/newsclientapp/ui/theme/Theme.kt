package com.example.newsclientapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NewsBlue,
    onPrimary = NewsWhite,
    secondary = NewsBlueLight,
    onSecondary = NewsBlack,
    background = NewsBlack,
    onBackground = NewsWhite,
    surface = Color(0xFF1C1C1C),
    onSurface = NewsWhite,
    onSurfaceVariant = Color(0xFFB8C6E3)
)

private val LightColorScheme = lightColorScheme(
    primary = NewsBlue,
    onPrimary = NewsWhite,
    secondary = NewsBlueLight,
    onSecondary = NewsBlack,
    background = NewsWhite,
    onBackground = NewsBlack,
    surface = NewsWhite,
    onSurface = NewsBlack,
    onSurfaceVariant = NewsGray,
    outlineVariant = NewsBorder
)

@Composable
fun NewsClientAppTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
