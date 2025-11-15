package de.felixnuesse.rclone.log

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

@Serializable
class LogState: Log() {

    init {
        internalType = InternalLogType.STATE
    }

    var stats: Stats = Stats()

    fun asJSON(): JSONObject {
        return JSONObject(Json.encodeToString(this))
    }

    companion object {
        fun fromJSON(data: String): LogState {
            return Json { ignoreUnknownKeys = true }.decodeFromString(data)
        }
    }
}
