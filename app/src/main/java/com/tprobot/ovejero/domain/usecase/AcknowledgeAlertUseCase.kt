package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.model.CommandRecord
import com.tprobot.ovejero.domain.model.CommandStatus
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.ConnectionState
import com.tprobot.ovejero.domain.model.RobotCommand
import com.tprobot.ovejero.domain.repository.RobotRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Reconocer una alerta: valida que exista, que no esté reconocida y que no haya otro reconocimiento pendiente. */
class AcknowledgeAlertUseCase @Inject constructor(
    private val repository: RobotRepository
) {
    suspend operator fun invoke(alertId: String): Resource<CommandRecord> {
        if (alertId.isBlank()) return Resource.Error("Alerta inválida")
        val status = repository.observeRobotStatus().first()
        if (status.connection != ConnectionState.CONECTADO) {
            return Resource.Error("Sin conexión con el robot: no se puede reconocer la alerta")
        }
        val alert = repository.observeAlerts().first().firstOrNull { it.id == alertId }
            ?: return Resource.Error("La alerta ya no existe")
        if (alert.acknowledged) return Resource.Error("La alerta ya estaba reconocida")
        val alreadyPending = repository.observeCommands().first().any {
            it.command.alertId == alertId && it.status == CommandStatus.PENDIENTE
        }
        if (alreadyPending) return Resource.Error("Ya se mandó el reconocimiento, esperando respuesta…")
        return repository.sendCommand(RobotCommand(CommandType.RECONOCER_ALERTA, alertId = alertId))
    }
}
