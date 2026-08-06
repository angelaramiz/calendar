package com.calendarfinance.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Green700,
    onPrimary = Color.White,
    primaryContainer = GreenLight,
    secondary = Blue500,
    onSecondary = Color.White,
    secondaryContainer = BlueLight,
    error = Red500,
    errorContainer = RedLight,
    background = Background,
    surface = Surface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    tertiary = Orange500,
    tertiaryContainer = OrangeLight
)

private val DarkColorScheme = darkColorScheme(
    primary = Green500,
    onPrimary = Color(0xFF003300),
    primaryContainer = Green700,
    secondary = Blue500,
    secondaryContainer = Color(0xFF003366),
    error = Red500,
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
)

@Composable
fun CalendarFinanceTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
