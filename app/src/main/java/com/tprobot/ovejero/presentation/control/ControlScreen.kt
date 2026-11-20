package com.tprobot.ovejero.presentation.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.core.theme.CardBorder
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.MissionType
import com.tprobot.ovejero.domain.model.OperationRules
import com.tprobot.ovejero.presentation.common.UiFormat
import com.tprobot.ovejero.presentation.common.components.CommandButton
import com.tprobot.ovejero.presentation.common.components.CommandRecordRow
import com.tprobot.ovejero.presentation.common.components.ConfirmCommandDialog
import com.tprobot.ovejero.presentation.common.components.EmptyStateView
import com.tprobot.ovejero.presentation.common.components.ErrorStateView
import com.tprobot.ovejero.presentation.common.components.HoldToConfirmButton
import com.tprobot.ovejero.presentation.common.components.LoadingStateView
import com.tprobot.ovejero.presentation.common.components.SectionCard
import com.tprobot.ovejero.presentation.common.BatteryRequirementUi
import com.tprobot.ovejero.presentation.common.components.Tone
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ControlScreen(
    viewModel: ControlViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    var confirmReturn by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collectLatest { effect ->
            when (effect) {
                is ControlEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("¿Cancelar la misión?") },
            text = { Text("El robot deja la recorrida y vuelve al galpón. Los animales que estaba arreando quedan donde estén.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    viewModel.cancel()
                }) { Text("Sí, cancelar") }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Seguir con la misión") } }
        )
    }

    if (confirmReturn) {
        ConfirmCommandDialog(
            title = "¿Volver al galpón?",
            text = "El robot interrumpe lo que está haciendo y vuelve al punto seguro. Si hay una misión en curso, se corta.",
            confirmLabel = "Sí, volver",
            dismissLabel = "Quedarme",
            onConfirm = {
                confirmReturn = false
                viewModel.returnToBase()
            },
            onDismiss = { confirmReturn = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        when (val state = uiState) {
            is UiState.Idle, is UiState.Loading ->
                LoadingStateView("Cargando controles…", Modifier.padding(innerPadding))
            is UiState.Error ->
                ErrorStateView(state.message, onRetry = { viewModel.observeControl() }, modifier = Modifier.padding(innerPadding))
            is UiState.Success -> ControlContent(
                data = state.data,
                onSelectMission = viewModel::selectMissionType,
                onStart = viewModel::startMission,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onCancel = { confirmCancel = true },
                onReturn = { confirmReturn = true },
                onStop = viewModel::operationalStop,
                onAcknowledge = viewModel::acknowledge,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun ControlContent(
    data: ControlData,
    onSelectMission: (MissionType) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onReturn: () -> Unit,
    onStop: () -> Unit,
    onAcknowledge: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val availability = data.availability
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard(title = "Nueva misión", icon = Icons.Filled.Flag) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MissionType.entries.forEach { type ->
                    MissionOption(
                        type = type,
                        selected = data.selectedMissionType == type,
                        onSelect = { onSelectMission(type) }
                    )
                }
                Spacer(Modifier.height(4.dp))
                CommandButton(
                    label = "Iniciar ${data.selectedMissionType.label.lowercase()}",
                    icon = UiFormat.commandIcon(CommandType.INICIAR_MISION),
                    availability = availability[CommandType.INICIAR_MISION],
                    onClick = onStart,
                    primary = true
                )
                BatteryRequirement(data.batteryRequirement)
                Text(
                    "Requisitos: robot en el galpón, sin misión activa y batería ≥ ${OperationRules.MIN_BATTERY_TO_START} %.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard(title = "Órdenes", icon = Icons.Filled.SettingsRemote) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CommandButton(
                        label = "Pausar",
                        icon = UiFormat.commandIcon(CommandType.PAUSAR),
                        availability = availability[CommandType.PAUSAR],
                        onClick = onPause,
                        modifier = Modifier.weight(1f)
                    )
                    CommandButton(
                        label = "Reanudar",
                        icon = UiFormat.commandIcon(CommandType.REANUDAR),
                        availability = availability[CommandType.REANUDAR],
                        onClick = onResume,
                        modifier = Modifier.weight(1f)
                    )
                }
                CommandButton(
                    label = "Cancelar misión",
                    icon = UiFormat.commandIcon(CommandType.CANCELAR),
                    availability = availability[CommandType.CANCELAR],
                    onClick = onCancel
                )
                CommandButton(
                    label = "Volver al galpón (punto seguro)",
                    icon = UiFormat.commandIcon(CommandType.VOLVER_A_BASE),
                    availability = availability[CommandType.VOLVER_A_BASE],
                    onClick = onReturn
                )
                val alert = data.oldestUnacknowledgedAlert
                CommandButton(
                    label = if (alert != null) "Reconocer ${alert.id} · ${alert.type.label}" else "Reconocer alerta",
                    icon = UiFormat.commandIcon(CommandType.RECONOCER_ALERTA),
                    availability = availability[CommandType.RECONOCER_ALERTA],
                    onClick = { alert?.let { onAcknowledge(it.id) } }
                )
                HorizontalDivider()
                HoldToConfirmButton(
                    label = "Detención operativa",
                    availability = availability[CommandType.DETENCION_OPERATIVA],
                    onConfirmed = onStop
                )
            }
        }

        SectionCard(title = "Registro de órdenes", icon = Icons.Filled.History) {
            if (data.commands.isEmpty()) {
                EmptyStateView("Todavía no mandaste órdenes en esta conexión.", Icons.Filled.History)
            } else {
                Column {
                    data.commands.forEachIndexed { index, record ->
                        CommandRecordRow(record)
                        if (index < data.commands.lastIndex) HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Una orden enviada no es una orden ejecutada: queda pendiente hasta que el robot responde. " +
                    "Si en ${OperationRules.COMMAND_TIMEOUT_MILLIS / 1000} s no hay respuesta, se marca sin respuesta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MissionOption(type: MissionType, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else CardBorder),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column {
                Text(type.label, style = MaterialTheme.typography.titleMedium)
                Text(type.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Batería frente al mínimo para iniciar: el texto y el color ya vienen resueltos por el ViewModel. */
@Composable
private fun BatteryRequirement(requirement: BatteryRequirementUi) {
    Text(
        requirement.text,
        style = MaterialTheme.typography.titleMedium,
        color = requirement.tone?.content ?: MaterialTheme.colorScheme.onSurfaceVariant
    )
}
