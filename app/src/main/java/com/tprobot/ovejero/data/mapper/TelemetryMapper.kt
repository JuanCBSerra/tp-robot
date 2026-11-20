package com.tprobot.ovejero.data.mapper

import com.tprobot.ovejero.data.remote.RobotProtocol
import com.tprobot.ovejero.data.remote.dto.MissionDto
import com.tprobot.ovejero.data.remote.dto.TelemetryDto
import com.tprobot.ovejero.domain.model.HerdStatus
import com.tprobot.ovejero.domain.model.MissionProgress
import com.tprobot.ovejero.domain.model.MissionStatus
import com.tprobot.ovejero.domain.model.MissionType
import com.tprobot.ovejero.domain.model.Motion
import com.tprobot.ovejero.domain.model.MotionMode
import com.tprobot.ovejero.domain.model.OperationalState
import com.tprobot.ovejero.domain.model.Position
import com.tprobot.ovejero.domain.model.Telemetry
import kotlin.math.roundToInt

/**
 * Traduce el JSON del robot (DTO, códigos en inglés) al modelo de dominio (enums en castellano).
 * Ante códigos desconocidos no rompe: usa valores por defecto.
 */
object TelemetryMapper {

    fun toDomain(dto: TelemetryDto): Telemetry = Telemetry(
        robotId = dto.robotId,
        timestampMillis = dto.timestamp,
        sequence = dto.sequence,
        batteryPercent = dto.battery.percent.roundToInt().coerceIn(0, 100),
        isCharging = dto.battery.charging,
        operationalState = toOperationalState(dto.state),
        mission = dto.mission?.let { toDomain(it) },
        position = Position(
            x = dto.position.x.toFloat().coerceIn(0f, 1f),
            y = dto.position.y.toFloat().coerceIn(0f, 1f),
            zoneName = dto.position.zone ?: "Sin datos",
            headingDegrees = dto.position.headingDeg ?: 0
        ),
        motion = Motion(
            mode = toMotionMode(dto.motion.mode),
            speedMetersPerSecond = dto.motion.speedMps
        ),
        herd = HerdStatus(
            counted = dto.service.headsCounted,
            expected = dto.service.headsExpected,
            inCorral = dto.service.headsInCorral
        ),
        temperatureCelsius = dto.service.temperatureC,
        signalPercent = dto.link?.signalPercent ?: 0
    )

    fun toDomain(dto: MissionDto): MissionProgress = MissionProgress(
        missionId = dto.missionId,
        type = toMissionType(dto.kind),
        status = toMissionStatus(dto.status),
        // progress viene como fracción 0..1 -> lo pasamos a porcentaje
        progressPercent = (dto.progress * 100).roundToInt().coerceIn(0, 100),
        checkpointsReached = dto.checkpointsReached,
        totalCheckpoints = dto.checkpointsTotal,
        startedAtMillis = dto.startedAt
    )

    fun toOperationalState(code: String): OperationalState = when (code) {
        RobotProtocol.State.DOCKED -> OperationalState.EN_BASE
        RobotProtocol.State.ON_MISSION -> OperationalState.EN_MISION
        RobotProtocol.State.PAUSED -> OperationalState.PAUSADO
        RobotProtocol.State.AWAITING_DECISION -> OperationalState.ESPERANDO_DECISION
        RobotProtocol.State.RETURNING -> OperationalState.VOLVIENDO_A_BASE
        RobotProtocol.State.STOPPED -> OperationalState.DETENIDO
        else -> OperationalState.DESCONOCIDO
    }

    fun toMotionMode(code: String): MotionMode = when (code) {
        RobotProtocol.Motion.WALKING -> MotionMode.CAMINANDO
        RobotProtocol.Motion.HERDING -> MotionMode.ARREANDO
        else -> MotionMode.QUIETO
    }

    fun toMissionType(code: String): MissionType = when (code) {
        RobotProtocol.MissionKind.HERDING -> MissionType.ARREO
        else -> MissionType.RECUENTO
    }

    fun toMissionKindCode(type: MissionType): String = when (type) {
        MissionType.RECUENTO -> RobotProtocol.MissionKind.HEAD_COUNT
        MissionType.ARREO -> RobotProtocol.MissionKind.HERDING
    }

    fun toMissionStatus(code: String): MissionStatus = when (code) {
        RobotProtocol.MissionStatus.RUNNING -> MissionStatus.EN_CURSO
        RobotProtocol.MissionStatus.PAUSED -> MissionStatus.PAUSADA
        RobotProtocol.MissionStatus.COMPLETED -> MissionStatus.COMPLETADA
        RobotProtocol.MissionStatus.CANCELLED -> MissionStatus.CANCELADA
        else -> MissionStatus.ABORTADA
    }
}
