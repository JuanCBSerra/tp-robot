package com.tprobot.ovejero.presentation.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.domain.model.Alert
import com.tprobot.ovejero.domain.model.CommandAvailability
import com.tprobot.ovejero.domain.model.CommandRecord
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.MissionType
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
import com.tprobot.ovejero.domain.usecase.StartMissionUseCase
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

data class ControlData(
    val status: RobotStatus,
    val availability: Map<CommandType, CommandAvailability>,
    val commands: List<CommandRecord>,
    val selectedMissionType: MissionType,
    val oldestUnacknowledgedAlert: Alert?
)

sealed interface ControlEffect {
    data class ShowSnackbar(val message: String) : ControlEffect
}

/**
 * Misiones y control: elegir el tipo de misión, mandar todas las órdenes de la consigna
 * y ver el registro de órdenes con su estado.
 */
@HiltViewModel
class ControlViewModel @Inject constructor(
    private val observeRobotStatusUseCase: ObserveRobotStatusUseCase,
    private val observeAlertsUseCase: ObserveAlertsUseCase,
    private val observeCommandsUseCase: ObserveCommandsUseCase,
    private val getCommandAvailabilityUseCase: GetCommandAvailabilityUseCase,
    private val startMissionUseCase: StartMissionUseCase,
    private val pauseMissionUseCase: PauseMissionUseCase,
    private val resumeMissionUseCase: ResumeMissionUseCase,
    private val cancelMissionUseCase: CancelMissionUseCase,
    private val returnToBaseUseCase: ReturnToBaseUseCase,
    private val operationalStopUseCase: OperationalStopUseCase,
    private val acknowledgeAlertUseCase: AcknowledgeAlertUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ControlData>>(UiState.Idle)
    val uiState: StateFlow<UiState<ControlData>> = _uiState.asStateFlow()

    private val _effectFlow = MutableSharedFlow<ControlEffect>()
    val effectFlow: SharedFlow<ControlEffect> = _effectFlow.asSharedFlow()

    private val selectedMissionType = MutableStateFlow(MissionType.ARREO)

    init {
        observeControl()
    }

    fun observeControl() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            combine(
                observeRobotStatusUseCase(),
                observeAlertsUseCase(),
                observeCommandsUseCase(),
                selectedMissionType
            ) { status, alerts, commands, missionType ->
                ControlData(
                    status = status,
                    availability = getCommandAvailabilityUseCase(status, alerts, commands),
                    commands = commands,
                    selectedMissionType = missionType,
                    oldestUnacknowledgedAlert = alerts.filter { !it.acknowledged }.minByOrNull { it.timestampMillis }
                )
            }
                .catch { e -> _uiState.value = UiState.Error("No se pudo leer el estado del robot: ${e.message}") }
                .collect { _uiState.value = UiState.Success(it) }
        }
    }

    fun selectMissionType(type: MissionType) {
        selectedMissionType.value = type
    }

    fun startMission() = runCommand("Iniciar misión") { startMissionUseCase(selectedMissionType.value) }
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
                    _effectFlow.emit(ControlEffect.ShowSnackbar("$label: enviada (${result.data.id}), esperando confirmación…"))
                is Resource.Error ->
                    _effectFlow.emit(ControlEffect.ShowSnackbar("$label: ${result.message}"))
            }
        }
    }
}
