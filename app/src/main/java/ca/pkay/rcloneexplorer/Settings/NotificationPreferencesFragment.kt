package ca.pkay.rcloneexplorer.Settings

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.R
import de.felixnuesse.extract.settings.preferences.ButtonPreference
import es.dmoral.toasty.Toasty

class NotificationPreferencesFragment : PreferenceFragmentCompat() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_notification_preferences, rootKey)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        requireActivity().title = getString(R.string.notifications_pref_title)

        val notificationSettings = findPreference<Preference>("TempKeyNotificationSettings") as? ButtonPreference
        notificationSettings?.setButtonText(getString(R.string.open_notification_settings_button))
        notificationSettings?.setButtonOnClick {
            val intent = Intent().apply {
                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                putExtra("android.provider.extra.APP_PACKAGE", requireContext().packageName)
            }
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                Toasty.error(requireContext(), "Couldn't open system notification settings", Toast.LENGTH_SHORT, true).show()
            }
        }
    }
}