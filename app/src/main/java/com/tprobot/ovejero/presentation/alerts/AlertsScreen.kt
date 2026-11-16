package com.tprobot.ovejero.presentation.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.domain.model.Alert
import com.tprobot.ovejero.domain.model.AlertSeverity
import com.tprobot.ovejero.presentation.common.UiFormat
import com.tprobot.ovejero.presentation.common.components.EmptyStateView
import com.tprobot.ovejero.presentation.common.components.ErrorStateView
import com.tprobot.ovejero.presentation.common.components.LoadingStateView
import com.tprobot.ovejero.presentation.common.components.StatusChip
import com.tprobot.ovejero.presentation.common.components.Tone
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AlertsScreen(
    onGoToSupervision: () -> Unit,
    viewModel: AlertsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collectLatest { effect ->
            when (effect) {
                is AlertsEffect.ShowSnackbar ->
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
                LoadingStateView("Cargando alertas…", Modifier.padding(innerPadding))
            is UiState.Error ->
                ErrorStateView(state.message, onRetry = { viewModel.observeAlerts() }, modifier = Modifier.padding(innerPadding))
            is UiState.Success -> {
                val data = state.data
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text("Pendientes de reconocer (${data.active.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    if (data.active.isEmpty()) {
                        item { EmptyStateView("No hay alertas pendientes. Todo tranqui en la chacra.", Icons.Filled.NotificationsNone) }
                    }
                    items(data.active, key = { it.id }) { alert ->
                        AlertCard(
                            alert = alert,
                            ackPending = alert.id in data.pendingAckIds,
                            canAcknowledge = data.canAcknowledge,
                            onAcknowledge = { viewModel.acknowledge(alert.id) },
                            onGoToSupervision = onGoToSupervision
                        )
                    }
                    if (data.acknowledged.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text("Reconocidas (${data.acknowledged.size})", style = MaterialTheme.typography.titleMedium)
                        }
                        items(data.acknowledged, key = { it.id }) { alert ->
                            AlertCard(
                                alert = alert,
                                ackPending = false,
                                canAcknowledge = false,
                                onAcknowledge = {},
                                onGoToSupervision = onGoToSupervision
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: Alert,
    ackPending: Boolean,
    canAcknowledge: Boolean,
    onAcknowledge: () -> Unit,
    onGoToSupervision: () -> Unit
) {
    val tone = if (alert.acknowledged) Tone.DESHABILITADO else UiFormat.severityTone(alert.severity)
    val icon = when (alert.severity) {
        AlertSeverity.INFO -> Icons.Filled.Info
        AlertSeverity.ADVERTENCIA -> Icons.Filled.WarningAmber
        AlertSeverity.CRITICA -> Icons.Filled.ErrorOutline
    }
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (alert.acknowledged) 0.75f else 1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tone.container.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, tone.content.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tone.content)
                Spacer(Modifier.width(8.dp))
                Text(alert.type.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(alert.severity.label, tone)
            }
            Text(alert.message, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${alert.id} · ${UiFormat.clock(alert.timestampMillis)}" + (alert.zoneName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (alert.acknowledged) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Tone.OK.content, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reconocida (confirmado por el robot)", color = Tone.OK.content)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onAcknowledge,
                        enabled = canAcknowledge && !ackPending,
                        colors = ButtonDefaults.buttonColors(containerColor = tone.content)
                    ) {
                        if (ackPending) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Pendiente…")
                        } else {
                            Text("Reconocer")
                        }
                    }
                    if (alert.requiresDecision) {
                        androidx.compose.material3.TextButton(onClick = onGoToSupervision) { Text("Decidir en el panel") }
                    }
                }
                if (!canAcknowledge) {
                    Text("Sin conexión: no se puede reconocer ahora.", style = MaterialTheme.typography.bodyMedium, color = Tone.ERROR.content)
                }
            }
        }
    }
}
