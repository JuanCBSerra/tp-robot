package com.tprobot.ovejero.domain.model

/**
 * Robot de la flota que el operador puede seleccionar.
 */
data class Robot(
    val id: String,
    val name: String,
    val model: String,
    val location: String,
    val availability: RobotAvailability,
    val batteryPercent: Int,
    val note: String?
) {
    val isSelectable: Boolean get() = availability == RobotAvailability.DISPONIBLE
}

enum class RobotAvailability(val label: String) {
    DISPONIBLE("Disponible"),
    EN_MANTENIMIENTO("En mantenimiento"),
    FUERA_DE_ALCANCE("Fuera de alcance")
}
