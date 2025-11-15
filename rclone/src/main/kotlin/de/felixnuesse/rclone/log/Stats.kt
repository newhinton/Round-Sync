package de.felixnuesse.rclone.log

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class Stats {

    var bytes = 0L
    var checks = 0L
    var deletedDirs = 0L
    var deletes = 0L
    var elapsedTime = 0.0
    var errors = 0L
    var eta: Long? = 0L
    var fatalError = false
    var retryError = false
    var speed = 0.0
    var totalBytes = 0L
    var totalChecks = 0L
    var totalTransfers = 0L
    var transferTime = 0.0
    var transfers = 0L

    companion object {
        fun fromJSON(data: String): Stats {
            return Json { ignoreUnknownKeys = true }.decodeFromString(data)
        }
    }

    override fun toString(): String {
        return "Stats(bytes=$bytes, checks=$checks, deletedDirs=$deletedDirs, deletes=$deletes, elapsedTime=$elapsedTime, errors=$errors, eta=$eta, fatalError=$fatalError, retryError=$retryError, speed=$speed, totalBytes=$totalBytes, totalChecks=$totalChecks, totalTransfers=$totalTransfers, transferTime=$transferTime, transfers=$transfers)"
    }


}
