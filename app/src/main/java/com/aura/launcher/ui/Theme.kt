package com.aura.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AuraColors {
    val Text = Color(0xFFF2F2F7)
    val TextDim = Color(0xB3F2F2F7)
    val TextFaint = Color(0x66F2F2F7)
    val Accent = Color(0xFF8C9BFF)
    val DrawerScrim = Color(0xF00B0B10)
    val Surface = Color(0xFF1A1A22)
}

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AuraColors.Accent,
            surface = AuraColors.Surface,
            surfaceContainer = AuraColors.Surface,
            onSurface = AuraColors.Text,
            background = Color.Transparent,
        ),
        content = content,
    )
}
