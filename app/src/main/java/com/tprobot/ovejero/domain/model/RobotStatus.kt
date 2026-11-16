package com.tprobot.ovejero.domain.model

/**
 * Estado de la conexión con el robot + la última telemetría recibida.
 *
 * Si la conexión está en SIN_SENIAL, [telemetry] es el último dato conocido
 * y hay que mostrarlo marcado como desactualizado.
 */
data class RobotStatus(
    val robot: Robot?,
    val connection: ConnectionState,
    val telemetry: Telemetry?,
    val lastUpdateMillis: Long?,
    val checkedAtMillis: Long
) {
    val isStale: Boolean get() = connection == ConnectionState.SIN_SENIAL

    val secondsSinceUpdate: Long?
        get() = lastUpdateMillis?.let { ((checkedAtMillis - it) / 1000).coerceAtLeast(0) }

    companion object {
        fun disconnected(now: Long) = RobotStatus(
            robot = null,
            connection = ConnectionState.DESCONECTADO,
            telemetry = null,
            lastUpdateMillis = null,
            checkedAtMillis = now
        )
    }
}

enum class ConnectionState(val label: String) {
    DESCONECTADO("Desconectado"),
    CONECTANDO("Conectando…"),
    CONECTADO("Conectado"),
    SIN_SENIAL("Sin señal")
}
