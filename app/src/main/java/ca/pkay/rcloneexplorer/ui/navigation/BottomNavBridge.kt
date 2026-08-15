package ca.pkay.rcloneexplorer.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

object BottomNavBridge {

    @JvmStatic
    fun setupBottomNav(
        composeView: ComposeView,
        currentTab: MainNavTab,
        visible: Boolean,
        onTabSelected: (MainNavTab) -> Unit
    ) {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF3B82F6),
                    secondary = Color(0xFF38BDF8),
                    surface = Color(0xFF0F172A),
                    surfaceVariant = Color(0xFF1E293B),
                    background = Color(0xFF0B1120),
                    onBackground = Color(0xFFF8FAFC),
                    onSurface = Color(0xFFF8FAFC)
                )
            ) {
                MainBottomNavigationBar(
                    currentTab = currentTab,
                    onTabSelected = onTabSelected,
                    visible = visible
                )
            }
        }
    }
}
