package com.example.havenspure_kotlin_prototype.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Custom colors
val PrimaryColor = Color(0xFF00A4B4)
val PrimaryDarkColor = Color(0xFF007A8A)
val AccentColor = Color(0xFF00C844)
val GradientStart = Color(0xFFCCF4D9)
val GradientEnd = Color(0xFFA1E6F7)
val TextDark = Color(0xFF1F2D42)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryColor,
    secondary = AccentColor,
    tertiary = TextDark
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryColor,
    secondary = AccentColor,
    tertiary = TextDark
)

@Composable
fun WilhelmshavenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,  // Use a different name here
        content = content
    )
}