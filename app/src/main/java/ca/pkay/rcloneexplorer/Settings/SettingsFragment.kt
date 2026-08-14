package ca.pkay.rcloneexplorer.Settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import ca.pkay.rcloneexplorer.Activities.MainActivity
import ca.pkay.rcloneexplorer.R

class SettingsFragment : Fragment() {

    companion object {
        const val GENERAL_SETTINGS = 1
        const val FILE_ACCESS_SETTINGS = 2
        const val LOOK_AND_FEEL_SETTINGS = 3
        const val LOGGING_SETTINGS = 4
        const val NOTIFICATION_SETTINGS = 5

        @JvmStatic
        fun newInstance(): SettingsFragment {
            return SettingsFragment()
        }
    }

    private var clickListener: OnSettingCategorySelectedListener? = null

    interface OnSettingCategorySelectedListener {
        fun onSettingCategoryClicked(category: Int)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnSettingCategorySelectedListener) {
            clickListener = context
        } else {
            throw RuntimeException("$context must implement listener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
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
                    SettingsScreen(
                        onCategoryClick = { category ->
                            clickListener?.onSettingCategoryClicked(category)
                        },
                        onImportClick = {
                            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                                action = MainActivity.MAIN_ACTIVITY_START_IMPORT
                            }
                            startActivity(intent)
                        },
                        onExportClick = {
                            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                                action = MainActivity.MAIN_ACTIVITY_START_EXPORT
                            }
                            startActivity(intent)
                        },
                        onBackPressed = {
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        }
                    )
                }
            }
        }
    }
}
