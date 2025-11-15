package de.felixnuesse.rclone.result

import de.felixnuesse.rclone.log.Log
import de.felixnuesse.rclone.log.LogError
import de.felixnuesse.rclone.log.LogState
import org.json.JSONObject

class ProcessReport {

    companion object {
        fun fromJSON(data: String): Log {
            val jsonObject = JSONObject(data)

            if(!jsonObject.has("stats")) {
                return LogError.fromJSON(data)
            }
            if(jsonObject.has("stats")) {
                return LogState.fromJSON(data)
            }

            System.err.println("Could not parse correct logtype, fall back to generic.")
            return LogError.fromJSON(data)
        }
    }

}