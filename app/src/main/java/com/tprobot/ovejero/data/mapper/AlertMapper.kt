package com.tprobot.ovejero.data.mapper

import com.tprobot.ovejero.data.remote.RobotProtocol
import com.tprobot.ovejero.data.remote.dto.AlertDto
import com.tprobot.ovejero.domain.model.Alert
import com.tprobot.ovejero.domain.model.AlertSeverity
import com.tprobot.ovejero.domain.model.AlertType

object AlertMapper {

    fun toDomain(dto: AlertDto): Alert {
        val type = toAlertType(dto.code)
        return Alert(
            id = dto.alertId,
            type = type,
            severity = toSeverity(dto.severity),
            message = dto.message ?: type.label,
            zoneName = dto.zone,
            timestampMillis = dto.timestamp,
            requiresDecision = dto.requiresDecision ?: false,
            acknowledged = false
        )
    }

    fun toAlertType(code: String): AlertType = when (code) {
        RobotProtocol.AlertCode.LOW_BATTERY -> AlertType.BATERIA_BAJA
        RobotProtocol.AlertCode.CRITICAL_BATTERY -> AlertType.BATERIA_CRITICA
        RobotProtocol.AlertCode.AUTO_RETURN -> AlertType.REGRESO_AUTOMATICO
        RobotProtocol.AlertCode.OBSTACLE -> AlertType.OBSTACULO
        RobotProtocol.AlertCode.STORM -> AlertType.TORMENTA
        RobotProtocol.AlertCode.PERSON_NEARBY -> AlertType.PERSONA_CERCA
        RobotProtocol.AlertCode.MISSING_ANIMALS -> AlertType.ANIMALES_FALTANTES
        RobotProtocol.AlertCode.HEAT_STRESS -> AlertType.ESTRES_TERMICO
        else -> AlertType.DESCONOCIDA
    }

    fun toSeverity(code: String): AlertSeverity = when (code) {
        RobotProtocol.Severity.CRITICAL -> AlertSeverity.CRITICA
        RobotProtocol.Severity.WARNING -> AlertSeverity.ADVERTENCIA
        else -> AlertSeverity.INFO
    }
}
