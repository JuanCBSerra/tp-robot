package com.tprobot.ovejero.domain.model

/** Velocidad del reloj del simulador (para que la demo no dure 15 minutos). */
enum class SimulationSpeed(val multiplier: Int, val label: String) {
    X1(1, "x1"),
    X5(5, "x5"),
    X10(10, "x10")
}

/** Escenarios guionados que pide la consigna (punto 5). */
enum class Scenario(val label: String, val description: String) {
    NORMAL(
        label = "1 · Misión normal",
        description = "Batería llena, sin incidentes. Arreo completo 45/45."
    ),
    BATERIA_BAJA(
        label = "2 · Batería baja",
        description = "Arranca con 34 %. Avisa al 25 %, se pone crítica al 15 % y vuelve sola al 10 %."
    ),
    DESCONEXION(
        label = "3 · Desconexión",
        description = "Al llegar al Potrero 3 (monte) se corta la señal unos 12 segundos."
    ),
    INCIDENTE(
        label = "4 · Alambrado caído",
        description = "En el Potrero 4 encuentra un alambrado caído y espera tu decisión."
    ),
    TORMENTA(
        label = "5 · Se viene una tormenta",
        description = "En el Potrero 3 se anuncia tormenta y quedan 3 vacas en el monte sin encontrar."
    ),
    CALOR(
        label = "6 · Ola de calor (extra)",
        description = "El calor sube durante el recorrido: primero avisa (ITH 75) y después frena al robot (ITH 79) y espera tu decisión."
    )
}

/** Acciones que se pueden forzar desde el panel de simulación. */
sealed interface SimulationAction {
    data class LoadScenario(val scenario: Scenario) : SimulationAction
    data class SetSpeed(val speed: SimulationSpeed) : SimulationAction
    data class SetLinkDown(val down: Boolean) : SimulationAction
    data object RejectNextCommand : SimulationAction
    data object IgnoreNextCommand : SimulationAction
    data object DrainBattery : SimulationAction
    data object TriggerObstacle : SimulationAction
    data object TriggerStorm : SimulationAction
    data object TriggerHeat : SimulationAction
    data object TriggerPersonNearby : SimulationAction
}

data class SimulationSettings(
    val scenario: Scenario,
    val speed: SimulationSpeed,
    val linkDown: Boolean,
    val rejectNextCommand: Boolean,
    val ignoreNextCommand: Boolean,
    val lastTelemetryJson: String?,
    val lastCommandJson: String?,
    val lastResponseJson: String?,
    val lastAlertJson: String?
)
