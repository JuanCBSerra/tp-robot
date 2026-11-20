package com.tprobot.ovejero.domain.model

/**
 * Foto del estado del robot en un instante (lo último que reportó).
 */
data class Telemetry(
    val robotId: String,
    val timestampMillis: Long,
    val sequence: Long,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val operationalState: OperationalState,
    val mission: MissionProgress?,
    val position: Position,
    val motion: Motion,
    val herd: HerdStatus,
    val temperatureCelsius: Double,
    val signalPercent: Int
)

enum class OperationalState(val label: String) {
    EN_BASE("En el galpón"),
    EN_MISION("En misión"),
    PAUSADO("Pausado"),
    ESPERANDO_DECISION("Esperando tu decisión"),
    VOLVIENDO_A_BASE("Volviendo al galpón"),
    DETENIDO("Detenido"),
    DESCONOCIDO("Desconocido")
}

/** Posición normalizada (0..1) sobre el plano de la chacra. */
data class Position(
    val x: Float,
    val y: Float,
    val zoneName: String,
    val headingDegrees: Int
)

data class Motion(
    val mode: MotionMode,
    val speedMetersPerSecond: Double
)

enum class MotionMode(val label: String) {
    QUIETO("Quieto"),
    CAMINANDO("Caminando"),
    ARREANDO("Arreando")
}

/** Variables propias del servicio: la hacienda. */
data class HerdStatus(
    val counted: Int,
    val expected: Int,
    val inCorral: Int
) {
    val missing: Int get() = (expected - counted).coerceAtLeast(0)
}

data class MissionProgress(
    val missionId: String,
    val type: MissionType,
    val status: MissionStatus,
    val progressPercent: Int,
    val checkpointsReached: Int,
    val totalCheckpoints: Int,
    val startedAtMillis: Long
) {
    val isActive: Boolean get() = status == MissionStatus.EN_CURSO || status == MissionStatus.PAUSADA
}

enum class MissionType(val label: String, val description: String) {
    RECUENTO(
        label = "Recuento",
        description = "Recorre los 5 potreros y cuenta la hacienda. No mueve a los animales."
    ),
    ARREO(
        label = "Arreo al corral",
        description = "Recorre los potreros, cuenta y lleva la hacienda hasta el corral."
    )
}

enum class MissionStatus(val label: String) {
    EN_CURSO("En curso"),
    PAUSADA("Pausada"),
    COMPLETADA("Completada"),
    CANCELADA("Cancelada"),
    ABORTADA("Abortada")
}
