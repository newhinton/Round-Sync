package de.felixnuesse.rclone.result

import de.felixnuesse.rclone.log.LogError

open class ResultType<T>() {

    private var data: T? = null
    private var success: Boolean? = null
    private var error: LogError? = null

    fun isSuccessful(): Boolean {
        return success == true
    }

    fun getData(): T {
        if (data != null) {
            return data!!
        }
        throw NullPointerException("The requested data is null. Success: ${isSuccessful()}")
    }

    fun getError(): LogError {
        if (error != null) {
            return error!!
        }
        throw NullPointerException("The requested error is null. Success: ${isSuccessful()}")
    }

    private fun setError(error: LogError) {
        this.error = error
        this.success = false
    }


    private fun setData(data: T) {
        this.data = data
        this.success = true
    }



    companion object {
        fun <T> success(data: T): ResultType<T> {
            val result = ResultType<T>()
            result.setData(data)
            return result
        }

        fun <T> failure(error: LogError): ResultType<T> {
            val result = ResultType<T>()
            result.setError(error)
            return result
        }
    }

}