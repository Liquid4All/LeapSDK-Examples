package com.tldw.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val lightColors =
    lightColorScheme(
        primary = Color(0xFF6B5410),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFF5DFA3),
        onPrimaryContainer = Color(0xFF231A00),
        secondary = Color(0xFFF4B400),
        onSecondary = Color(0xFF1D1C14),
        secondaryContainer = Color(0xFFF5DFA3),
        onSecondaryContainer = Color(0xFF231A00),
        tertiary = Color(0xFF4F6516),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD2EC8B),
        onTertiaryContainer = Color(0xFF151F00),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFEFBF6),
        onBackground = Color(0xFF1D1C14),
        surface = Color(0xFFFEFBF6),
        onSurface = Color(0xFF1D1C14),
        surfaceVariant = Color(0xFFF4EEE4),
        onSurfaceVariant = Color(0xFF4C4838),
        outline = Color(0xFF7E7866),
        outlineVariant = Color(0xFFCFC8B6),
        surfaceContainer = Color(0xFFF4EEE4),
        surfaceContainerHigh = Color(0xFFEDE7DD),
        surfaceContainerHighest = Color(0xFFE7E1D8),
        inverseSurface = Color(0xFF322F27),
        inverseOnSurface = Color(0xFFF5F0E6),
    )

private val darkColors =
    darkColorScheme(
        primary = Color(0xFFF5C84A),
        onPrimary = Color(0xFF3A2C00),
        primaryContainer = Color(0xFF534008),
        onPrimaryContainer = Color(0xFFF5DFA3),
        secondary = Color(0xFFF4B400),
        onSecondary = Color(0xFF3A2C00),
        secondaryContainer = Color(0xFF534008),
        onSecondaryContainer = Color(0xFFF5DFA3),
        tertiary = Color(0xFFB6CF72),
        onTertiary = Color(0xFF1F3500),
        tertiaryContainer = Color(0xFF384C00),
        onTertiaryContainer = Color(0xFFD2EC8B),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF15140D),
        onBackground = Color(0xFFE8E2D4),
        surface = Color(0xFF15140D),
        onSurface = Color(0xFFE8E2D4),
        surfaceVariant = Color(0xFF211F17),
        onSurfaceVariant = Color(0xFFCFC8B6),
        outline = Color(0xFF989282),
        outlineVariant = Color(0xFF4C4838),
        surfaceContainer = Color(0xFF211F17),
        surfaceContainerHigh = Color(0xFF2C2920),
        surfaceContainerHighest = Color(0xFF37342A),
        inverseSurface = Color(0xFFE8E2D4),
        inverseOnSurface = Color(0xFF322F27),
    )

@Composable
fun TldwTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) darkColors else lightColors, content = content)
}
