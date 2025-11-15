package de.felixnuesse.rclone.result

import de.felixnuesse.rclone.HashesObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

@Serializable
class LsJsonResultRow {

    var Hashes: HashesObject? = null
    var ID: String? = null
    var OrigID: String? = null
    var IsBucket = false
    var IsDir = false
    var MimeType = ""
    var ModTime = ""
    var Name = ""
    var Encrypted = ""
    var EncryptedPath = ""
    var Path = ""
    var Size = 0
    var Tier = 0


    fun asJSON(): JSONObject {
        return JSONObject(Json.encodeToString(this))
    }

    override fun toString(): String {
        return "LsJsonResultRow(Hashes=$Hashes, ID=$ID, OrigID=$OrigID, IsBucket=$IsBucket, IsDir=$IsDir, MimeType='$MimeType', ModTime='$ModTime', Name='$Name', Encrypted='$Encrypted', EncryptedPath='$EncryptedPath', Path='$Path', Size=$Size, Tier=$Tier)"
    }

    companion object {
        fun fromJSON(data: String): LsJsonResultRow {
            val json = Json { ignoreUnknownKeys = true }
            return json.decodeFromString(data)
        }
    }


}