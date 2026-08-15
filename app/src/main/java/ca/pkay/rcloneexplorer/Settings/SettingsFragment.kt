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
import ca.pkay.rcloneexplorer.Activities.AboutActivity
import ca.pkay.rcloneexplorer.Activities.MainActivity
import ca.pkay.rcloneexplorer.Activities.SettingsActivity
import ca.pkay.rcloneexplorer.R

class SettingsFragment : Fragment() {

    companion object {
        const val GENERAL_SETTINGS = 1
        const val FILE_ACCESS_SETTINGS = 2
        const val LOOK_AND_FEEL_SETTINGS = 3
        const val LOGGING_SETTINGS = 4
        const val NOTIFICATION_SETTINGS = 5

        @JvmStatic
        fun newInstance(showBackButton: Boolean = true): SettingsFragment {
            val fragment = SettingsFragment()
            val args = Bundle()
            args.putBoolean("SHOW_BACK", showBackButton)
            fragment.arguments = args
            return fragment
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
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val showBackButton = arguments?.getBoolean("SHOW_BACK", true) ?: true

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
                        showBackButton = showBackButton,
                        onCategoryClick = { category ->
                            if (clickListener != null) {
                                clickListener?.onSettingCategoryClicked(category)
                            } else {
                                val intent = Intent(requireContext(), SettingsActivity::class.java).apply {
                                    putExtra("START_CATEGORY", category)
                                }
                                startActivity(intent)
                            }
                        },
                        onImportClick = {
                            val act = activity
                            if (act is MainActivity) {
                                act.importConfigFile()
                            } else {
                                val intent = Intent(requireContext(), MainActivity::class.java).apply {
                                    action = MainActivity.MAIN_ACTIVITY_START_IMPORT
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                startActivity(intent)
                            }
                        },
                        onExportClick = {
                            val act = activity
                            if (act is MainActivity) {
                                act.exportConfigFile()
                            } else {
                                val intent = Intent(requireContext(), MainActivity::class.java).apply {
                                    action = MainActivity.MAIN_ACTIVITY_START_EXPORT
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                startActivity(intent)
                            }
                        },
                        onAboutClick = {
                            val intent = Intent(requireContext(), AboutActivity::class.java)
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
