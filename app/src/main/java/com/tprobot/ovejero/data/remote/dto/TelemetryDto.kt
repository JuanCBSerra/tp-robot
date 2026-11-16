package com.tprobot.ovejero.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Mensaje "telemetry": lo manda el robot cada ~500 ms. */
@JsonClass(generateAdapter = true)
data class TelemetryDto(
    @Json(name = "type") val type: String,
    @Json(name = "protocol_version") val protocolVersion: String?,
    @Json(name = "robot_id") val robotId: String,
    @Json(name = "seq") val sequence: Long,
    @Json(name = "timestamp") val timestamp: Long, // epoch en milisegundos
    @Json(name = "state") val state: String,
    @Json(name = "battery") val battery: BatteryDto,
    @Json(name = "mission") val mission: MissionDto?,
    @Json(name = "position") val position: PositionDto,
    @Json(name = "motion") val motion: MotionDto,
    @Json(name = "service") val service: ServiceDataDto,
    @Json(name = "link") val link: LinkDto?
)

@JsonClass(generateAdapter = true)
data class BatteryDto(
    @Json(name = "percent") val percent: Double, // 0..100
    @Json(name = "charging") val charging: Boolean
)

@JsonClass(generateAdapter = true)
data class MissionDto(
    @Json(name = "mission_id") val missionId: String,
    @Json(name = "kind") val kind: String,
    @Json(name = "status") val status: String,
    @Json(name = "progress") val progress: Double, // 0.0 .. 1.0
    @Json(name = "checkpoints_reached") val checkpointsReached: Int,
    @Json(name = "checkpoints_total") val checkpointsTotal: Int,
    @Json(name = "started_at") val startedAt: Long // epoch en milisegundos
)

@JsonClass(generateAdapter = true)
data class PositionDto(
    @Json(name = "x") val x: Double, // normalizado 0..1 sobre el plano
    @Json(name = "y") val y: Double, // normalizado 0..1 sobre el plano
    @Json(name = "zone") val zone: String?,
    @Json(name = "heading_deg") val headingDeg: Int? // 0 = norte, sentido horario
)

@JsonClass(generateAdapter = true)
data class MotionDto(
    @Json(name = "mode") val mode: String,
    @Json(name = "speed_mps") val speedMps: Double // metros por segundo
)

/** Variables propias del servicio (hacienda + clima). */
@JsonClass(generateAdapter = true)
data class ServiceDataDto(
    @Json(name = "heads_counted") val headsCounted: Int,
    @Json(name = "heads_expected") val headsExpected: Int,
    @Json(name = "heads_in_corral") val headsInCorral: Int,
    @Json(name = "temperature_c") val temperatureC: Double
)

@JsonClass(generateAdapter = true)
data class LinkDto(
    @Json(name = "signal_percent") val signalPercent: Int
)
