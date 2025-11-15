package de.felixnuesse.rclone

import de.felixnuesse.rclone.result.LsJsonResultRow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

@Serializable
class HashesObject {


    @SerialName("SHA-1") var sha1: String = ""
    var MD5: String = ""
    var DropboxHash: String = ""


    fun asJSON(): JSONObject {
        return JSONObject(Json.encodeToString(this))
    }

    companion object {
        fun fromJSON(data: String): LsJsonResultRow {
            val json = Json { ignoreUnknownKeys = true }
            return json.decodeFromString(data)
        }
    }
}