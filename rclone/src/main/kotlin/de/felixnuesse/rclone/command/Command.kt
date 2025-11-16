package de.felixnuesse.rclone.command

import de.felixnuesse.rclone.Rclone
import de.felixnuesse.rclone.log.InternalLogType
import de.felixnuesse.rclone.log.LogError
import de.felixnuesse.rclone.log.LogState
import de.felixnuesse.rclone.result.ProcessReport
import de.felixnuesse.rclone.result.ResultType
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

abstract class Command<T>(var command: String) {


    // ASYNC

    private var mProcess: Process? = null

    open fun preprocessLine(line: String): String {
        return line
    }

    open fun isValidLine(line: String?): Boolean {
        return line != null
    }

    fun stop() {
        mProcess?.destroy()
    }

    open fun exec(callback: AsyncCallback?) {
        val params = command()
        params.addAll(0, Rclone.getRcloneBase())
        mProcess = Runtime.getRuntime().exec(params.toTypedArray<String>())
        if(mProcess != null) {
            Thread {processStream(mProcess!!.inputStream, callback)}.start()
            Thread {processStream(mProcess!!.errorStream, callback)}.start()

            mProcess!!.waitFor()
        }
        callback?.onComplete()
    }

    private fun processStream(stream: InputStream, callback: AsyncCallback?) {
        BufferedReader(InputStreamReader(stream)).use { input ->
            var line: String?
            while ((input.readLine().also { line = it }) != null) {
                if(isValidLine(line)) {
                    callback?.onUpdate(preprocessLine(line!!))
                    val log = ProcessReport.fromJSON(preprocessLine(line!!))
                    when (log.internalType) {
                        InternalLogType.NONE -> {
                            callback?.onInfo(log)
                        }
                        InternalLogType.STATE -> {
                            callback?.onState((log as LogState))
                        }
                        InternalLogType.ERROR -> {
                            callback?.onError((log as LogError))
                        }
                    }

                }
            }
        }
    }


    // SYNC


    abstract fun command(): ArrayList<String>

    abstract fun parse(result: String): ResultType<T>

    fun exec(): ResultType<T> {

        val params = command()
        params.addAll(0, Rclone.getRcloneBase())
        val p = Runtime.getRuntime().exec(params.toTypedArray<String>())


        val result = StringBuilder()
        processStream(p.inputStream, result)
        processStream(p.errorStream, result)

        return parse(result.toString())
    }

    private fun processStream(stream: InputStream, result: StringBuilder) {
        BufferedReader(InputStreamReader(stream)).use { input ->
            var line: String?
            while ((input.readLine().also { line = it }) != null) {
                result.append(line).append("\n")
            }
        }
    }

    fun tryToGetError(result: String): LogError {

        try {
            var substring = result.substring(result.indexOf('{'), result.length)
            substring = substring.substring(0, substring.indexOf('}')+1)
            return LogError.fromJSON(substring)
        } catch (e: Exception) {
            e.printStackTrace()
            System.err.println(result.toString())
        }
        val error = LogError()
        error.source = "rcloneLib - Internal error (tryToGetError)"
        return error
    }
}