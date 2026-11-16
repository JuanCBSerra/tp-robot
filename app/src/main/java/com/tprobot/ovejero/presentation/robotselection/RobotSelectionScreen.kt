package com.tprobot.ovejero.presentation.robotselection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.core.theme.CardBorder
import com.tprobot.ovejero.core.theme.TierraColorada
import com.tprobot.ovejero.domain.model.Robot
import com.tprobot.ovejero.domain.model.RobotAvailability
import com.tprobot.ovejero.presentation.common.UiFormat
import com.tprobot.ovejero.presentation.common.components.ErrorStateView
import com.tprobot.ovejero.presentation.common.components.LoadingStateView
import com.tprobot.ovejero.presentation.common.components.StatusChip
import com.tprobot.ovejero.presentation.common.components.Tone
import kotlinx.coroutines.flow.collectLatest

/**
 * Pantalla inicial: selección del robot de la flota.
 * StateFlow para la lista + SharedFlow para eventos (snackbar y navegación).
 */
@Composable
fun RobotSelectionScreen(
    onConnected: () -> Unit,
    onOpenSimulation: () -> Unit,
    viewModel: RobotSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connectingId by viewModel.connectingRobotId.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collectLatest { effect ->
            when (effect) {
                is RobotSelectionEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
                RobotSelectionEffect.NavigateToDashboard -> onConnected()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Header(onRefresh = { viewModel.loadRobots() }, onOpenSimulation = onOpenSimulation)
            when (val state = uiState) {
                is UiState.Idle, is UiState.Loading -> LoadingStateView("Buscando robots de la flota…")
                is UiState.Error -> ErrorStateView(state.message, onRetry = { viewModel.loadRobots() })
                is UiState.Success -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.data, key = { it.id }) { robot ->
                        RobotCard(
                            robot = robot,
                            connecting = connectingId == robot.id,
                            anyConnecting = connectingId != null,
                            onConnect = { viewModel.connect(robot) }
                        )
                    }
                    item {
                        Text(
                            "Robot simulado: la app no necesita robot, servidor ni internet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(onRefresh: () -> Unit, onOpenSimulation: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(TierraColorada)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Pets, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("El Ovejero", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text("Chacra La Colorada · Oberá, Misiones", color = Color.White.copy(alpha = 0.85f))
            }
            TextButton(onClick = onRefresh) { Text("Actualizar", color = Color.White) }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Elegí el robot que vas a supervisar",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenSimulation) { Text("Simulación", color = Color.White) }
        }
    }
}

@Composable
private fun RobotCard(robot: Robot, connecting: Boolean, anyConnecting: Boolean, onConnect: () -> Unit) {
    val tone = when (robot.availability) {
        RobotAvailability.DISPONIBLE -> Tone.OK
        RobotAvailability.EN_MANTENIMIENTO -> Tone.ADVERTENCIA
        RobotAvailability.FUERA_DE_ALCANCE -> Tone.DESHABILITADO
    }
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (robot.isSelectable) 1f else 0.7f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(robot.name, style = MaterialTheme.typography.titleLarge)
                    Text(robot.model, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(robot.availability.label, tone)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(robot.location, style = MaterialTheme.typography.bodyMedium)
            }
            if (robot.isSelectable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.BatteryStd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = UiFormat.batteryTone(robot.batteryPercent).content
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Batería ${robot.batteryPercent} %", style = MaterialTheme.typography.bodyMedium)
                }
            }
            robot.note?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = tone.content, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onConnect,
                enabled = robot.isSelectable && !anyConnecting,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (connecting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Conectando…")
                } else {
                    Text(if (robot.isSelectable) "Conectar" else "No disponible")
                }
            }
        }
    }
}
