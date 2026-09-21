package ca.pkay.rcloneexplorer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtonSafetyTest {

    @Test
    fun detectsOrphanedDraftPattern() {
        val failure = ProtonSafety.classify(
            "HTTP error Code=2500 filename already exists\n" +
            "revision query failed Code=2501 file/folder not found"
        )
        assertEquals(ProtonFailureKind.ORPHANED_DRAFT, failure.kind)
        assertFalse(failure.retryable)
    }

    @Test
    fun detectsRefreshTokenRace() {
        val failure = ProtonSafety.classify(
            "400 Invalid refresh token (Code=10013)"
        )
        assertEquals(ProtonFailureKind.SESSION_CONFLICT, failure.kind)
        assertFalse(failure.retryable)
    }

    @Test
    fun rateLimitDoesNotAutoRetry() {
        val failure = ProtonSafety.classify(
            "429 Too many recent API requests Code=2011"
        )
        assertEquals(ProtonFailureKind.RATE_LIMITED, failure.kind)
        assertFalse(failure.retryable)
    }

    @Test
    fun temporaryNetworkFailureCanRetry() {
        val failure = ProtonSafety.classify(
            "Post https://drive.proton.me: i/o timeout"
        )
        assertEquals(ProtonFailureKind.NETWORK_TRANSIENT, failure.kind)
        assertTrue(failure.retryable)
        assertEquals(2, failure.maxAutomaticRetries)
    }

    @Test
    fun unknownFailureIsNotMisclassified() {
        val failure = ProtonSafety.classify("some unrelated rclone error")
        assertEquals(ProtonFailureKind.NONE, failure.kind)
        assertFalse(failure.retryable)
    }
}
