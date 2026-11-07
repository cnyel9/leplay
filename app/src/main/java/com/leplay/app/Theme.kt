package com.leplay.app

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)





val AppRoundedHeavy = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-rounded"), weight = FontWeight.ExtraBold),
    Font(DeviceFontFamilyName("sans-serif-rounded"), weight = FontWeight.Bold),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.ExtraBold),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.Bold),
)

val RoundedHeavy: FontFamily
    get() = if (ThemeSettings.useSystemFont) FontFamily.Default else AppRoundedHeavy


















object ThemeSettings {
    private var prefs: SharedPreferences? = null

    var mode by mutableStateOf("system")
        private set
    var useSystemFont by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).also { prefs = it }
        // The color palette is gone; the accent is always the YouTube seed.
        // Drop any seed stored by older versions so nothing reads it again.
        p.edit().remove("seed").apply()
        mode = p.getString("mode", "system") ?: "system"
        useSystemFont = p.getBoolean("use_system_font", false)
    }

    fun chooseMode(value: String) {
        mode = value
        prefs?.edit()?.putString("mode", value)?.apply()
    }

    fun updateUseSystemFont(enabled: Boolean) {
        useSystemFont = enabled
        prefs?.edit()?.putBoolean("use_system_font", enabled)?.apply()
    }
}


const val SEED_YOUTUBE = "#FF0033"

private fun youTubeScheme(dark: Boolean): ColorScheme = if (!dark) lightColorScheme(
    primary = Color(0xFFE6002E), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3E8), onPrimaryContainer = Color(0xFF5C0012),
    secondary = Color(0xFF606060), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E0E0), onSecondaryContainer = Color(0xFF0F0F0F),
    tertiary = Color(0xFFE6002E), tertiaryContainer = Color(0xFFF2F2F2), onTertiaryContainer = Color(0xFF0F0F0F),
    background = Color.White, onBackground = Color(0xFF0F0F0F),
    surface = Color.White, onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFE5E5E5), onSurfaceVariant = Color(0xFF606060),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Color(0xFFF2F2F2), surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFE0E0E0),
    outline = Color(0xFF909090), outlineVariant = Color(0xFFE5E5E5),
) else darkColorScheme(
    primary = Color(0xFFFF0033), onPrimary = Color.White,
    primaryContainer = Color(0xFF4A0010), onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = Color(0xFFAAAAAA), onSecondary = Color(0xFF0F0F0F),
    secondaryContainer = Color(0xFF3F3F3F), onSecondaryContainer = Color(0xFFF1F1F1),
    tertiary = Color(0xFFFF4E45), tertiaryContainer = Color(0xFF272727), onTertiaryContainer = Color(0xFFF1F1F1),
    background = Color(0xFF0F0F0F), onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF0F0F0F), onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF272727), onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceContainerLowest = Color(0xFF0A0A0A), surfaceContainerLow = Color(0xFF181818),
    surfaceContainer = Color(0xFF212121), surfaceContainerHigh = Color(0xFF2C2C2C),
    surfaceContainerHighest = Color(0xFF3A3A3A),
    outline = Color(0xFF717171), outlineVariant = Color(0xFF303030),
)


/** The accent palette is fixed: every scheme is the YouTube scheme now. */
fun schemeFor(dark: Boolean): ColorScheme = youTubeScheme(dark)

@Composable
fun isDarkTheme(): Boolean = when (ThemeSettings.mode) {
    "dark", "amoled" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LePlayTubeTheme(content: @Composable () -> Unit) {
    val dark = isDarkTheme()
    val isAmoled = ThemeSettings.mode == "amoled"
    val baseScheme = schemeFor(dark)
    val scheme = if (isAmoled) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF141414),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF050505),
            surfaceContainer = Color(0xFF0C0C0C),
            surfaceContainerHigh = Color(0xFF141414),
            surfaceContainerHighest = Color(0xFF1E1E1E),
            onBackground = Color(0xFFF5F5F5),
            onSurface = Color(0xFFF5F5F5),
            outlineVariant = Color(0xFF1F1F1F),
        )
    } else baseScheme

    SystemBars(dark, scheme.surface)
    MaterialTheme(colorScheme = scheme, shapes = ExpressiveShapes, typography = Typography()) {
        CompositionLocalProvider(

            LocalRippleConfiguration provides null,


            LocalContentColor provides scheme.onSurface,
            content = content,
        )
    }
}






@Composable
private fun SystemBars(dark: Boolean, windowBg: Color) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = view.context as? ComponentActivity
    SideEffect {
        val window = activity?.window ?: (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        // isAppearanceLightStatusBars = true means DARK icons on LIGHT background
        // isAppearanceLightStatusBars = false means LIGHT/WHITE icons on DARK/AMOLED background
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
        if (activity != null) {
            val barStyle = if (dark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            }
            runCatching {
                activity.enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                // Re-enforce in case enableEdgeToEdge reset controller flags
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    DisposableEffect(windowBg) {
        (view.context as? Activity)?.window?.setBackgroundDrawable(ColorDrawable(windowBg.toArgb()))
        onDispose { }
    }
}
