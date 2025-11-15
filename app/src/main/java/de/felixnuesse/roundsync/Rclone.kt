package de.felixnuesse.roundsync

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader


class Rclone(var context: Context) {

    init {

        var rclone = context.applicationInfo.nativeLibraryDir + "/librclone.so"

        System.err.println("ABC")
        File(context.applicationInfo.nativeLibraryDir).listFiles().forEach {
            System.err.println(it)
        }

        tryy(rclone)
    }

    fun tryy(rclone: String) {

        try {
            val r = Runtime.getRuntime()
            val process = r.exec(rclone)

            val stdoutString = convertInputStreamToString(process.inputStream)
            val stderrString = convertInputStreamToString(process.errorStream)

            println(stdoutString)
            println(stderrString)

        } catch (e: IOException) {
            Log.e("rclone", "Error executing rclone!" + e.message)
            throw IOException("Error executing rclone!" + e.message)
        }
    }


    fun convertInputStreamToString(inputStream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val sb = StringBuilder()
        var line: String?

        try {
            while (true) {
                line = reader.readLine() ?: break
                sb.append(line).append("\n")
            }
        } catch (e: Exception) {
            Log.e("InputStreamError", "Error reading InputStream", e)
        } finally {
            try {
                reader.close()
            } catch (e: Exception) {
                Log.e("InputStreamError", "Error closing InputStream", e)
            }
        }

        return sb.toString()
    }


}