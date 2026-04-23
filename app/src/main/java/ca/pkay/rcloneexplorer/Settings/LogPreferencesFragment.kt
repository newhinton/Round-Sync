package ca.pkay.rcloneexplorer.Settings

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.util.FLog
import de.schuelken.cloudbridge.extensions.tag
import de.schuelken.cloudbridge.settings.preferences.ButtonPreference
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern


class LogPreferencesFragment : PreferenceFragmentCompat() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_logging_preferences, rootKey)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        requireActivity().title = getString(R.string.logging_settings_header)

        val sigkill = findPreference<Preference>("TempKeySigquit") as ButtonPreference
        sigkill.setButtonText(getString(R.string.pref_send_sigquit_button))
        sigkill.setButtonOnClick {
            sigquitAll()
        }

    }


    private fun sigquitAll() {
        Toast.makeText(context, "Round Sync: Stopping everything", Toast.LENGTH_LONG).show()
        try {
            val runtime = Runtime.getRuntime()
            val process = runtime.exec("ps")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            val output = StringBuilder()
            while ((reader.readLine().also { line = it }) != null) {
                output.append('\n')
                output.append(line)
            }

            process.waitFor()

            val regex = "\\s+(\\d+)\\s+\\d+\\s+\\d+\\s+.+librclone.+$"
            val pattern = Pattern.compile(regex, Pattern.MULTILINE)
            val matcher = pattern.matcher(output.toString())

            while (matcher.find()) {
                for (i in 1..matcher.groupCount()) {
                    val pidMatch = matcher.group(i) ?: continue
                    val pid = pidMatch.toInt()
                    FLog.i(tag(), "SIGQUIT to process pid=%s", pid)
                    Process.sendSignal(pid, Process.SIGNAL_QUIT)
                }
            }
            Process.killProcess(Process.myPid())
        } catch (e: IOException) {
            FLog.e(tag(), "Error executing shell commands", e)
        } catch (e: InterruptedException) {
            FLog.e(tag(), "Error executing shell commands", e)
        }
    }
}