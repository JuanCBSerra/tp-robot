package com.tprobot.ovejero.domain.model

data class HistoryEvent(
    val id: Long,
    val timestampMillis: Long,
    val category: EventCategory,
    val title: String,
    val detail: String
)

enum class EventCategory(val label: String) {
    MISION("Misión"),
    COMANDO("Orden"),
    ALERTA("Alerta"),
    CONEXION("Conexión"),
    SISTEMA("Sistema")
}

data class MissionRecord(
    val missionId: String,
    val robotName: String,
    val type: MissionType,
    val result: MissionStatus,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val headsCounted: Int,
    val headsExpected: Int,
    val headsInCorral: Int,
    val batteryUsedPercent: Int
) {
    val durationMinutes: Long get() = ((endedAtMillis - startedAtMillis) / 60_000).coerceAtLeast(0)
}
