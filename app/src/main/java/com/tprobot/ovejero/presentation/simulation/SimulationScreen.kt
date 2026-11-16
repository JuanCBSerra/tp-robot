package com.tprobot.ovejero.presentation.simulation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.core.theme.CardBorder
import com.tprobot.ovejero.core.theme.VerdeMonte
import com.tprobot.ovejero.domain.model.Scenario
import com.tprobot.ovejero.domain.model.SimulationAction
import com.tprobot.ovejero.domain.model.SimulationSettings
import com.tprobot.ovejero.domain.model.SimulationSpeed
import com.tprobot.ovejero.presentation.common.components.ErrorStateView
import com.tprobot.ovejero.presentation.common.components.LoadingStateView
import com.tprobot.ovejero.presentation.common.components.SectionCard
import com.tprobot.ovejero.presentation.common.components.StatusChip
import com.tprobot.ovejero.presentation.common.components.Tone
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationScreen(
    onBackClick: () -> Unit,
    viewModel: SimulationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collectLatest { effect ->
            when (effect) {
                is SimulationEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panel de simulación") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VerdeMonte,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        when (val state = uiState) {
            is UiState.Idle, is UiState.Loading -> LoadingStateView("Conectando con el simulador…", Modifier.padding(innerPadding))
            is UiState.Error -> ErrorStateView(state.message, onRetry = { viewModel.observeSettings() }, modifier = Modifier.padding(innerPadding))
            is UiState.Success -> SimulationContent(state.data, viewModel::apply, Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun SimulationContent(
    settings: SimulationSettings,
    onAction: (SimulationAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Esto no es parte de la HMI del operador: sirve para demostrar los escenarios de la consigna. " +
                "Cargar un escenario reinicia el robot simulado en el galpón.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionCard(title = "Escenarios", icon = Icons.Filled.Science) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Scenario.entries.forEach { scenario ->
                    ScenarioOption(
                        scenario = scenario,
                        selected = settings.scenario == scenario,
                        onClick = { onAction(SimulationAction.LoadScenario(scenario)) }
                    )
                }
            }
        }

        SectionCard(title = "Velocidad del reloj", icon = Icons.Filled.Speed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimulationSpeed.entries.forEach { speed ->
                    FilterChip(
                        selected = settings.speed == speed,
                        onClick = { onAction(SimulationAction.SetSpeed(speed)) },
                        label = { Text(speed.label) }
                    )
                }
            }
            Text(
                "Con x10 una misión de arreo dura ~1,5 min (ideal para el video).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        SectionCard(title = "Forzar fallas", icon = Icons.Filled.Bolt) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Cortar el enlace", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Deja de llegar telemetría y las órdenes se pierden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.linkDown,
                        onCheckedChange = { onAction(SimulationAction.SetLinkDown(it)) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (settings.rejectNextCommand) StatusChip("Próxima: rechazo", Tone.ERROR)
                    if (settings.ignoreNextCommand) StatusChip("Próxima: sin respuesta", Tone.ADVERTENCIA)
                }
                ActionButton("Rechazar la próxima orden") { onAction(SimulationAction.RejectNextCommand) }
                ActionButton("No responder la próxima orden") { onAction(SimulationAction.IgnoreNextCommand) }
                ActionButton("Bajar batería al 22 %") { onAction(SimulationAction.DrainBattery) }
                ActionButton("Alambrado caído (obstáculo)") { onAction(SimulationAction.TriggerObstacle) }
                ActionButton("Se viene una tormenta") { onAction(SimulationAction.TriggerStorm) }
                ActionButton("Persona cerca del robot") { onAction(SimulationAction.TriggerPersonNearby) }
            }
        }

        SectionCard(title = "Últimos mensajes JSON", icon = Icons.Filled.Code) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                JsonBlock("command (app → robot)", settings.lastCommandJson)
                JsonBlock("command_ack (robot → app)", settings.lastResponseJson)
                JsonBlock("alert (robot → app)", settings.lastAlertJson)
                JsonBlock("telemetry (robot → app)", settings.lastTelemetryJson)
            }
        }
    }
}

@Composable
private fun ScenarioOption(scenario: Scenario, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) VerdeMonte else CardBorder),
        color = if (selected) VerdeMonte.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(scenario.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (selected) StatusChip("Cargado", Tone.OK)
            }
            Text(scenario.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Text(label)
    }
}

@Composable
private fun JsonBlock(title: String, json: String?) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            json ?: "(todavía nada)",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
            color = Color(0xFFB5E3A6)
        )
    }
}
