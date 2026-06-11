// © Art Chaidarun

package com.chaidarun.chronofile

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ColorPrimary = Color(0xFF284759)
val ColorPrimaryDark = Color(0xFF1C3645)
val ColorAccent = Color(0xFF81C784)
val ColorFadedText = Color(0xFF3B5A6B)

/** Dark-theme secondary-text emphasis, matching the old TextAppearance.AppCompat.Medium default */
val ColorSecondaryText = Color.White.copy(alpha = 0.7f)

/** Slightly-rounded rectangle for accent buttons (matches the old Material button shape) */
val ButtonShape = RoundedCornerShape(4.dp)

private val Exo2 = FontFamily(Font(R.font.exo2_regular, FontWeight.Normal))

private val AppColors =
  darkColorScheme(
    background = ColorPrimaryDark,
    onBackground = Color.White,
    onPrimary = ColorPrimaryDark,
    onSecondary = ColorPrimaryDark,
    onSurface = Color.White,
    onSurfaceVariant = Color.White,
    // Map the old single colorAccent (green) to M3 primary so default-accent components (dialog
    // buttons, text-field focus/cursor/labels, tab indicator, date-picker selection) render green
    // and legible rather than the near-invisible blue of ColorPrimary on the dark background
    primary = ColorAccent,
    secondary = ColorAccent,
    surface = ColorPrimaryDark,
    surfaceVariant = ColorPrimary,
  )

// letterSpacing 0 matches the old TextView default. M3's per-role typography bakes in non-zero
// tracking (bodyMedium 0.25sp, bodyLarge 0.5sp) which widened text vs the pre-Compose look
private val baseTextStyle = TextStyle(fontFamily = Exo2, letterSpacing = 0.sp)

private val AppTypography =
  Typography().run {
    Typography(
      // 18sp matches the old item_entry's TextAppearance.AppCompat.Medium (the timeline entry rows
      // are the sole bodyLarge consumer). Unspecified lineHeight + includeFontPadding=true
      // reproduce the old TextView's font-metric line box exactly, undoing M3's compression
      // (shorter default lineHeight and includeFontPadding=false)
      bodyLarge =
        bodyLarge
          .merge(baseTextStyle)
          .copy(
            fontSize = 18.sp,
            lineHeight = TextUnit.Unspecified,
            platformStyle = PlatformTextStyle(includeFontPadding = true),
          ),
      bodyMedium = bodyMedium.merge(baseTextStyle).copy(fontSize = 15.sp, lineHeight = 18.sp),
      bodySmall = bodySmall.merge(baseTextStyle),
      displayLarge = displayLarge.merge(baseTextStyle),
      displayMedium = displayMedium.merge(baseTextStyle),
      displaySmall = displaySmall.merge(baseTextStyle),
      headlineLarge = headlineLarge.merge(baseTextStyle),
      headlineMedium = headlineMedium.merge(baseTextStyle),
      headlineSmall = headlineSmall.merge(baseTextStyle),
      labelLarge = labelLarge.merge(baseTextStyle),
      labelMedium = labelMedium.merge(baseTextStyle),
      labelSmall = labelSmall.merge(baseTextStyle),
      titleLarge = titleLarge.merge(baseTextStyle),
      titleMedium = titleMedium.merge(baseTextStyle),
      titleSmall = titleSmall.merge(baseTextStyle),
    )
  }

@Composable
fun ChronofileTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = AppColors, typography = AppTypography, content = content)
}
