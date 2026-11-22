package computer.helium.mobile

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

private val HeliumLight = lightColorScheme(
    primary = Color(0xFF4152B8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E2FF),
    onPrimaryContainer = Color(0xFF111A5A),
    secondary = Color(0xFF5C5F78),
    background = Color(0xFFF8F8FC),
    surface = Color(0xFFF8F8FC),
    surfaceContainer = Color(0xFFEDEEF5),
    surfaceContainerHigh = Color(0xFFE5E6EF),
    surfaceContainerLow = Color(0xFFF1F2F8),
)

private val HeliumDark = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF20296A),
    primaryContainer = Color(0xFF384285),
    onPrimaryContainer = Color(0xFFE0E2FF),
    secondary = Color(0xFFC4C5DD),
    background = Color(0xFF11121B),
    surface = Color(0xFF11121B),
    surfaceContainer = Color(0xFF1C1D29),
    surfaceContainerHigh = Color(0xFF262735),
    surfaceContainerLow = Color(0xFF171822),
)

@Composable
fun HeliumTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> HeliumDark
        else -> HeliumLight
    }

    MaterialTheme(colorScheme = colors, content = content)
}
