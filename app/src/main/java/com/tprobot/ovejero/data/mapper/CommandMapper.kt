package com.tprobot.ovejero.data.mapper

import com.tprobot.ovejero.data.remote.RobotProtocol
import com.tprobot.ovejero.data.remote.dto.CommandParamsDto
import com.tprobot.ovejero.data.remote.dto.CommandRequestDto
import com.tprobot.ovejero.domain.model.CommandStatus
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.RobotCommand

/** Dominio -> JSON (las órdenes viajan en el sentido contrario a la telemetría). */
object CommandMapper {

    fun toDto(commandId: String, robotId: String, command: RobotCommand, timestamp: Long): CommandRequestDto {
        val params = when {
            command.missionType != null ->
                CommandParamsDto(missionKind = TelemetryMapper.toMissionKindCode(command.missionType))
            command.alertId != null -> CommandParamsDto(alertId = command.alertId)
            else -> null
        }
        return CommandRequestDto(
            type = RobotProtocol.MessageType.COMMAND,
            commandId = commandId,
            robotId = robotId,
            timestamp = timestamp,
            command = toCode(command.type),
            params = params
        )
    }

    fun toCode(type: CommandType): String = when (type) {
        CommandType.INICIAR_MISION -> RobotProtocol.Command.START_MISSION
        CommandType.PAUSAR -> RobotProtocol.Command.PAUSE_MISSION
        CommandType.REANUDAR -> RobotProtocol.Command.RESUME_MISSION
        CommandType.CANCELAR -> RobotProtocol.Command.CANCEL_MISSION
        CommandType.VOLVER_A_BASE -> RobotProtocol.Command.RETURN_TO_BASE
        CommandType.DETENCION_OPERATIVA -> RobotProtocol.Command.OPERATIONAL_STOP
        CommandType.RECONOCER_ALERTA -> RobotProtocol.Command.ACK_ALERT
    }

    fun toStatus(ackStatus: String): CommandStatus = when (ackStatus) {
        RobotProtocol.AckStatus.ACCEPTED -> CommandStatus.CONFIRMADA
        else -> CommandStatus.RECHAZADA
    }
}
