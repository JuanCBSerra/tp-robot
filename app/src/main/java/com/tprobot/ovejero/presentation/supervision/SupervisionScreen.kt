package com.tprobot.ovejero.presentation.supervision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.core.theme.EstadoError
import com.tprobot.ovejero.core.theme.EstadoErrorFondo
import com.tprobot.ovejero.domain.model.Alert
import com.tprobot.ovejero.domain.model.AlertType
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.FieldMap
import com.tprobot.ovejero.domain.model.MissionType
import com.tprobot.ovejero.domain.model.Telemetry
import com.tprobot.ovejero.presentation.common.UiFormat
import com.tprobot.ovejero.presentation.common.components.CommandButton
import com.tprobot.ovejero.presentation.common.components.CommandRecordRow
import com.tprobot.ovejero.presentation.common.components.EmptyStateView
import com.tprobot.ovejero.presentation.common.components.ErrorStateView
import com.tprobot.ovejero.presentation.common.components.FieldMapView
import com.tprobot.ovejero.presentation.common.components.HoldToConfirmButton
import com.tprobot.ovejero.presentation.common.components.LoadingStateView
import com.tprobot.ovejero.presentation.common.components.SectionCard
import com.tprobot.ovejero.presentation.common.components.StatusChip
import com.tprobot.ovejero.presentation.common.components.TelemetryTile
import com.tprobot.ovejero.presentation.common.components.Tone
import kotlinx.coroutines.flow.collectLatest

/**
 * Panel de supervisión: mapa, misión, telemetría completa (incluye las 3 variables
 * del servicio), decisión pendiente y acciones rápidas.
 */
@Composable
fun SupervisionScreen(
    onGoToControl: () -> Unit,
    viewModel: SupervisionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collectLatest { effect ->
            when (effect) {
                is SupervisionEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        when (val state = uiState) {
            is UiState.Idle, is UiState.Loading ->
                LoadingStateView("Esperando telemetría…", Modifier.padding(innerPadding))
            is UiState.Error ->
                ErrorStateView(state.message, onRetry = { viewModel.observeSupervision() }, modifier = Modifier.padding(innerPadding))
            is UiState.Success -> SupervisionContent(
                data = state.data,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onCancel = viewModel::cancel,
                onReturn = viewModel::returnToBase,
                onStop = viewModel::operationalStop,
                onAcknowledge = viewModel::acknowledge,
                onGoToControl = onGoToControl,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun SupervisionContent(
    data: SupervisionData,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onReturn: () -> Unit,
    onStop: () -> Unit,
    onAcknowledge: (String) -> Unit,
    onGoToControl: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry = data.status.telemetry
    val stale = data.status.isStale
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusHeader(data)

        data.pendingDecision?.let { alert ->
            DecisionCard(
                alert = alert,
                data = data,
                onAcknowledge = { onAcknowledge(alert.id) },
                onResume = onResume,
                onReturn = onReturn,
                onCancel = onCancel
            )
        }

        if (telemetry == null) {
            SectionCard(title = "Telemetría", icon = Icons.Filled.SettingsRemote) {
                EmptyStateView("Todavía no llegó la primera telemetría del robot.", Icons.Filled.SettingsRemote)
            }
        } else {
            TelemetrySections(telemetry, data, stale, onPause, onResume, onReturn, onStop, onGoToControl)
        }
    }
}

@Composable
private fun TelemetrySections(
    telemetry: Telemetry,
    data: SupervisionData,
    stale: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReturn: () -> Unit,
    onStop: () -> Unit,
    onGoToControl: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Mapa de la chacra", icon = Icons.Filled.Map) {
            FieldMapView(
                map = FieldMap.LA_COLORADA,
                robotPosition = telemetry.position,
                checkpointsReached = telemetry.mission?.takeIf { it.isActive }?.checkpointsReached ?: 0,
                stale = stale,
                alertActive = data.unacknowledgedAlerts > 0
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Ubicación: ${telemetry.position.zoneName} · rumbo ${telemetry.position.headingDegrees}°",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        MissionCard(telemetry, stale, onGoToControl)

        TelemetryGrid(telemetry, data)

        SectionCard(title = "Acciones rápidas", icon = Icons.Filled.SettingsRemote) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CommandButton(
                        label = "Pausar",
                        icon = UiFormat.commandIcon(CommandType.PAUSAR),
                        availability = data.availability[CommandType.PAUSAR],
                        onClick = onPause,
                        modifier = Modifier.weight(1f)
                    )
                    CommandButton(
                        label = "Reanudar",
                        icon = UiFormat.commandIcon(CommandType.REANUDAR),
                        availability = data.availability[CommandType.REANUDAR],
                        onClick = onResume,
                        modifier = Modifier.weight(1f)
                    )
                }
                CommandButton(
                    label = "Volver al galpón",
                    icon = UiFormat.commandIcon(CommandType.VOLVER_A_BASE),
                    availability = data.availability[CommandType.VOLVER_A_BASE],
                    onClick = onReturn
                )
                HoldToConfirmButton(
                    label = "Detención operativa",
                    availability = data.availability[CommandType.DETENCION_OPERATIVA],
                    onConfirmed = onStop
                )
                data.lastCommand?.let {
                    Text("Última orden", style = MaterialTheme.typography.labelLarge)
                    CommandRecordRow(it)
                }
            }
        }
    }
}

@Composable
private fun StatusHeader(data: SupervisionData) {
    val status = data.status
    val telemetry = status.telemetry
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(status.connection.label, UiFormat.connectionTone(status.connection), icon = Icons.Filled.Wifi)
            if (telemetry != null) {
                StatusChip(
                    telemetry.operationalState.label,
                    if (status.isStale) Tone.DESHABILITADO else UiFormat.operationalTone(telemetry.operationalState)
                )
            }
        }
        Text(
            "Última actualización: ${UiFormat.secondsAgo(status.secondsSinceUpdate)}" +
                (status.lastUpdateMillis?.let { " (${UiFormat.clock(it)})" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = if (status.isStale) EstadoError else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (status.isStale) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * Tarjeta de decisión: aparece cuando el robot se frena y espera al operador
 * (alambrado caído, tormenta). Paso 1: reconocer. Paso 2: elegir qué hacer.
 */
@Composable
private fun DecisionCard(
    alert: Alert,
    data: SupervisionData,
    onAcknowledge: () -> Unit,
    onResume: () -> Unit,
    onReturn: () -> Unit,
    onCancel: () -> Unit
) {
    val resumeLabel = when (alert.type) {
        AlertType.OBSTACULO -> "Rodear y seguir"
        AlertType.TORMENTA -> "Seguir buscando"
        else -> "Reanudar"
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = EstadoErrorFondo)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = EstadoError)
                Spacer(Modifier.padding(4.dp))
                Text("El robot espera tu decisión", style = MaterialTheme.typography.titleMedium, color = EstadoError)
            }
            Text(alert.message, style = MaterialTheme.typography.bodyLarge)
            if (!alert.acknowledged) {
                Text("1. Reconocé la alerta para habilitar las opciones.", style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = onAcknowledge,
                    enabled = data.availability[CommandType.RECONOCER_ALERTA]?.enabled == true,
                    colors = ButtonDefaults.buttonColors(containerColor = EstadoError),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reconocer alerta ${alert.id}") }
            } else {
                Text("2. ¿Qué hacemos?", style = MaterialTheme.typography.bodyMedium)
            }
            CommandButton(
                label = resumeLabel,
                icon = UiFormat.commandIcon(CommandType.REANUDAR),
                availability = data.availability[CommandType.REANUDAR],
                onClick = onResume,
                primary = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onReturn,
                    enabled = alert.acknowledged && data.availability[CommandType.VOLVER_A_BASE]?.enabled == true,
                    modifier = Modifier.weight(1f)
                ) { Text("Volver al galpón") }
                OutlinedButton(
                    onClick = onCancel,
                    enabled = alert.acknowledged && data.availability[CommandType.CANCELAR]?.enabled == true,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancelar misión") }
            }
            Text(
                "Si no hacés nada, el robot se queda quieto esperando.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MissionCard(telemetry: Telemetry, stale: Boolean, onGoToControl: () -> Unit) {
    val mission = telemetry.mission
    val trailing: (@Composable () -> Unit)? = mission?.let { m ->
        @Composable { StatusChip(m.status.label, if (stale) Tone.DESHABILITADO else UiFormat.missionTone(m.status)) }
    }
    SectionCard(
        title = "Misión",
        icon = Icons.Filled.Flag,
        trailing = trailing
    ) {
        if (mission == null) {
            Text("No hay misión. Iniciá una desde Control.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onGoToControl) { Text("Ir a Control") }
        } else {
            Text(mission.type.label, style = MaterialTheme.typography.titleLarge)
            Text(
                "${mission.missionId} · desde las ${UiFormat.clock(mission.startedAtMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { mission.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(10.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${mission.progressPercent} % del recorrido · punto de control ${mission.checkpointsReached} de ${mission.totalCheckpoints}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun TelemetryGrid(telemetry: Telemetry, data: SupervisionData) {
    val stale = data.status.isStale
    val herd = telemetry.herd
    val isHerding = telemetry.mission?.type == MissionType.ARREO
    SectionCard(title = "Telemetría", icon = Icons.Filled.SettingsRemote) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TelemetryTile(
                    label = "Batería",
                    value = "${telemetry.batteryPercent} %",
                    icon = if (telemetry.isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                    tone = UiFormat.batteryTone(telemetry.batteryPercent),
                    stale = stale,
                    caption = if (telemetry.isCharging) "cargando en el galpón" else null,
                    modifier = Modifier.weight(1f)
                )
                TelemetryTile(
                    label = "Señal",
                    value = "${telemetry.signalPercent} %",
                    icon = Icons.Filled.Wifi,
                    tone = when {
                        telemetry.signalPercent < 40 -> Tone.ADVERTENCIA
                        else -> Tone.OK
                    },
                    stale = stale,
                    caption = data.status.connection.label,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TelemetryTile(
                    label = "Movimiento",
                    value = telemetry.motion.mode.label,
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    tone = Tone.NORMAL,
                    stale = stale,
                    caption = "%.1f m/s".format(telemetry.motion.speedMetersPerSecond),
                    modifier = Modifier.weight(1f)
                )
                TelemetryTile(
                    label = "Ubicación",
                    value = telemetry.position.zoneName,
                    icon = Icons.Filled.Place,
                    tone = Tone.NORMAL,
                    stale = stale,
                    modifier = Modifier.weight(1f)
                )
            }
            Text("Datos del servicio", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TelemetryTile(
                    label = "Animales contados",
                    value = "${herd.counted} / ${herd.expected}",
                    icon = Icons.Filled.Pets,
                    tone = if (telemetry.mission?.isActive == false && herd.missing > 0) Tone.ADVERTENCIA else Tone.NORMAL,
                    stale = stale,
                    caption = if (herd.missing > 0) "faltan ${herd.missing}" else "están todos",
                    modifier = Modifier.weight(1f)
                )
                TelemetryTile(
                    label = "En el corral",
                    value = if (isHerding) "${herd.inCorral} / ${herd.expected}" else "—",
                    icon = Icons.Filled.Warehouse,
                    tone = Tone.NORMAL,
                    stale = stale,
                    caption = if (isHerding) null else "solo en arreo",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TelemetryTile(
                    label = "Temperatura",
                    value = "%.1f °C".format(telemetry.temperatureCelsius),
                    icon = Icons.Filled.Thermostat,
                    tone = when {
                        telemetry.temperatureCelsius >= 38 -> Tone.ERROR
                        telemetry.temperatureCelsius >= 34 -> Tone.ADVERTENCIA
                        else -> Tone.OK
                    },
                    stale = stale,
                    caption = if (telemetry.temperatureCelsius >= 34) "estrés térmico" else null,
                    modifier = Modifier.weight(1f)
                )
                TelemetryTile(
                    label = "Telemetría #",
                    value = telemetry.sequence.toString(),
                    icon = Icons.Filled.AccessTime,
                    tone = Tone.NORMAL,
                    stale = stale,
                    caption = UiFormat.clock(telemetry.timestampMillis),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
