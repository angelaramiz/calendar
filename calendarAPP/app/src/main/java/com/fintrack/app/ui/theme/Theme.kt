package com.fintrack.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0F6A5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E9E0),
    onPrimaryContainer = Color(0xFF0A3F36),
    secondary = Color(0xFF5C7C6F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE8E1),
    onSecondaryContainer = Color(0xFF22382F),
    tertiary = Color(0xFF4E7A8A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E5EC),
    onTertiaryContainer = Color(0xFF14333D),
    background = Color(0xFFF7F3EC),
    onBackground = Color(0xFF1B1C19),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF1B1C19),
    surfaceVariant = Color(0xFFE8E2D5),
    onSurfaceVariant = Color(0xFF4D4A42),
    outline = Color(0xFF7D776B),
    outlineVariant = Color(0xFFD8D2C2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7FD1B6),
    onPrimary = Color(0xFF05322A),
    primaryContainer = Color(0xFF0E4A3F),
    onPrimaryContainer = Color(0xFFD3E9E0),
    secondary = Color(0xFFA9C7B5),
    onSecondary = Color(0xFF10281F),
    secondaryContainer = Color(0xFF2A3F35),
    onSecondaryContainer = Color(0xFFDDE8E1),
    tertiary = Color(0xFF9CC3D5),
    onTertiary = Color(0xFF0A2E3A),
    tertiaryContainer = Color(0xFF24404D),
    onTertiaryContainer = Color(0xFFD3E5EC),
    background = Color(0xFF101413),
    onBackground = Color(0xFFE4E3DC),
    surface = Color(0xFF161D1B),
    onSurface = Color(0xFFE4E3DC),
    surfaceVariant = Color(0xFF2E3531),
    onSurfaceVariant = Color(0xFFC6C4B8),
    outline = Color(0xFF8F8B7E),
    outlineVariant = Color(0xFF3A3F39),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/**
 * Color semantico de ingresos: verde esmeralda legible en light y dark.
 * Usar siempre este helper en lugar de hardcodear Color(0xFF...) en pantallas.
 */
@Composable
fun incomeColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF7BD9A5) else Color(0xFF1E7A4C)

/**
 * Color semantico de gastos: rojo terracota legible en light y dark.
 * Usar siempre este helper en lugar de hardcodear Color(0xFF...) en pantallas.
 */
@Composable
fun expenseColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFFFFB4AB) else Color(0xFFB3261E)

@Composable
fun FinTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}
