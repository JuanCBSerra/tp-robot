package com.tprobot.ovejero.domain.model

/**
 * Reglas de negocio de la operación (umbrales). Viven en el dominio para que
 * la UI, los casos de uso y los tests usen los mismos números.
 */
object OperationRules {
    const val MIN_BATTERY_TO_START = 30
    const val MIN_BATTERY_TO_RESUME = 15
    const val LOW_BATTERY_WARNING = 25
    const val STALE_AFTER_MILLIS = 3_000L
    const val COMMAND_TIMEOUT_MILLIS = 5_000L

    // Estrés térmico (ITH = índice de temperatura y humedad), bovinos de carne.
    // Fuente: INTA EEA General Villegas: >= 75 alerta, >= 79 peligro (>= 84 emergencia).
    const val HEAT_THI_WARNING = 75
    const val HEAT_THI_DANGER = 79

    /** Si el robot no informa humedad (contrato 1.0), se asume este valor para calcular el ITH. */
    const val ASSUMED_HUMIDITY_PERCENT = 60
}
