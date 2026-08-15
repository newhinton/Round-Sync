package ca.pkay.rcloneexplorer.Activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

class AboutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = if (darkTheme) {
                darkColorScheme(
                    primary = Color(0xFF3B82F6),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFF1D4ED8),
                    onPrimaryContainer = Color(0xFFDBEAFE),
                    background = Color(0xFF0B0F19),
                    onBackground = Color(0xFFF1F5F9),
                    surface = Color(0xFF111827),
                    onSurface = Color(0xFFF1F5F9),
                    surfaceVariant = Color(0xFF1F2937),
                    onSurfaceVariant = Color(0xFF9CA3AF)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF2563EB),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFDBEAFE),
                    onPrimaryContainer = Color(0xFF1E40AF),
                    background = Color(0xFFF8FAFC),
                    onBackground = Color(0xFF0F172A),
                    surface = Color(0xFFFFFFFF),
                    onSurface = Color(0xFF0F172A),
                    surfaceVariant = Color(0xFFF1F5F9),
                    onSurfaceVariant = Color(0xFF64748B)
                )
            }

            MaterialTheme(
                colorScheme = colorScheme
            ) {
                AboutScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}
