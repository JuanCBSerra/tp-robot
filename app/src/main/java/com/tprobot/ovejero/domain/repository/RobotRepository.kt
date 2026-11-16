package com.tprobot.ovejero.domain.repository

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.model.Alert
import com.tprobot.ovejero.domain.model.CommandRecord
import com.tprobot.ovejero.domain.model.HistoryEvent
import com.tprobot.ovejero.domain.model.MissionRecord
import com.tprobot.ovejero.domain.model.Robot
import com.tprobot.ovejero.domain.model.RobotCommand
import com.tprobot.ovejero.domain.model.RobotStatus
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del Repositorio del robot, definido en la capa de Domain.
 * Sigue el principio de Inversión de Dependencias (DIP) de SOLID:
 * el Dominio define QUÉ necesita (telemetría, órdenes, alertas, historial) y la
 * capa de Data decide CÓMO obtenerlo. Hoy la implementación habla con un robot
 * simulado; mañana podría hablar con un Go2 real sin tocar la UI.
 *
 * A diferencia de una API REST, el robot manda datos todo el tiempo:
 * por eso la telemetría, las alertas y las órdenes se exponen como Flow.
 */
interface RobotRepository {

    // --- Flota y conexión ---
    suspend fun getRobots(): Resource<List<Robot>>
    suspend fun connect(robotId: String): Resource<Robot>
    suspend fun disconnect(): Resource<Unit>

    // --- Datos en vivo ---
    fun observeRobotStatus(): Flow<RobotStatus>
    fun observeAlerts(): Flow<List<Alert>>
    fun observeCommands(): Flow<List<CommandRecord>>

    // --- Órdenes ---
    /** Envía la orden y devuelve el registro en estado PENDIENTE. La confirmación llega después por [observeCommands]. */
    suspend fun sendCommand(command: RobotCommand): Resource<CommandRecord>

    // --- Historial persistido (Room) ---
    fun observeMissionHistory(): Flow<List<MissionRecord>>
    fun observeEventHistory(): Flow<List<HistoryEvent>>
    suspend fun clearHistory(): Resource<Unit>
}
