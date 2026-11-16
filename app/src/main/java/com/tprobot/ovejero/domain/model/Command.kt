package com.tprobot.ovejero.domain.model

enum class CommandType(val label: String) {
    INICIAR_MISION("Iniciar misión"),
    PAUSAR("Pausar"),
    REANUDAR("Reanudar"),
    CANCELAR("Cancelar misión"),
    VOLVER_A_BASE("Volver al galpón"),
    DETENCION_OPERATIVA("Detención operativa"),
    RECONOCER_ALERTA("Reconocer alerta")
}

/** Orden que el operador le manda al robot. */
data class RobotCommand(
    val type: CommandType,
    val missionType: MissionType? = null,
    val alertId: String? = null
)

/**
 * Ciclo de vida de una orden. Una orden ENVIADA no es una orden EJECUTADA:
 * hasta que el robot no confirma, queda PENDIENTE.
 */
enum class CommandStatus(val label: String) {
    PENDIENTE("Pendiente"),
    CONFIRMADA("Confirmada"),
    RECHAZADA("Rechazada"),
    SIN_RESPUESTA("Sin respuesta")
}

data class CommandRecord(
    val id: String,
    val command: RobotCommand,
    val status: CommandStatus,
    val sentAtMillis: Long,
    val resolvedAtMillis: Long?,
    val rejectionReason: String?
)

/** Si una orden se puede mandar ahora y, si no, por qué. */
data class CommandAvailability(
    val enabled: Boolean,
    val reason: String?
)
