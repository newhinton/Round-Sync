package de.felixnuesse.rclone.log

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

@Serializable
class LogError: Log() {

    init {
        internalType = InternalLogType.ERROR
    }

    var source = ""

    fun asJSON(): JSONObject {
        return JSONObject(Json.encodeToString(this))
    }

    companion object {
        fun fromJSON(data: String): LogError {
            return Json { ignoreUnknownKeys = true }.decodeFromString(data)
        }
    }

    override fun toString(): String {
        return "LogError(source='$source', ${super.toString()})"
    }
}