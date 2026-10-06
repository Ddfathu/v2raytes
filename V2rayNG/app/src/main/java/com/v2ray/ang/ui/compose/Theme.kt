package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// === TEMA PINK UNTUK MODE TERANG (LIGHT MODE) ===
private val LightColor = lightColorScheme(
    primary = Color(0xFFE91E63), // Pink Utama (Material Pink 500)
    onPrimary = Color(0xFFFFFFFF), // Teks Putih di atas pink
    primaryContainer = Color(0xFFFCE4EC), // Pink Sangat Muda (Pink 50)
    onPrimaryContainer = Color(0xFF880E4F), // Pink Gelap untuk teks di container
    secondary = Color(0xFFFF4081), // Pink Aksen Terang (Pink A200)
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF8BBD0), // Soft Pink
    onSecondaryContainer = Color(0xFF4A0021),
    tertiary = Color(0xFFD81B60), // Deep Pink
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF3B0721),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF0F5), // Background LavenderBlush / Pink sangat halus
    onBackground = Color(0xFF2C151B),
    surface = Color(0xFFFFFFFF), // Permukaan kartu/dialog putih bersih
    onSurface = Color(0xFF2C151B),
    surfaceVariant = Color(0xFFF4D8E2), // Kartu varian pink pastel
    onSurfaceVariant = Color(0xFF514347),
    outline = Color(0xFF837377),
    outlineVariant = Color(0xFFD5C2C6),
    inverseSurface = Color(0xFF382E30),
    inverseOnSurface = Color(0xFFFEEDEF),
    inversePrimary = Color(0xFFFFB0C8),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFFE91E63),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF8F9),
    surfaceContainer = Color(0xFFFCF0F2),
    surfaceContainerHigh = Color(0xFFF6EAEC),
    surfaceContainerHighest = Color(0xFFF0E4E6),
)

// === TEMA PINK UNTUK MODE GELAP (DARK MODE / AMETHYST PINK) ===
private val DarkColor = darkColorScheme(
    primary = Color(0xFFFF4081), // Pink Terang Menyala (Pink A200)
    onPrimary = Color(0xFF5C0028),
    primaryContainer = Color(0xFF880E4F), // Pink Gelap Elegan
    onPrimaryContainer = Color(0xFFFFD8E4),
    secondary = Color(0xFFFF80AB), // Pink Pastel Cerah
    onSecondary = Color(0xFF580031),
    secondaryContainer = Color(0xFF7A0046),
    onSecondaryContainer = Color(0xFFFFD8E7),
    tertiary = Color(0xFFFF6492),
    onTertiary = Color(0xFF5B002A),
    tertiaryContainer = Color(0xFF7E003D),
    onTertiaryContainer = Color(0xFFFFD8E5),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1D1015), // Background Gelap dengan hint Pink Deep
    onBackground = Color(0xFFF0DFE2),
    surface = Color(0xFF24151B),
    onSurface = Color(0xFFF0DFE2),
    surfaceVariant = Color(0xFF514347),
    onSurfaceVariant = Color(0xFFD5C2C6),
    outline = Color(0xFF9E8C90),
    outlineVariant = Color(0xFF514347),
    inverseSurface = Color(0xFFF0DFE2),
    inverseOnSurface = Color(0xFF382E30),
    inversePrimary = Color(0xFFE91E63),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFFFF4081),
    surfaceContainerLowest = Color(0xFF180B10),
    surfaceContainerLow = Color(0xFF211319),
    surfaceContainer = Color(0xFF25171D),
    surfaceContainerHigh = Color(0xFF302127),
    surfaceContainerHighest = Color(0xFF3B2B32),
)

// Semantic Colors (Tombol & Status)
val colorPing = Color(0xFF00C853) // Green
val colorPingRed = Color(0xFFFF1744) // Merah bila ping timeout
val colorConfigType = Color(0xFFE91E63) // Label config Pink
val colorFabActive = Color(0xFFE91E63) // Tombol Konek (FAB) Pink Aktif
val colorFabInactiveLight = Color(0xFFBDBDBD)
val colorFabInactiveDark = Color(0xFF616161)
val dividerColorLight = Color(0xFFF48FB1)
val dividerColorDark = Color(0xFF4A1528)

// Toast Colors
val toastNormalBgLight = Color(0xD94A1528)
val toastNormalBgDark = Color(0xD92E0818)
val toastSuccessBg = Color(0xD9388E3C)
val toastErrorBg = Color(0xD9D50000)
val toastInfoBg = Color(0xD9C2185B)
val toastIconCircleBg = Color(0x33FFFFFF)
val toastTextColor = Color.White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // Default dinonaktifkan (false) agar tema custom pink tidak tertimpa wallpaper HP
    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    // Dipaksa menggunakan palet warna pink kita
    val colorScheme = if (darkTheme) DarkColor else LightColor
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
