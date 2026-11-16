package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.model.CommandRecord
import com.tprobot.ovejero.domain.model.RobotCommand
import com.tprobot.ovejero.domain.repository.RobotRepository
import kotlinx.coroutines.flow.first

/*
 * Un caso de uso por orden (consigna 2.2). Todos validan las condiciones de
 * habilitación ANTES de mandar la orden: aunque la UI deshabilite el botón,
 * la regla de negocio vive acá y no en la pantalla.
 */
internal suspend fun sendIfAvailable(
    repository: RobotRepository,
    availability: GetCommandAvailabilityUseCase,
    command: RobotCommand
): Resource<CommandRecord> {
    val status = repository.observeRobotStatus().first()
    val alerts = repository.observeAlerts().first()
    val commands = repository.observeCommands().first()
    val check = availability(status, alerts, commands)[command.type]
    if (check == null || !check.enabled) {
        return Resource.Error(check?.reason ?: "Orden no disponible")
    }
    return repository.sendCommand(command)
}
