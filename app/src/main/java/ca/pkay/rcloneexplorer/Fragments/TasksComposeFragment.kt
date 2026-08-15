package ca.pkay.rcloneexplorer.Fragments

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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.pkay.rcloneexplorer.Activities.TaskActivity
import ca.pkay.rcloneexplorer.Activities.TriggerActivity
import ca.pkay.rcloneexplorer.ui.TasksComposeScreen
import ca.pkay.rcloneexplorer.ui.viewmodel.TasksViewModel

class TasksComposeFragment : Fragment() {

    private val tasksViewModel: TasksViewModel by activityViewModels()

    companion object {
        @JvmStatic
        fun newInstance(): TasksComposeFragment {
            return TasksComposeFragment()
        }
    }

    override fun onResume() {
        super.onResume()
        tasksViewModel.refresh()
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
                    TasksComposeScreen(
                        viewModel = tasksViewModel,
                        onNewTaskClick = {
                            val intent = Intent(requireContext(), TaskActivity::class.java)
                            startActivity(intent)
                        },
                        onEditTaskClick = { task ->
                            val intent = Intent(requireContext(), TaskActivity::class.java).apply {
                                putExtra(TaskActivity.ID_EXTRA, task.id)
                            }
                            startActivity(intent)
                        },
                        onManageTriggersClick = { task ->
                            val intent = Intent(requireContext(), TriggerActivity::class.java).apply {
                                putExtra(TriggerActivity.TARGET_TASK_ID_EXTRA, task.id)
                            }
                            startActivity(intent)
                        },
                        onEditTriggerClick = { trigger ->
                            val intent = Intent(requireContext(), TriggerActivity::class.java).apply {
                                putExtra(TriggerActivity.ID_EXTRA, trigger.id)
                                putExtra(TriggerActivity.TARGET_TASK_ID_EXTRA, trigger.triggerTarget)
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}
