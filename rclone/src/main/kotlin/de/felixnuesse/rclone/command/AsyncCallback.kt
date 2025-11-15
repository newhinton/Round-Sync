package de.felixnuesse.rclone.command

import de.felixnuesse.rclone.log.Log
import de.felixnuesse.rclone.log.LogError
import de.felixnuesse.rclone.log.LogState


interface AsyncCallback {

    fun onUpdate(update: String)

    fun onInfo(update: Log)
    fun onState(update: LogState)
    fun onError(update: LogError)

    fun onComplete()


}