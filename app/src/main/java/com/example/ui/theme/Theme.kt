package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF818CF8),
        onPrimary = Color(0xFF1E1B4B),
        primaryContainer = Color(0xFF3730A3),
        onPrimaryContainer = Color(0xFFE0E7FF),
        secondary = CyanAccent,
        onSecondary = Color(0xFF083344),
        secondaryContainer = Color(0xFF164E63),
        onSecondaryContainer = Color(0xFFCFFAFE),
        tertiary = VioletTertiary,
        onTertiary = Color(0xFF2E1065),
        background = DarkBackground,
        onBackground = Color(0xFFF1F5F9),
        surface = DarkSurface,
        onSurface = Color(0xFFF1F5F9),
        surfaceVariant = DarkSurfaceElevated,
        onSurfaceVariant = Color(0xFFCBD5E1),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = IndigoPrimary,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E7FF),
        onPrimaryContainer = Color(0xFF1E1B4B),
        secondary = Color(0xFF0891B2),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCFFAFE),
        onSecondaryContainer = Color(0xFF164E63),
        tertiary = Color(0xFF7C3AED),
        onTertiary = Color.White,
        background = LightBackground,
        onBackground = Color(0xFF0F172A),
        surface = LightSurface,
        onSurface = Color(0xFF0F172A),
        surfaceVariant = LightSurfaceElevated,
        onSurfaceVariant = Color(0xFF475569),
    )

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
  val colorScheme =
      when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
          val context = LocalContext.current
          if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
      }

  MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
  )
}
