package com.tprobot.ovejero.presentation.common

import com.tprobot.ovejero.domain.model.HeatStress
import com.tprobot.ovejero.domain.model.MissionType
import com.tprobot.ovejero.domain.model.OperationRules
import com.tprobot.ovejero.domain.model.RobotStatus
import com.tprobot.ovejero.domain.model.Telemetry
import com.tprobot.ovejero.presentation.common.components.Tone
import kotlin.math.roundToInt

/** Una celda de telemetría ya resuelta: la pantalla solo la dibuja. */
data class TileUi(
    val value: String,
    val caption: String?,
    val tone: Tone
)

/** Las celdas de telemetría del panel de supervisión. */
data class TelemetryPanelUi(
    val battery: TileUi,
    val signal: TileUi,
    val motion: TileUi,
    val location: TileUi,
    val counted: TileUi,
    val corral: TileUi,
    val temperature: TileUi
)

/** Batería frente al mínimo para iniciar. [tone] nulo = color neutro (sin dato o dato viejo). */
data class BatteryRequirementUi(
    val text: String,
    val tone: Tone?
)

/**
 * Decide QUÉ mostrar (textos, tonos, umbrales) a partir del estado del robot.
 *
 * Es Kotlin puro: los ViewModels lo usan para armar el estado de la UI y las pantallas
 * (Compose) solo dibujan el resultado, sin consultar reglas de negocio. Se testea sin Android.
 */
object StatusPresenter {

    /** Debajo de este porcentaje de señal la celda pasa a advertencia. */
    const val LOW_SIGNAL_PERCENT = 40

    fun batteryTone(percent: Int): Tone = when {
        percent <= OperationRules.MIN_BATTERY_TO_RESUME -> Tone.ERROR
        percent <= OperationRules.LOW_BATTERY_WARNING -> Tone.ADVERTENCIA
        else -> Tone.OK
    }

    /** Batería siempre visible en la barra superior; si no hay señal se marca como dato viejo. */
    fun topBarBatterySuffix(status: RobotStatus): String {
        val battery = status.telemetry?.batteryPercent ?: return ""
        val marker = when {
            status.isStale -> " (dato viejo)"
            battery <= OperationRules.LOW_BATTERY_WARNING -> " ⚠️"
            else -> ""
        }
        return " · 🔋 $battery %$marker"
    }

    /**
     * Batería actual frente al mínimo para iniciar. Se compara contra MIN_BATTERY_TO_START
     * (no contra el umbral de advertencia) para que el color coincida con lo que realmente
     * habilita o bloquea la orden.
     */
    fun batteryRequirement(status: RobotStatus): BatteryRequirementUi {
        val minimum = OperationRules.MIN_BATTERY_TO_START
        val battery = status.telemetry?.batteryPercent
        return when {
            battery == null -> BatteryRequirementUi("Batería: sin datos (mínimo $minimum %)", null)
            status.isStale -> BatteryRequirementUi("Batería: $battery % · dato viejo (mínimo $minimum %)", null)
            battery >= minimum -> BatteryRequirementUi("Batería: $battery % (mínimo $minimum %)", Tone.OK)
            else -> BatteryRequirementUi(
                "Batería: $battery % · insuficiente para iniciar (mínimo $minimum %)",
                Tone.ERROR
            )
        }
    }

    fun temperatureTile(telemetry: Telemetry): TileUi {
        val heatIndex = telemetry.heatIndex
        val ith = heatIndex.roundToInt()
        val (tone, caption) = when (HeatStress.level(heatIndex)) {
            HeatStress.Level.PELIGRO -> Tone.ERROR to "ITH $ith · peligro"
            HeatStress.Level.ALERTA -> Tone.ADVERTENCIA to "ITH $ith · estrés térmico"
            HeatStress.Level.SIN_ESTRES -> Tone.OK to "ITH $ith · hum. ${telemetry.humidityPercent} %"
        }
        return TileUi(value = "%.1f °C".format(telemetry.temperatureCelsius), caption = caption, tone = tone)
    }

    fun telemetryPanel(status: RobotStatus, telemetry: Telemetry): TelemetryPanelUi {
        val herd = telemetry.herd
        val isHerding = telemetry.mission?.type == MissionType.ARREO
        val missionEndedWithMissing = telemetry.mission?.isActive == false && herd.missing > 0
        return TelemetryPanelUi(
            battery = TileUi(
                value = "${telemetry.batteryPercent} %",
                caption = if (telemetry.isCharging) "cargando en el galpón" else null,
                tone = batteryTone(telemetry.batteryPercent)
            ),
            signal = TileUi(
                value = "${telemetry.signalPercent} %",
                caption = status.connection.label,
                tone = if (telemetry.signalPercent < LOW_SIGNAL_PERCENT) Tone.ADVERTENCIA else Tone.OK
            ),
            motion = TileUi(
                value = telemetry.motion.mode.label,
                caption = "%.1f m/s".format(telemetry.motion.speedMetersPerSecond),
                tone = Tone.NORMAL
            ),
            location = TileUi(
                value = telemetry.position.zoneName,
                caption = null,
                tone = Tone.NORMAL
            ),
            counted = TileUi(
                value = "${herd.counted} / ${herd.expected}",
                caption = if (herd.missing > 0) "faltan ${herd.missing}" else "están todos",
                tone = if (missionEndedWithMissing) Tone.ADVERTENCIA else Tone.NORMAL
            ),
            corral = TileUi(
                value = if (isHerding) "${herd.inCorral} / ${herd.expected}" else "—",
                caption = if (isHerding) null else "solo en arreo",
                tone = Tone.NORMAL
            ),
            temperature = temperatureTile(telemetry)
        )
    }
}
