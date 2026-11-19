package com.tprobot.ovejero.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tprobot.ovejero.core.navigation.Screen
import com.tprobot.ovejero.core.theme.TierraColorada
import com.tprobot.ovejero.domain.model.ConnectionState
import com.tprobot.ovejero.presentation.alerts.AlertsScreen
import com.tprobot.ovejero.presentation.common.components.ConnectionBanner
import com.tprobot.ovejero.presentation.control.ControlScreen
import com.tprobot.ovejero.presentation.history.HistoryScreen
import com.tprobot.ovejero.presentation.robotselection.RobotSelectionScreen
import com.tprobot.ovejero.presentation.shell.ShellEffect
import com.tprobot.ovejero.presentation.shell.ShellViewModel
import com.tprobot.ovejero.presentation.simulation.SimulationScreen
import com.tprobot.ovejero.presentation.supervision.SupervisionScreen
import kotlinx.coroutines.flow.collectLatest

data class BottomNavItem(
    val title: String,
    val route: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem("Panel", Screen.Supervision.route, Icons.Filled.Dashboard),
    BottomNavItem("Control", Screen.Control.route, Icons.Filled.SportsEsports),
    BottomNavItem("Alertas", Screen.Alerts.route, Icons.Filled.Notifications),
    BottomNavItem("Historial", Screen.History.route, Icons.Filled.History)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OvejeroApp(
    shellViewModel: ShellViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val status by shellViewModel.status.collectAsStateWithLifecycle()
    val alertCount by shellViewModel.unacknowledgedAlerts.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }

    // Barra superior / inferior solo en las pestañas principales (con robot conectado)
    val showChrome = currentRoute in bottomNavItems.map { it.route }

    LaunchedEffect(Unit) {
        shellViewModel.effectFlow.collectLatest { effect ->
            when (effect) {
                is ShellEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
                ShellEffect.NavigateToSelection ->
                    navController.navigate(Screen.RobotSelection.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
            }
        }
    }

    if (confirmDisconnect) {
        val missionRunning = status.telemetry?.mission?.isActive == true
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("¿Desconectarte de ${status.robot?.name ?: "el robot"}?") },
            text = {
                Text(
                    if (missionRunning) {
                        "Hay una misión en curso. El robot va a seguir trabajando solo, pero vos dejás de ver lo que hace y no vas a poder intervenir."
                    } else {
                        "Vas a volver a la selección de robot."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    shellViewModel.disconnect()
                }) { Text("Desconectar") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Quedarme") } }
        )
    }

    Scaffold(
        topBar = {
            if (showChrome) {
                TopAppBar(
                    title = {
                        Column {
                            Text(status.robot?.name ?: "El Ovejero")
                            Text(
                                status.connection.label +
                                    batterySuffix(status) +
                                    (status.robot?.let { " · ${it.model}" } ?: ""),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(Screen.Simulation.route) }) {
                            Icon(Icons.Filled.Science, contentDescription = "Panel de simulación")
                        }
                        IconButton(onClick = { confirmDisconnect = true }) {
                            Icon(Icons.Filled.LinkOff, contentDescription = "Desconectar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = TierraColorada,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            if (showChrome) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { navController.navigateToTab(item.route) },
                            icon = {
                                if (item.route == Screen.Alerts.route && alertCount > 0) {
                                    BadgedBox(badge = { Badge { Text(alertCount.toString()) } }) {
                                        Icon(item.icon, contentDescription = item.title)
                                    }
                                } else {
                                    Icon(item.icon, contentDescription = item.title)
                                }
                            },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (showChrome && status.connection != ConnectionState.CONECTADO) {
                ConnectionBanner(status)
            }
            NavHost(
                navController = navController,
                startDestination = Screen.RobotSelection.route,
                modifier = Modifier.weight(1f)
            ) {
                composable(Screen.RobotSelection.route) {
                    RobotSelectionScreen(
                        onConnected = {
                            navController.navigate(Screen.Supervision.route) {
                                popUpTo(Screen.RobotSelection.route) { inclusive = true }
                            }
                        },
                        onOpenSimulation = { navController.navigate(Screen.Simulation.route) }
                    )
                }
                composable(Screen.Supervision.route) {
                    SupervisionScreen(onGoToControl = { navController.navigateToTab(Screen.Control.route) })
                }
                composable(Screen.Control.route) {
                    ControlScreen()
                }
                composable(Screen.Alerts.route) {
                    AlertsScreen(onGoToSupervision = { navController.navigateToTab(Screen.Supervision.route) })
                }
                composable(Screen.History.route) {
                    HistoryScreen()
                }
                composable(Screen.Simulation.route) {
                    SimulationScreen(onBackClick = { navController.popBackStack() })
                }
            }
        }
    }
}

/** Batería siempre visible en la barra superior; si no hay señal, se marca como dato viejo. */
private fun batterySuffix(status: com.tprobot.ovejero.domain.model.RobotStatus): String {
    val battery = status.telemetry?.batteryPercent ?: return ""
    val low = battery <= com.tprobot.ovejero.domain.model.OperationRules.LOW_BATTERY_WARNING
    return " · 🔋 $battery %" + when {
        status.isStale -> " (dato viejo)"
        low -> " ⚠️"
        else -> ""
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
