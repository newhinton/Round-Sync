package ca.pkay.rcloneexplorer.workmanager

import androidx.work.ListenableWorker
import ca.pkay.rcloneexplorer.workmanager.SyncWorker.FAILURE_REASON
import ca.pkay.rcloneexplorer.workmanager.SyncWorker.Companion.processFailureReason
import ca.pkay.rcloneexplorer.workmanager.SyncWorker.Companion.workResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncWorkerTest {

    @Test
    fun successfulProcessExitPreservesNoFailure() {
        assertEquals(
            FAILURE_REASON.NO_FAILURE,
            processFailureReason(0, FAILURE_REASON.NO_FAILURE)
        )
    }

    @Test
    fun nonzeroProcessExitBecomesRcloneFailure() {
        assertEquals(
            FAILURE_REASON.RCLONE_ERROR,
            processFailureReason(2, FAILURE_REASON.NO_FAILURE)
        )
    }

    @Test
    fun missingProcessExitBecomesRcloneFailure() {
        assertEquals(
            FAILURE_REASON.RCLONE_ERROR,
            processFailureReason(null, FAILURE_REASON.NO_FAILURE)
        )
    }

    @Test
    fun processExitDoesNotMaskEarlierFailure() {
        assertEquals(
            FAILURE_REASON.CANCELLED,
            processFailureReason(0, FAILURE_REASON.CANCELLED)
        )
        assertEquals(
            FAILURE_REASON.CONNECTIVITY_CHANGED,
            processFailureReason(2, FAILURE_REASON.CONNECTIVITY_CHANGED)
        )
    }

    @Test
    fun workResultMatchesFailureReason() {
        assertEquals(ListenableWorker.Result.success(), workResult(FAILURE_REASON.NO_FAILURE))
        FAILURE_REASON.entries.forEach { reason ->
            if (reason != FAILURE_REASON.NO_FAILURE) {
                assertEquals(
                    "Expected WorkManager failure for $reason",
                    ListenableWorker.Result.failure(),
                    workResult(reason)
                )
            }
        }
    }
}
