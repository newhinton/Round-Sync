package de.felixnuesse.rclone.result

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

@Serializable
class LsJsonResult {

    var list = listOf<LsJsonResultRow>()


    fun asJSON(): JSONObject {
        return JSONObject(Json.encodeToString(this))
    }

    companion object {
        fun fromResultRowList(data: String): LsJsonResult {
            val result = LsJsonResult()
            val json = Json { ignoreUnknownKeys = true }
            result.list = json.decodeFromString<List<LsJsonResultRow>>(data)
            return result
        }


        fun fromJson(data: String): LsJsonResult {
            return Json { ignoreUnknownKeys = true }.decodeFromString(data)
        }
    }
}