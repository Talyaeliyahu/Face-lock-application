package com.facelockapp.ui.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryBlueDark,
    onPrimaryContainer = PrimaryBlueLight,
    
    secondary = SecondaryCyan,
    onSecondary = Color.White,
    secondaryContainer = SecondaryCyanDark,
    onSecondaryContainer = SecondaryCyanLight,
    
    tertiary = AccentSkyBlue,
    onTertiary = Color.White,
    tertiaryContainer = AccentSkyBlueDark,
    onTertiaryContainer = AccentSkyBlueLight,
    
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFDC2626),
    onErrorContainer = Color(0xFFFEE2E2)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryBlueLight,
    onPrimaryContainer = PrimaryBlueDark,
    
    secondary = SecondaryCyan,
    onSecondary = Color.White,
    secondaryContainer = SecondaryCyanLight,
    onSecondaryContainer = SecondaryCyanDark,
    
    tertiary = AccentSkyBlue,
    onTertiary = Color.White,
    tertiaryContainer = AccentSkyBlueLight,
    onTertiaryContainer = AccentSkyBlueDark,
    
    background = LightBackground,
    onBackground = Color(0xFF0F172A),
    surface = LightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

@Composable
fun FaceLockAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    // כבוי כדי להשתמש בצבעים המותאמים אישית (תכלת בהיר)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
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
