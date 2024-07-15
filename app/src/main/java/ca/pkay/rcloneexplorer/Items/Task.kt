package ca.pkay.rcloneexplorer.Items

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import org.json.JSONObject

@Serializable
data class Task(var id: Long) {
    // Alternatives are kept for backwards compatibility with old, manual parser
    @JsonNames("name") var title = ""
    var remoteId = ""
    var remoteType = 0
    var remotePath = ""
    var localPath = ""
    @JsonNames("syncDirection") var direction = 0
    var md5sum = TASK_MD5SUM_DEFAULT
    var wifionly = TASK_WIFIONLY_DEFAULT
    var filterId: Long? = null
    var deleteExcluded = false;

    override fun toString(): String {
        return "$title: $remoteId: $remoteType: $remotePath: $localPath: $direction"
    }

    companion object {
        var TABLE_NAME = "task_table"
        var COLUMN_NAME_ID = "task_id"
        var COLUMN_NAME_TITLE = "task_title"
        var COLUMN_NAME_REMOTE_ID = "task_remote_id"
        var COLUMN_NAME_REMOTE_TYPE = "task_remote_type"
        var COLUMN_NAME_REMOTE_PATH = "task_remote_path"
        var COLUMN_NAME_LOCAL_PATH = "task_local_path"
        var COLUMN_NAME_SYNC_DIRECTION = "task_direction"
        var COLUMN_NAME_MD5SUM = "task_use_md5sum"
        var COLUMN_NAME_WIFI_ONLY = "task_use_only_wifi"
        var COLUMN_NAME_FILTER_ID = "task_filter_id"
        var COLUMN_NAME_DELETE_EXCLUDED = "task_delete_excluded"

        const val TASK_MD5SUM_DEFAULT = false
        const val TASK_WIFIONLY_DEFAULT = false

        fun fromString(json: String): Task {
            return Json.decodeFromString(json)
        }
    }

    fun asJSON(): JSONObject {
        return JSONObject(Json.encodeToString(this))
    }
}