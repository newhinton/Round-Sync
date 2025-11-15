package de.felixnuesse.roundsync

import android.content.Context
import android.util.Log
import de.felixnuesse.rclone.Rclone
import de.felixnuesse.rclone.commands.Version
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader


class RcloneLib(var context: Context) {
    init {
        Rclone.setBase(context.applicationInfo.nativeLibraryDir + "/librclone.so")
        Log.e("TAG", Version().exec().getData())
    }
}