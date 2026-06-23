package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = IndigoAccent,
    secondary = MintAccent,
    background = Slate950,
    surface = Slate900,
    surfaceVariant = Slate800,
    outline = Slate700,
    onBackground = TextWhite,
    onSurface = TextWhite,
    onPrimary = TextWhite,
    onSecondary = Slate950
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
