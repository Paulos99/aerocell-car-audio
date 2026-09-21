package com.aerocell.stand.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AeroRed = Color(0xFFFF1A1A)
val AeroRedDark = Color(0xFFB00000)
val AeroRedGlow = Color(0xFFFF3B3B)
val AeroBg = Color(0xFF000000)
val AeroPanel = Color(0xFF141414)
val AeroPanelSoft = Color(0xFF1A1A1A)
val AeroTrackDim = Color(0xFF3A3A3A)
val AeroMuted = Color(0xB8FFFFFF)
val AeroSilver = Color(0xFFD8D8D8)

private val Colors = darkColorScheme(
    primary = AeroRed,
    onPrimary = Color.White,
    background = AeroBg,
    surface = AeroPanel,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun AerocellTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                fontSize = 64.sp,
                letterSpacing = 8.sp,
                color = AeroRedGlow
            ),
            headlineMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                color = Color.White
            ),
            titleLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            ),
            bodyLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                color = Color.White
            ),
            labelLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                color = AeroMuted
            )
        ),
        content = content
    )
}
