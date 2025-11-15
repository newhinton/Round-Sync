package de.felixnuesse.rclone.log

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Level {

    @SerialName("none") NONE,
    @SerialName("info") INFO,
    @SerialName("notice") NOTICE,
    @SerialName("error") ERROR,
    @SerialName("critical") CRITICAL
}