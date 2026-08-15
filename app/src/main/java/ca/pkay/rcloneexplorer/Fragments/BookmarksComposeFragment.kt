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
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.Activities.MainActivity
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.ui.BookmarksComposeScreen

class BookmarksComposeFragment : Fragment() {

    companion object {
        @JvmStatic
        fun newInstance(): BookmarksComposeFragment {
            return BookmarksComposeFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rclone = Rclone(requireContext())
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val pinnedPrefKey = requireContext().getString(ca.pkay.rcloneexplorer.R.string.shared_preferences_pinned_remotes)

        fun getPinnedRemotes(): List<RemoteItem> {
            val pinnedSet = prefs.getStringSet(pinnedPrefKey, emptySet()) ?: emptySet()
            val list = rclone.remotes.filter { pinnedSet.contains(it.name) }
            return RemoteItem.prepareDisplay(requireContext(), list)
        }

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
                    BookmarksComposeScreen(
                        pinnedRemotes = getPinnedRemotes(),
                        onRemoteClick = { remote ->
                            (activity as? MainActivity)?.startRemote(remote, false)
                        },
                        onBookmarkClick = { remoteName, path ->
                            val remote = rclone.getRemoteItemFromName(remoteName)
                            if (remote != null) {
                                (activity as? MainActivity)?.startRemote(remote, false)
                            }
                        },
                        onUnpinRemote = { remote ->
                            val currentSet = prefs.getStringSet(pinnedPrefKey, emptySet())?.toMutableSet() ?: mutableSetOf()
                            currentSet.remove(remote.name)
                            prefs.edit().putStringSet(pinnedPrefKey, currentSet).apply()
                        }
                    )
                }
            }
        }
    }
}
