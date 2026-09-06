package ca.pkay.rcloneexplorer.RemoteConfig

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.os.AsyncTask
import android.os.Build
import android.text.InputType
import android.widget.Toast
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.util.FLog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import es.dmoral.toasty.Toasty
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SuppressLint("StaticFieldLeak")
class InternxtReauth(
    private val context: Context,
    private val rclone: Rclone,
    private val remoteName: String
) : AsyncTask<Void?, Void?, Boolean>() {

    private var progressDialog: ProgressDialog? = null
    private var errorMessage: String? = null

    companion object {
        private const val TAG = "InternxtReauth"
        private const val CANCEL = "CANCEL"
        private const val TEMPORARY = "TEMPORARY"
        private const val PERMANENT = "PERMANENT"
    }

    override fun onPreExecute() {
        progressDialog = ProgressDialog(context).apply {
            setMessage(context.getString(R.string.internxt_reauth_progress))
            setCancelable(false)
            show()
        }
    }

    override fun doInBackground(vararg params: Void?): Boolean {
        val authMethod = getAuthPreferenceFromUser()
        if (authMethod == CANCEL) {
            errorMessage = context.getString(R.string.cancelled)
            return false
        }

        if (authMethod == PERMANENT) {
            val totpSecret = getTOTPSecretFromUser()
            if (totpSecret.isEmpty()) {
                errorMessage = context.getString(R.string.cancelled)
                return false
            }
            if (!updateTOTPSecret(totpSecret)) {
                return false
            }
        }

        return runConfigReconnect()
    }

    private fun updateTOTPSecret(totpSecret: String): Boolean {
        val options = arrayListOf(remoteName, "totp_secret", totpSecret, "--obscure")
        val proc = rclone.config("update", options)
        if (proc == null) {
            errorMessage = context.getString(R.string.error_creating_remote)
            return false
        }

        val outputReader = proc.drainOutput()
        val errorReader = proc.drainError()
        val completed = proc.waitForQuietly(1, TimeUnit.MINUTES)
        outputReader.join(1000)
        errorReader.join(1000)
        if (!completed || proc.exitValue() != 0) {
            errorMessage = context.getString(R.string.error_creating_remote)
            FLog.e(TAG, "Failed to update Internxt TOTP secret")
            return false
        }
        return true
    }

    private fun runConfigReconnect(): Boolean {
        var state = ""
        var result = ""

        while (true) {
            val options = arrayListOf(remoteName, "--non-interactive", "--no-obscure")
            if (state.isNotEmpty()) {
                options.add("--continue")
                options.add("--state")
                options.add(state)
                options.add("--result")
                options.add(result)
            }

            val proc = rclone.config("update", options)
            if (proc == null) {
                errorMessage = context.getString(R.string.error_creating_remote)
                return false
            }

            val jsonOutput = StringBuilder()
            // Capture stderr so we can surface a real failure reason instead of a
            // generic "error" toast. Lines that may carry secrets (tokens,
            // passwords, Authorization headers) are filtered out and never logged
            // or shown to the user.
            val stderrCapture = StringBuilder()
            val outputReader = Thread {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { jsonOutput.append(it).append('\n') }
                }
            }
            val errorReader = Thread {
                proc.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (!containsSecret(line)) {
                            stderrCapture.append(line).append('\n')
                        }
                    }
                }
            }

            outputReader.start()
            errorReader.start()

            val completed = proc.waitForQuietly(2, TimeUnit.MINUTES)
            outputReader.join(1000)
            errorReader.join(1000)

            val stderrSummary = stderrCapture.toString().trim()

            if (!completed) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) proc.destroyForcibly() else proc.destroy()
                errorMessage = describeError("Internxt reauth timed out", stderrSummary)
                FLog.e(TAG, "Internxt reauth timed out")
                return false
            }

            if (proc.exitValue() != 0) {
                errorMessage = describeError("Internxt reauth failed (exit ${proc.exitValue()})", stderrSummary)
                FLog.e(TAG, "Internxt reauth failed. stderr: $stderrSummary")
                return false
            }

            val jsonStr = jsonOutput.toString().trim()
            if (jsonStr.isEmpty()) {
                return true
            }

            try {
                val json = JSONObject(jsonStr)
                state = json.optString("State", "")
                if (state.isEmpty()) {
                    return true
                }

                val optionObj = json.optJSONObject("Option")
                result = if (optionObj != null &&
                    optionObj.optString("Help", "").contains("Two-factor authentication code", ignoreCase = true)
                ) {
                    getTwoFactorCodeFromUser()
                } else {
                    ""
                }
            } catch (e: Exception) {
                errorMessage = describeError("Failed to parse Internxt reauth state", stderrSummary)
                FLog.e(TAG, "Failed to parse Internxt reauth state. stdout=$jsonStr stderr=$stderrSummary", e)
                return false
            }
        }
    }

    private fun getAuthPreferenceFromUser(): String {
        val latch = CountDownLatch(1)
        var choice = CANCEL

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val options = arrayOf(
                context.getString(R.string.internxt_auth_option_temp),
                context.getString(R.string.internxt_auth_option_perm)
            )

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.internxt_reauth_title)
                .setMessage(R.string.internxt_reauth_message)
                .setItems(options) { _, which ->
                    choice = if (which == 0) TEMPORARY else PERMANENT
                    latch.countDown()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> latch.countDown() }
                .setCancelable(false)
                .show()
        }

        latch.awaitQuietly(5, TimeUnit.MINUTES)
        return choice
    }

    private fun getTOTPSecretFromUser(): String {
        val latch = CountDownLatch(1)
        var secret = ""

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val inputLayout = TextInputLayout(context)
            inputLayout.hint = context.getString(R.string.internxt_totp_secret_hint)

            val input = TextInputEditText(context)
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            inputLayout.addView(input)

            val padding = (16 * context.resources.displayMetrics.density).toInt()
            inputLayout.setPadding(padding, 0, padding, 0)

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.internxt_totp_secret_title)
                .setMessage(R.string.internxt_totp_secret_message)
                .setView(inputLayout)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    secret = input.text?.toString()?.trim() ?: ""
                    latch.countDown()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> latch.countDown() }
                .setCancelable(false)
                .show()
        }

        latch.awaitQuietly(5, TimeUnit.MINUTES)
        return secret
    }

    private fun getTwoFactorCodeFromUser(): String {
        val latch = CountDownLatch(1)
        var code = ""

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val inputLayout = TextInputLayout(context)
            inputLayout.hint = context.getString(R.string.internxt_2fa_hint)

            val input = TextInputEditText(context)
            input.inputType = InputType.TYPE_CLASS_NUMBER
            inputLayout.addView(input)

            val padding = (16 * context.resources.displayMetrics.density).toInt()
            inputLayout.setPadding(padding, 0, padding, 0)

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.internxt_2fa_title)
                .setMessage(R.string.internxt_2fa_message)
                .setView(inputLayout)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    code = input.text?.toString()?.trim() ?: ""
                    latch.countDown()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> latch.countDown() }
                .setCancelable(false)
                .show()
        }

        latch.awaitQuietly(5, TimeUnit.MINUTES)
        return code
    }

    override fun onPostExecute(success: Boolean) {
        progressDialog?.dismiss()
        if (success) {
            Toasty.success(context, context.getString(R.string.internxt_reauth_success), Toast.LENGTH_SHORT, true).show()
        } else if (errorMessage != context.getString(R.string.cancelled)) {
            Toasty.error(
                context,
                errorMessage ?: context.getString(R.string.error_creating_remote),
                Toast.LENGTH_SHORT,
                true
            ).show()
        }
    }
}

/**
 * Heuristic check for lines that may carry credentials. Used to keep tokens,
 * passwords and Authorization headers out of both the user-facing error toast
 * and the logcat output.
 */
private fun containsSecret(line: String): Boolean {
    val lower = line.lowercase()
    return lower.contains("token") || lower.contains("password") ||
        lower.contains("pass") || lower.contains("secret") ||
        lower.contains("bearer") || lower.contains("authorization") ||
        lower.contains("mnemonic") || lower.contains("credential")
}

/**
 * Builds a user-facing error message from a short reason and the (already
 * secret-filtered) rclone stderr. Caps the stderr excerpt so the toast stays
 * readable; the full filtered stderr is logged separately.
 */
private fun describeError(reason: String, stderrSummary: String): String {
    if (stderrSummary.isEmpty()) {
        return reason
    }
    val excerpt = stderrSummary.lineSequence()
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .take(160)
    return "$reason: $excerpt"
}

private fun Process.waitForQuietly(timeout: Long, unit: TimeUnit): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            waitFor(timeout, unit)
        } else {
            val deadlineMs = System.currentTimeMillis() + unit.toMillis(timeout)
            while (System.currentTimeMillis() < deadlineMs) {
                try {
                    exitValue()
                    return true
                } catch (e: IllegalThreadStateException) {
                    Thread.sleep(50)
                }
            }
            false
        }
    } catch (e: InterruptedException) {
        destroy()
        false
    }
}

private fun Process.drainOutput(): Thread {
    return Thread {
        inputStream.bufferedReader().useLines { lines ->
            lines.forEach { }
        }
    }.also { it.start() }
}

private fun Process.drainError(): Thread {
    return Thread {
        errorStream.bufferedReader().useLines { lines ->
            lines.forEach { }
        }
    }.also { it.start() }
}

private fun CountDownLatch.awaitQuietly(timeout: Long, unit: TimeUnit): Boolean {
    return try {
        await(timeout, unit)
    } catch (e: InterruptedException) {
        false
    }
}
