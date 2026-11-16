package com.tprobot.ovejero.data.remote

import kotlinx.coroutines.flow.Flow

/**
 * Canal de comunicación con el robot (el equivalente al PokeApiService de la cátedra).
 *
 * Todo lo que viaja por acá es JSON (ver docs/contrato-json.md):
 * - [incoming]: mensajes que manda el robot (telemetry, alert, command_ack).
 * - [send]: órdenes que manda la app (command).
 *
 * Hoy lo implementa RobotSimulator. Para un robot real habría que escribir otra
 * implementación (por ejemplo, con WebSocket o un bridge ROS 2) y cambiar el binding en Hilt.
 * El contrato es una propuesta nuestra: NO es el protocolo oficial de Unitree.
 */
interface RobotDataSource {
    val incoming: Flow<String>

    /** Devuelve la flota como JSON ({"robots": [...]}). */
    suspend fun fetchFleet(): String

    /** Handshake con el robot. true si aceptó la conexión. */
    suspend fun connect(robotId: String): Boolean

    suspend fun disconnect()

    suspend fun send(json: String)
}
