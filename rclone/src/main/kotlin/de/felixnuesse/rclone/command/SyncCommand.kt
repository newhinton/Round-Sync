package de.felixnuesse.rclone.command

import de.felixnuesse.rclone.Rclone
import de.felixnuesse.rclone.log.LogError
import de.felixnuesse.rclone.result.ResultType
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

abstract class SyncCommand<T>(var command: String): Command<ResultType<T>> {

    abstract fun command(): ArrayList<String>

    abstract fun parse(result: String): ResultType<T>

    override fun exec(): ResultType<T> {

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