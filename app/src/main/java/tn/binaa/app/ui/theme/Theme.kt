package tn.binaa.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Navy = Color(0xFF0F3D5E)
val NavyLight = Color(0xFF1F5F8B)
val Amber = Color(0xFFF5A623)
val Present = Color(0xFF2E7D32)
val Half = Color(0xFFEF8F00)
val Absent = Color(0xFFC62828)
val Surface = Color(0xFFF4F6F9)
val Muted = Color(0xFF6B7785)

private val Scheme = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F2),
    onPrimaryContainer = Navy,
    secondary = Amber,
    onSecondary = Color(0xFF2B1B00),
    secondaryContainer = Color(0xFFFFE8C2),
    background = Surface,
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EDF2),
    error = Absent,
)

private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun BinaaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
