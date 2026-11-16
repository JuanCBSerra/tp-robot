package com.tprobot.ovejero.presentation.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.domain.model.Alert
import com.tprobot.ovejero.domain.model.CommandStatus
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.ConnectionState
import com.tprobot.ovejero.domain.usecase.AcknowledgeAlertUseCase
import com.tprobot.ovejero.domain.usecase.ObserveAlertsUseCase
import com.tprobot.ovejero.domain.usecase.ObserveCommandsUseCase
import com.tprobot.ovejero.domain.usecase.ObserveRobotStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertsData(
    val alerts: List<Alert>,
    /** Alertas con un reconocimiento enviado que todavía no confirmó el robot. */
    val pendingAckIds: Set<String>,
    val canAcknowledge: Boolean
) {
    val active: List<Alert> get() = alerts.filter { !it.acknowledged }
    val acknowledged: List<Alert> get() = alerts.filter { it.acknowledged }
}

sealed interface AlertsEffect {
    data class ShowSnackbar(val message: String) : AlertsEffect
}

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val observeAlertsUseCase: ObserveAlertsUseCase,
    private val observeCommandsUseCase: ObserveCommandsUseCase,
    private val observeRobotStatusUseCase: ObserveRobotStatusUseCase,
    private val acknowledgeAlertUseCase: AcknowledgeAlertUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<AlertsData>>(UiState.Idle)
    val uiState: StateFlow<UiState<AlertsData>> = _uiState.asStateFlow()

    private val _effectFlow = MutableSharedFlow<AlertsEffect>()
    val effectFlow: SharedFlow<AlertsEffect> = _effectFlow.asSharedFlow()

    init {
        observeAlerts()
    }

    fun observeAlerts() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            combine(
                observeAlertsUseCase(),
                observeCommandsUseCase(),
                observeRobotStatusUseCase()
            ) { alerts, commands, status ->
                AlertsData(
                    alerts = alerts,
                    pendingAckIds = commands
                        .filter { it.command.type == CommandType.RECONOCER_ALERTA && it.status == CommandStatus.PENDIENTE }
                        .mapNotNull { it.command.alertId }
                        .toSet(),
                    canAcknowledge = status.connection == ConnectionState.CONECTADO
                )
            }
                .catch { e -> _uiState.value = UiState.Error("No se pudieron leer las alertas: ${e.message}") }
                .collect { _uiState.value = UiState.Success(it) }
        }
    }

    fun acknowledge(alertId: String) {
        viewModelScope.launch {
            when (val result = acknowledgeAlertUseCase(alertId)) {
                is Resource.Success ->
                    _effectFlow.emit(AlertsEffect.ShowSnackbar("Reconocimiento de $alertId enviado, esperando confirmación…"))
                is Resource.Error -> _effectFlow.emit(AlertsEffect.ShowSnackbar(result.message))
            }
        }
    }
}
