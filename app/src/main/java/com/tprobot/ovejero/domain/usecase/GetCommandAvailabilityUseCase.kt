package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.domain.model.Alert
import com.tprobot.ovejero.domain.model.CommandAvailability
import com.tprobot.ovejero.domain.model.CommandRecord
import com.tprobot.ovejero.domain.model.CommandStatus
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.ConnectionState
import com.tprobot.ovejero.domain.model.MissionStatus
import com.tprobot.ovejero.domain.model.OperationRules
import com.tprobot.ovejero.domain.model.OperationalState
import com.tprobot.ovejero.domain.model.RobotStatus
import javax.inject.Inject

/**
 * Condiciones de habilitación de cada orden (consigna 2.2).
 *
 * Es una función pura: recibe el estado y devuelve, para cada orden, si se puede
 * mandar y el motivo si no. La usan los ViewModels (para habilitar botones) y
 * los casos de uso de órdenes (para no mandar algo inválido). Se testea sin Android.
 */
class GetCommandAvailabilityUseCase @Inject constructor() {

    operator fun invoke(
        status: RobotStatus,
        alerts: List<Alert>,
        commands: List<CommandRecord>
    ): Map<CommandType, CommandAvailability> {
        val telemetry = status.telemetry
        val connected = status.connection == ConnectionState.CONECTADO && telemetry != null
        val state = telemetry?.operationalState
        val mission = telemetry?.mission
        val battery = telemetry?.batteryPercent ?: 0
        val missionActive = mission?.isActive == true
        val pendingTypes = commands.filter { it.status == CommandStatus.PENDIENTE }.map { it.command.type }.toSet()
        val decisionPending = alerts.any { it.requiresDecision && !it.acknowledged }
        val connectionCheck = connected to connectionReason(status.connection)

        fun rule(type: CommandType, vararg checks: Pair<Boolean, String>): CommandAvailability {
            if (type in pendingTypes) return CommandAvailability(false, "Esperando respuesta del robot…")
            val failed = checks.firstOrNull { !it.first }
            return if (failed != null) CommandAvailability(false, failed.second) else CommandAvailability(true, null)
        }

        return mapOf(
            CommandType.INICIAR_MISION to rule(
                CommandType.INICIAR_MISION,
                connectionCheck,
                (!missionActive) to "Ya hay una misión en curso",
                (state == OperationalState.EN_BASE) to "El robot tiene que estar en el galpón",
                (battery >= OperationRules.MIN_BATTERY_TO_START) to
                    "Batería insuficiente: mínimo ${OperationRules.MIN_BATTERY_TO_START} %"
            ),
            CommandType.PAUSAR to rule(
                CommandType.PAUSAR,
                connectionCheck,
                (mission?.status == MissionStatus.EN_CURSO && state == OperationalState.EN_MISION) to
                    "No hay una misión en marcha"
            ),
            CommandType.REANUDAR to rule(
                CommandType.REANUDAR,
                connectionCheck,
                (mission?.status == MissionStatus.PAUSADA) to "No hay una misión pausada",
                (!decisionPending) to "Primero reconocé la alerta que pide tu decisión",
                (battery >= OperationRules.MIN_BATTERY_TO_RESUME) to
                    "Batería muy baja para seguir: mandalo al galpón"
            ),
            CommandType.CANCELAR to rule(
                CommandType.CANCELAR,
                connectionCheck,
                missionActive to "No hay una misión para cancelar"
            ),
            CommandType.VOLVER_A_BASE to rule(
                CommandType.VOLVER_A_BASE,
                connectionCheck,
                (state != OperationalState.EN_BASE) to "El robot ya está en el galpón",
                (state != OperationalState.VOLVIENDO_A_BASE) to "Ya está volviendo al galpón"
            ),
            // La detención se permite aun sin señal: se intenta igual (puede quedar "sin respuesta").
            CommandType.DETENCION_OPERATIVA to rule(
                CommandType.DETENCION_OPERATIVA,
                (telemetry != null && status.connection != ConnectionState.DESCONECTADO) to
                    connectionReason(status.connection),
                (state == OperationalState.EN_MISION || state == OperationalState.VOLVIENDO_A_BASE) to
                    "El robot no está en movimiento"
            ),
            CommandType.RECONOCER_ALERTA to rule(
                CommandType.RECONOCER_ALERTA,
                connectionCheck,
                alerts.any { !it.acknowledged } to "No hay alertas pendientes"
            )
        )
    }

    private fun connectionReason(connection: ConnectionState): String = when (connection) {
        ConnectionState.DESCONECTADO -> "No hay un robot conectado"
        ConnectionState.CONECTANDO -> "Conectando con el robot…"
        ConnectionState.SIN_SENIAL -> "Sin señal: la orden no llegaría"
        ConnectionState.CONECTADO -> "Esperando la primera telemetría…"
    }
}
