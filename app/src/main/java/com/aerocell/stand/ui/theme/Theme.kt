package com.aerocell.stand.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AeroRed = Color(0xFFE53935)
val AeroRedDark = Color(0xFFB71C1C)
val AeroBg = Color(0xFF070707)
val AeroPanel = Color(0xFF141414)
val AeroLine = Color(0xE6FFFFFF)
val AeroMuted = Color(0xB8FFFFFF)
val AeroCyan = Color(0xFF4FC3F7)
val AeroGreen = Color(0xFF8BC34A)

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
                color = Color.White
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
