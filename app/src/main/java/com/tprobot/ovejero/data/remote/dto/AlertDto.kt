package com.tprobot.ovejero.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Mensaje "alert": lo manda el robot cuando detecta algo que el operador tiene que saber. */
@JsonClass(generateAdapter = true)
data class AlertDto(
    @Json(name = "type") val type: String,
    @Json(name = "alert_id") val alertId: String,
    @Json(name = "robot_id") val robotId: String,
    @Json(name = "timestamp") val timestamp: Long, // epoch en milisegundos
    @Json(name = "code") val code: String,
    @Json(name = "severity") val severity: String,
    @Json(name = "message") val message: String?,
    @Json(name = "zone") val zone: String?,
    @Json(name = "requires_decision") val requiresDecision: Boolean?
)
