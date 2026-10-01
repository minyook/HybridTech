package com.minyook.sllm2.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val HybridTeal = Color(0xFF007A72)
val HybridBlue = Color(0xFF086E9F)
val HybridCanvas = Color(0xFFF3F7FA)
val HybridChatCanvas = Color(0xFFF8FAFA)
val HybridCard = Color(0xFFFFFFFF)
val HybridInk = Color(0xFF10283A)
val HybridBody = Color(0xFF385264)
val HybridMuted = Color(0xFF62798A)
val HybridHairline = Color(0xFFC8D8E2)
val HybridDark = Color(0xFF0D2638)
val HybridDarkElevated = Color(0xFF143A52)
val HybridOnDark = Color(0xFFF3FAFE)
val HybridOnDarkSoft = Color(0xFFABC6D5)
val HybridSafe = Color(0xFF087F5B)
val HybridWarning = Color(0xFFA96600)
val HybridDanger = Color(0xFFB52D35)
val HybridAssistant = Color(0xFFF7FBFD)
val HybridWorker = Color(0xFFE2F1F8)

private val colorScheme = lightColorScheme(
    primary = HybridTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F1EC),
    onPrimaryContainer = HybridInk,
    secondary = HybridBlue,
    onSecondary = Color.White,
    background = HybridChatCanvas,
    onBackground = HybridInk,
    surface = HybridCard,
    onSurface = HybridInk,
    surfaceVariant = Color(0xFFE3F0F6),
    onSurfaceVariant = HybridBody,
    outline = HybridHairline,
    error = HybridDanger,
)

private val typography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = HybridInk),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, color = HybridInk),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = HybridInk),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = HybridInk),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, color = HybridBody),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun HybridTechTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}

val GasNumberFont = FontFamily.Monospace
