package com.tprobot.ovejero.presentation.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.tprobot.ovejero.domain.model.EventCategory
import com.tprobot.ovejero.domain.model.HistoryEvent
import com.tprobot.ovejero.domain.model.MissionRecord
import com.tprobot.ovejero.domain.model.MissionType
import com.tprobot.ovejero.presentation.common.UiFormat
import com.tprobot.ovejero.presentation.common.components.EmptyStateView
import com.tprobot.ovejero.presentation.common.components.ErrorStateView
import com.tprobot.ovejero.presentation.common.components.LoadingStateView
import com.tprobot.ovejero.presentation.common.components.StatusChip
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collectLatest { effect ->
            when (effect) {
                is HistoryEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar el historial?") },
            text = { Text("Se borran todas las misiones y eventos guardados en el celular. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearHistory()
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Misiones") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Eventos") })
            }
            when (val state = uiState) {
                is UiState.Idle, is UiState.Loading -> LoadingStateView("Leyendo historial…")
                is UiState.Error -> ErrorStateView(state.message, onRetry = { viewModel.loadHistory() })
                is UiState.Success -> {
                    val data = state.data
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (selectedTab == 0) "${data.missions.size} misiones guardadas" else "Últimos ${data.events.size} eventos",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { confirmClear = true },
                                    enabled = data.missions.isNotEmpty() || data.events.isNotEmpty()
                                ) {
                                    Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Borrar")
                                }
                            }
                        }
                        if (selectedTab == 0) {
                            if (data.missions.isEmpty()) {
                                item { EmptyStateView("Todavía no terminó ninguna misión.", Icons.Filled.Flag) }
                            }
                            items(data.missions, key = { it.missionId }) { MissionRow(it) }
                        } else {
                            if (data.events.isEmpty()) {
                                item { EmptyStateView("No hay eventos registrados.", Icons.AutoMirrored.Filled.EventNote) }
                            }
                            items(data.events, key = { it.id }) { event ->
                                EventRow(event)
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionRow(record: MissionRecord) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(record.type.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${record.missionId} · ${record.robotName} · ${UiFormat.dateTime(record.startedAtMillis)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(record.result.label, UiFormat.missionTone(record.result))
            }
            Text(
                buildString {
                    append("Contados ${record.headsCounted}/${record.headsExpected}")
                    if (record.type == MissionType.ARREO) append(" · en corral ${record.headsInCorral}")
                    append(" · ${record.durationMinutes} min · batería usada ${record.batteryUsedPercent} %")
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun EventRow(event: HistoryEvent) {
    val icon = when (event.category) {
        EventCategory.MISION -> Icons.Filled.Flag
        EventCategory.COMANDO -> Icons.Filled.SettingsRemote
        EventCategory.ALERTA -> Icons.Filled.NotificationsActive
        EventCategory.CONEXION -> Icons.Filled.Wifi
        EventCategory.SISTEMA -> Icons.Filled.Build
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = event.category.label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.bodyLarge)
            Text(event.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(UiFormat.clock(event.timestampMillis), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
