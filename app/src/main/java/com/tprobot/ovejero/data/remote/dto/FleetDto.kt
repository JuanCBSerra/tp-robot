package com.tprobot.ovejero.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FleetResponseDto(
    @Json(name = "robots") val robots: List<RobotSummaryDto>
)

@JsonClass(generateAdapter = true)
data class RobotSummaryDto(
    @Json(name = "robot_id") val robotId: String,
    @Json(name = "name") val name: String,
    @Json(name = "model") val model: String,
    @Json(name = "location") val location: String?,
    @Json(name = "status") val status: String,
    @Json(name = "battery_percent") val batteryPercent: Double?,
    @Json(name = "note") val note: String?
)
