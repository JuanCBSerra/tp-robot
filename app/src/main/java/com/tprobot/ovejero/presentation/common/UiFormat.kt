package com.tprobot.ovejero.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.ui.graphics.vector.ImageVector
import com.tprobot.ovejero.domain.model.AlertSeverity
import com.tprobot.ovejero.domain.model.CommandStatus
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.ConnectionState
import com.tprobot.ovejero.domain.model.MissionStatus
import com.tprobot.ovejero.domain.model.OperationRules
import com.tprobot.ovejero.domain.model.OperationalState
import com.tprobot.ovejero.presentation.common.components.Tone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formateo de horas y elección de "tono" visual (normal / ok / advertencia / error / deshabilitado). */
object UiFormat {

    fun clock(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))

    fun dateTime(millis: Long): String = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(millis))

    fun secondsAgo(seconds: Long?): String = when {
        seconds == null -> "sin datos"
        seconds < 2 -> "recién"
        seconds < 60 -> "hace $seconds s"
        else -> "hace ${seconds / 60} min ${seconds % 60} s"
    }

    fun batteryTone(percent: Int): Tone = when {
        percent <= OperationRules.MIN_BATTERY_TO_RESUME -> Tone.ERROR
        percent <= OperationRules.LOW_BATTERY_WARNING -> Tone.ADVERTENCIA
        else -> Tone.OK
    }

    fun connectionTone(state: ConnectionState): Tone = when (state) {
        ConnectionState.CONECTADO -> Tone.OK
        ConnectionState.CONECTANDO -> Tone.NORMAL
        ConnectionState.SIN_SENIAL -> Tone.ERROR
        ConnectionState.DESCONECTADO -> Tone.DESHABILITADO
    }

    fun operationalTone(state: OperationalState): Tone = when (state) {
        OperationalState.EN_MISION -> Tone.NORMAL
        OperationalState.EN_BASE -> Tone.OK
        OperationalState.PAUSADO, OperationalState.VOLVIENDO_A_BASE -> Tone.ADVERTENCIA
        OperationalState.ESPERANDO_DECISION, OperationalState.DETENIDO -> Tone.ERROR
        OperationalState.DESCONOCIDO -> Tone.DESHABILITADO
    }

    fun missionTone(status: MissionStatus): Tone = when (status) {
        MissionStatus.EN_CURSO -> Tone.NORMAL
        MissionStatus.PAUSADA -> Tone.ADVERTENCIA
        MissionStatus.COMPLETADA -> Tone.OK
        MissionStatus.CANCELADA -> Tone.DESHABILITADO
        MissionStatus.ABORTADA -> Tone.ERROR
    }

    fun commandTone(status: CommandStatus): Tone = when (status) {
        CommandStatus.PENDIENTE -> Tone.NORMAL
        CommandStatus.CONFIRMADA -> Tone.OK
        CommandStatus.RECHAZADA -> Tone.ERROR
        CommandStatus.SIN_RESPUESTA -> Tone.ADVERTENCIA
    }

    fun severityTone(severity: AlertSeverity): Tone = when (severity) {
        AlertSeverity.INFO -> Tone.NORMAL
        AlertSeverity.ADVERTENCIA -> Tone.ADVERTENCIA
        AlertSeverity.CRITICA -> Tone.ERROR
    }

    fun commandIcon(type: CommandType): ImageVector = when (type) {
        CommandType.INICIAR_MISION -> Icons.Filled.PlayArrow
        CommandType.PAUSAR -> Icons.Filled.Pause
        CommandType.REANUDAR -> Icons.Filled.PlayArrow
        CommandType.CANCELAR -> Icons.Filled.Cancel
        CommandType.VOLVER_A_BASE -> Icons.Filled.Home
        CommandType.DETENCION_OPERATIVA -> Icons.Filled.PanTool
        CommandType.RECONOCER_ALERTA -> Icons.Filled.DoneAll
    }

    fun commandStatusIcon(status: CommandStatus): ImageVector = when (status) {
        CommandStatus.PENDIENTE -> Icons.Filled.Pause
        CommandStatus.CONFIRMADA -> Icons.Filled.CheckCircle
        CommandStatus.RECHAZADA -> Icons.Filled.Block
        CommandStatus.SIN_RESPUESTA -> Icons.Filled.Cancel
    }
}
