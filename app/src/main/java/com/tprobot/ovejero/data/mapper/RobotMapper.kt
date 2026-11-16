package com.tprobot.ovejero.data.mapper

import com.tprobot.ovejero.data.remote.RobotProtocol
import com.tprobot.ovejero.data.remote.dto.RobotSummaryDto
import com.tprobot.ovejero.domain.model.Robot
import com.tprobot.ovejero.domain.model.RobotAvailability
import kotlin.math.roundToInt

object RobotMapper {

    fun toDomain(dto: RobotSummaryDto): Robot = Robot(
        id = dto.robotId,
        name = dto.name,
        model = dto.model,
        location = dto.location ?: "Ubicación desconocida",
        availability = when (dto.status) {
            RobotProtocol.RobotStatus.AVAILABLE -> RobotAvailability.DISPONIBLE
            RobotProtocol.RobotStatus.MAINTENANCE -> RobotAvailability.EN_MANTENIMIENTO
            else -> RobotAvailability.FUERA_DE_ALCANCE
        },
        batteryPercent = dto.batteryPercent?.roundToInt() ?: 0,
        note = dto.note
    )
}
