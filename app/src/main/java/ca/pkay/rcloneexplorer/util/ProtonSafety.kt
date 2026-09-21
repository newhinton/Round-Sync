package ca.pkay.rcloneexplorer.util

import ca.pkay.rcloneexplorer.Items.RemoteItem
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock

enum class ProtonFailureKind {
    NONE,
    NETWORK_TRANSIENT,
    RATE_LIMITED,
    SESSION_CONFLICT,
    ORPHANED_DRAFT
}

data class ProtonFailure(
    val kind: ProtonFailureKind,
    val userMessage: String,
    val maxAutomaticRetries: Int,
    val suppressFailureFollowup: Boolean
) {
    val retryable: Boolean
        get() = maxAutomaticRetries > 0
}

/**
 * App-side safety policy for Proton Drive.
 *
 * This deliberately does not try to repair Proton's cryptographic state or
 * purge server-side objects. It serializes app-owned Proton operations and
 * classifies known failure patterns so workers can fail/retry safely.
 */
object ProtonSafety {
    private val operationLock = ReentrantLock(true)

    val NONE = ProtonFailure(
        ProtonFailureKind.NONE,
        "",
        0,
        false
    )

    fun isProton(remote: RemoteItem?): Boolean {
        return remote?.typeReadable?.equals("protondrive", ignoreCase = true) == true
    }

    fun acquireIfProton(remote: RemoteItem?): Boolean {
        if (!isProton(remote)) return false
        operationLock.lock()
        return true
    }

    fun releaseIfHeld(held: Boolean) {
        if (held) {
            operationLock.unlock()
        }
    }

    fun classify(rawLog: String): ProtonFailure {
        if (rawLog.isBlank()) return NONE
        val log = rawLog.lowercase(Locale.ROOT)

        // Incomplete upload/draft: Proton can report that the name exists and
        // then report that the corresponding file/revision cannot be found.
        val has2500 = hasCode(log, 2500) || log.contains("filename already exists") || log.contains("file already exists")
        val has2501 = hasCode(log, 2501) || log.contains("file/folder not found")
        if (has2500 && has2501) {
            return ProtonFailure(
                ProtonFailureKind.ORPHANED_DRAFT,
                "Proton Drive appears to have left an incomplete upload draft. Automatic retries were stopped to protect the destination. Local data was not deleted. Retry manually later; do not purge the parent folder or empty Proton trash automatically.",
                0,
                true
            )
        }

        if (hasCode(log, 2011) || log.contains("status=429") || log.contains("status 429") ||
            log.contains("too many recent api requests") || log.contains("rate limit")) {
            return ProtonFailure(
                ProtonFailureKind.RATE_LIMITED,
                "Proton Drive rate-limited the request. Automatic retries were stopped so the app does not extend the rate limit. Existing credentials are preserved; retry later.",
                0,
                true
            )
        }

        if (hasCode(log, 10013) || log.contains("invalid refresh token")) {
            return ProtonFailure(
                ProtonFailureKind.SESSION_CONFLICT,
                "Proton Drive rejected a rotated refresh token. Automatic retries were stopped to avoid repeated logins and further rate limiting. Wait for any other Proton/rclone operation to finish, then retry; reconnect only if the error persists.",
                0,
                true
            )
        }

        if (
            log.contains("i/o timeout") ||
            log.contains("context deadline exceeded") ||
            log.contains("connection reset") ||
            log.contains("connection refused") ||
            log.contains("network is unreachable") ||
            log.contains("no such host") ||
            log.contains("temporary failure in name resolution") ||
            log.contains("tls handshake timeout") ||
            log.contains("unexpected eof") ||
            log.contains("status=500") ||
            log.contains("status 500") ||
            log.contains("status=502") ||
            log.contains("status 502")
        ) {
            return ProtonFailure(
                ProtonFailureKind.NETWORK_TRANSIENT,
                "A temporary Proton/network error interrupted the operation. The task will retry with exponential backoff while keeping the existing Proton session.",
                2,
                true
            )
        }

        return NONE
    }

    private fun hasCode(log: String, code: Int): Boolean {
        return log.contains("code=$code") ||
            log.contains("code $code") ||
            log.contains("\"code\":$code") ||
            log.contains("\"code\": $code")
    }
}
