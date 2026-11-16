package com.tprobot.ovejero.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Mensaje "command": lo manda la app al robot. */
@JsonClass(generateAdapter = true)
data class CommandRequestDto(
    @Json(name = "type") val type: String,
    @Json(name = "command_id") val commandId: String,
    @Json(name = "robot_id") val robotId: String,
    @Json(name = "timestamp") val timestamp: Long, // epoch en milisegundos
    @Json(name = "command") val command: String,
    @Json(name = "params") val params: CommandParamsDto?
)

@JsonClass(generateAdapter = true)
data class CommandParamsDto(
    @Json(name = "mission_kind") val missionKind: String? = null,
    @Json(name = "alert_id") val alertId: String? = null
)

/** Mensaje "command_ack": respuesta del robot a una orden. */
@JsonClass(generateAdapter = true)
data class CommandAckDto(
    @Json(name = "type") val type: String,
    @Json(name = "command_id") val commandId: String,
    @Json(name = "robot_id") val robotId: String,
    @Json(name = "timestamp") val timestamp: Long, // epoch en milisegundos
    @Json(name = "status") val status: String,
    @Json(name = "reason") val reason: String?
)
