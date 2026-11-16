package com.tprobot.ovejero.domain.model

data class Alert(
    val id: String,
    val type: AlertType,
    val severity: AlertSeverity,
    val message: String,
    val zoneName: String?,
    val timestampMillis: Long,
    val requiresDecision: Boolean,
    val acknowledged: Boolean
)

enum class AlertType(val label: String) {
    BATERIA_BAJA("Batería baja"),
    BATERIA_CRITICA("Batería crítica"),
    REGRESO_AUTOMATICO("Regreso automático"),
    OBSTACULO("Obstáculo en el camino"),
    TORMENTA("Se viene una tormenta"),
    PERSONA_CERCA("Persona cerca del robot"),
    ANIMALES_FALTANTES("Faltan animales"),
    DESCONOCIDA("Alerta")
}

enum class AlertSeverity(val label: String) {
    INFO("Info"),
    ADVERTENCIA("Advertencia"),
    CRITICA("Crítica")
}
