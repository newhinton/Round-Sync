package ca.pkay.rcloneexplorer.workmanager

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Extension function to get LiveData value synchronously with timeout.
 */
fun <T> LiveData<T>.getOrAwait(
    time: Long = 2,
    timeUnit: TimeUnit = TimeUnit.SECONDS
): T {
    var data: T? = null
    val latch = CountDownLatch(1)
    val observer = object : Observer<T> {
        override fun onChanged(value: T) {
            data = value
            latch.countDown()
            this@getOrAwait.removeObserver(this)
        }
    }

    this.observeForever(observer)

    if (!latch.await(time, timeUnit)) {
        this.removeObserver(observer)
        throw TimeoutException("LiveData value never returned")
    }

    @Suppress("UNCHECKED_CAST")
    return data as T
}
