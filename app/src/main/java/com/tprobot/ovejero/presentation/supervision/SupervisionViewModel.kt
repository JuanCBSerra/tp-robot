package com.tprobot.ovejero.presentation.supervision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.domain.model.Alert
import com.tprobot.ovejero.domain.model.CommandAvailability
import com.tprobot.ovejero.domain.model.CommandRecord
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.OperationalState
import com.tprobot.ovejero.domain.model.RobotStatus
import com.tprobot.ovejero.domain.usecase.AcknowledgeAlertUseCase
import com.tprobot.ovejero.domain.usecase.CancelMissionUseCase
import com.tprobot.ovejero.domain.usecase.GetCommandAvailabilityUseCase
import com.tprobot.ovejero.domain.usecase.ObserveAlertsUseCase
import com.tprobot.ovejero.domain.usecase.ObserveCommandsUseCase
import com.tprobot.ovejero.domain.usecase.ObserveRobotStatusUseCase
import com.tprobot.ovejero.domain.usecase.OperationalStopUseCase
import com.tprobot.ovejero.domain.usecase.PauseMissionUseCase
import com.tprobot.ovejero.domain.usecase.ResumeMissionUseCase
import com.tprobot.ovejero.domain.usecase.ReturnToBaseUseCase
import com.tprobot.ovejero.presentation.common.StatusPresenter
import com.tprobot.ovejero.presentation.common.TelemetryPanelUi
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

/** Todo lo que necesita el panel de supervisión, ya combinado. */
data class SupervisionData(
    val status: RobotStatus,
    val alerts: List<Alert>,
    val availability: Map<CommandType, CommandAvailability>,
    val pendingDecision: Alert?,
    val lastCommand: CommandRecord?,
    /** Celdas de telemetría ya resueltas (textos y tonos); nulo si todavía no llegó telemetría. */
    val telemetryPanel: TelemetryPanelUi? = null
) {
    val unacknowledgedAlerts: Int get() = alerts.count { !it.acknowledged }
}

sealed interface SupervisionEffect {
    data class ShowSnackbar(val message: String) : SupervisionEffect
}

@HiltViewModel
class SupervisionViewModel @Inject constructor(
    private val observeRobotStatusUseCase: ObserveRobotStatusUseCase,
    private val observeAlertsUseCase: ObserveAlertsUseCase,
    private val observeCommandsUseCase: ObserveCommandsUseCase,
    private val getCommandAvailabilityUseCase: GetCommandAvailabilityUseCase,
    private val pauseMissionUseCase: PauseMissionUseCase,
    private val resumeMissionUseCase: ResumeMissionUseCase,
    private val cancelMissionUseCase: CancelMissionUseCase,
    private val returnToBaseUseCase: ReturnToBaseUseCase,
    private val operationalStopUseCase: OperationalStopUseCase,
    private val acknowledgeAlertUseCase: AcknowledgeAlertUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<SupervisionData>>(UiState.Idle)
    val uiState: StateFlow<UiState<SupervisionData>> = _uiState.asStateFlow()

    private val _effectFlow = MutableSharedFlow<SupervisionEffect>()
    val effectFlow: SharedFlow<SupervisionEffect> = _effectFlow.asSharedFlow()

    init {
        observeSupervision()
    }

    fun observeSupervision() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            combine(
                observeRobotStatusUseCase(),
                observeAlertsUseCase(),
                observeCommandsUseCase()
            ) { status, alerts, commands ->
                val waitingDecision = status.telemetry?.operationalState == OperationalState.ESPERANDO_DECISION
                SupervisionData(
                    status = status,
                    alerts = alerts,
                    availability = getCommandAvailabilityUseCase(status, alerts, commands),
                    pendingDecision = if (waitingDecision) {
                        alerts.filter { it.requiresDecision }.maxByOrNull { it.timestampMillis }
                    } else {
                        null
                    },
                    lastCommand = commands.firstOrNull(),
                    telemetryPanel = status.telemetry?.let { StatusPresenter.telemetryPanel(status, it) }
                )
            }
                .catch { e -> _uiState.value = UiState.Error("No se pudo leer el estado del robot: ${e.message}") }
                .collect { _uiState.value = UiState.Success(it) }
        }
    }

    fun pause() = runCommand("Pausar") { pauseMissionUseCase() }
    fun resume() = runCommand("Reanudar") { resumeMissionUseCase() }
    fun cancel() = runCommand("Cancelar misión") { cancelMissionUseCase() }
    fun returnToBase() = runCommand("Volver al galpón") { returnToBaseUseCase() }
    fun operationalStop() = runCommand("Detención operativa") { operationalStopUseCase() }
    fun acknowledge(alertId: String) = runCommand("Reconocer alerta") { acknowledgeAlertUseCase(alertId) }

    private fun runCommand(label: String, block: suspend () -> Resource<CommandRecord>) {
        viewModelScope.launch {
            when (val result = block()) {
                is Resource.Success ->
                    _effectFlow.emit(SupervisionEffect.ShowSnackbar("$label: enviada, esperando confirmación…"))
                is Resource.Error ->
                    _effectFlow.emit(SupervisionEffect.ShowSnackbar("$label: ${result.message}"))
            }
        }
    }
}
