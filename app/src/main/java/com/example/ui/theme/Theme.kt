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
    primary = Color(0xFFFFB4A5),
    onPrimary = Color(0xFF651800),
    primaryContainer = Color(0xFF8B2800),
    onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Color(0xFFE7BDB5),
    onSecondary = Color(0xFF442A24),
    secondaryContainer = Color(0xFF5D3F39),
    onSecondaryContainer = Color(0xFFFFDBD4),
    background = Color(0xFF201A18),
    onBackground = Color(0xFFEDE0DD),
    surface = Color(0xFF201A18),
    onSurface = Color(0xFFEDE0DD),
    surfaceVariant = Color(0xFF534340),
    onSurfaceVariant = Color(0xFFD8C2BC),
    outline = Color(0xFFA08C89)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = VidMatePrimary,
    onPrimary = VidMateOnPrimary,
    primaryContainer = VidMatePrimaryContainer,
    onPrimaryContainer = VidMateOnPrimaryContainer,
    secondary = VidMateSecondary,
    onSecondary = VidMateOnSecondary,
    secondaryContainer = VidMateSecondaryContainer,
    onSecondaryContainer = VidMateOnSecondaryContainer,
    background = VidMateBackground,
    onBackground = VidMateOnBackground,
    surface = VidMateSurface,
    onSurface = VidMateOnSurface,
    surfaceVariant = VidMateSurfaceVariant,
    onSurfaceVariant = VidMateOnSurfaceVariant,
    outline = VidMateOutline,
    outlineVariant = VidMateOutlineVariant
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled by default to preserve the VidMate brand design
  dynamicColor: Boolean = false,
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

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
