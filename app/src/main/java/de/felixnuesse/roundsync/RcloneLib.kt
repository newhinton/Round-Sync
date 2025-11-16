package de.felixnuesse.roundsync

import android.content.Context
import android.os.Environment
import android.util.Log
import de.felixnuesse.rclone.Rclone
import de.felixnuesse.rclone.RemoteObject
import de.felixnuesse.rclone.commands.Copy
import de.felixnuesse.rclone.commands.LsJson
import de.felixnuesse.rclone.commands.Version
import de.felixnuesse.rclone.rFile.rFile
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader


class RcloneLib(var context: Context) {
    init {
        Rclone.setBase(context.applicationInfo.nativeLibraryDir + "/librclone.so")
        Rclone.setConfig(context.getExternalFilesDir(null)?.absolutePath.toString()+"/rclone.conf")



        Log.e("TAG", Rclone.getRcloneBase().get(1))
        Log.e("TAG", Version().exec().getData())



        var remote = RemoteObject.from("/storage/emulated/0/")
        var file = rFile(remote)
        var files = file.listFiles()

        files.get(6).listFiles().forEach { Log.e("TAG", it.isDirectory().toString()) }
        files.get(7).listFiles().forEach { Log.e("TAG", it.isDirectory().toString()) }

    }
}