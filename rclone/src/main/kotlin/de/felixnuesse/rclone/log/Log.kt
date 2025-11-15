package de.felixnuesse.rclone.log

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
open class Log {

    @Transient var internalType = InternalLogType.NONE

    var level: Level = Level.NONE
    var msg = ""
    var time = ""



    companion object {
        fun fromJSON(data: String): Log {
            return Json { ignoreUnknownKeys = true }.decodeFromString(data)
        }
    }

    override fun toString(): String {
        return "Log(internalType=$internalType, level=$level, msg='$msg', time='$time')"
    }
}