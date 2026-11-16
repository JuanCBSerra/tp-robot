package com.tprobot.ovejero.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = TierraColorada,
    onPrimary = Color.White,
    primaryContainer = TierraClara,
    onPrimaryContainer = TierraOscura,
    secondary = VerdeMonte,
    onSecondary = Color.White,
    secondaryContainer = VerdeMonteClaro,
    onSecondaryContainer = VerdeMonte,
    tertiary = EstadoNormal,
    error = EstadoError,
    errorContainer = EstadoErrorFondo,
    background = Crema,
    surface = SurfaceLight,
    surfaceVariant = Color(0xFFF3EAE4),
    onBackground = TextoPrincipal,
    onSurface = TextoPrincipal,
    onSurfaceVariant = TextoSecundario,
    outline = CardBorder
)

@Composable
fun OvejeroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
