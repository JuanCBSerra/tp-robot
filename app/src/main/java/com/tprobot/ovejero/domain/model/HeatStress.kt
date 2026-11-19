package com.tprobot.ovejero.domain.model

/**
 * Estrés térmico de la hacienda a partir de temperatura y humedad (ITH / THI).
 *
 * Fórmula (Texas A&M AgriLife Extension), con la temperatura en °F y la humedad en %:
 *   ITH = T - (0.55 - 0.0055 * HR) * (T - 58)
 * Umbrales para bovinos de carne (INTA EEA General Villegas): >= 75 alerta, >= 79 peligro.
 * Ver docs/decisiones-y-limitaciones.md.
 */
object HeatStress {

    enum class Level { SIN_ESTRES, ALERTA, PELIGRO }

    fun thi(temperatureC: Double, humidityPercent: Int): Double {
        val fahrenheit = temperatureC * 1.8 + 32.0
        return fahrenheit - (0.55 - 0.0055 * humidityPercent) * (fahrenheit - 58.0)
    }

    fun level(thi: Double): Level = when {
        thi >= OperationRules.HEAT_THI_DANGER -> Level.PELIGRO
        thi >= OperationRules.HEAT_THI_WARNING -> Level.ALERTA
        else -> Level.SIN_ESTRES
    }
}
