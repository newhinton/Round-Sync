package de.felixnuesse.roundsync

import android.content.Context
import android.os.Environment
import android.util.Log
import de.felixnuesse.rclone.Rclone
import de.felixnuesse.rclone.RemoteObject
import de.felixnuesse.rclone.commands.LsJson
import de.felixnuesse.rclone.commands.Version
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



        var remote = RemoteObject.from("/storage/emulated/0")
        var lsdir = LsJson(remote).exec()




        if(lsdir.isSuccessful()) {
            Log.e("TAG", lsdir.getData().toString())
        } else {
            Log.e("TAG", lsdir.getError().toString())
        }
    }
}