package com.tprobot.ovejero.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tprobot.ovejero.core.theme.EstadoDeshabilitado
import com.tprobot.ovejero.core.theme.EstadoDeshabilitadoFondo
import com.tprobot.ovejero.core.theme.EstadoError
import com.tprobot.ovejero.domain.model.CommandAvailability
import com.tprobot.ovejero.domain.model.CommandRecord
import com.tprobot.ovejero.domain.model.CommandStatus
import com.tprobot.ovejero.presentation.common.UiFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Botón de orden: si está deshabilitado muestra debajo POR QUÉ (condición de habilitación).
 */
@Composable
fun CommandButton(
    label: String,
    icon: ImageVector,
    availability: CommandAvailability?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    val enabled = availability?.enabled == true
    Column(modifier = modifier) {
        if (primary) {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        } else {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
        val reason = availability?.reason
        if (!enabled && reason != null) {
            Text(
                reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

/**
 * Detención operativa: hay que MANTENER presionado [holdMillis] para evitar
 * órdenes accidentales (riesgo 2). Es un comando de software, no una parada de emergencia física.
 */
@Composable
fun HoldToConfirmButton(
    label: String,
    availability: CommandAvailability?,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Long = 1_500
) {
    val enabled = availability?.enabled == true
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val currentOnConfirmed by rememberUpdatedState(onConfirmed)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) EstadoError.copy(alpha = 0.85f) else EstadoDeshabilitadoFondo)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            val job = scope.launch {
                                val start = System.currentTimeMillis()
                                while (progress < 1f) {
                                    progress = ((System.currentTimeMillis() - start) / holdMillis.toFloat()).coerceAtMost(1f)
                                    delay(16)
                                }
                                currentOnConfirmed()
                            }
                            tryAwaitRelease()
                            job.cancel()
                            progress = 0f
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(Color(0xFF7F0000))
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.PanTool,
                    contentDescription = null,
                    tint = if (enabled) Color.White else EstadoDeshabilitado
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (enabled) "Mantené presionado · $label" else label,
                    color = if (enabled) Color.White else EstadoDeshabilitado,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        val reason = if (!enabled) availability?.reason else null
        Text(
            reason ?: "Comando de software simulado: no reemplaza la parada de emergencia física.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}

/** Fila del registro de órdenes con su estado visible (pendiente / confirmada / rechazada / sin respuesta). */
@Composable
fun CommandRecordRow(record: CommandRecord, modifier: Modifier = Modifier) {
    val tone = UiFormat.commandTone(record.status)
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            UiFormat.commandIcon(record.command.type),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val extra = record.command.missionType?.let { " · ${it.label}" }
                ?: record.command.alertId?.let { " · $it" }
                ?: ""
            Text(record.command.type.label + extra, style = MaterialTheme.typography.bodyLarge)
            val detail = buildString {
                append(UiFormat.clock(record.sentAtMillis))
                append(" · ")
                append(record.id)
                if (record.status == CommandStatus.RECHAZADA && record.rejectionReason != null) {
                    append(" · ")
                    append(record.rejectionReason)
                }
                if (record.status == CommandStatus.SIN_RESPUESTA) append(" · no sabemos si se ejecutó")
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(record.status.label, tone, icon = UiFormat.commandStatusIcon(record.status))
    }
}
