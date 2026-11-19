package com.tprobot.ovejero.presentation.simulation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.domain.model.SimulationAction
import com.tprobot.ovejero.domain.model.SimulationSettings
import com.tprobot.ovejero.domain.usecase.ApplySimulationActionUseCase
import com.tprobot.ovejero.domain.usecase.ObserveSimulationSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SimulationEffect {
    data class ShowSnackbar(val message: String) : SimulationEffect
}

/**
 * Panel de simulación: carga escenarios, cambia la velocidad del reloj y fuerza fallas
 * (desconexión, rechazos, batería, obstáculo, tormenta, persona cerca).
 * Pasa por casos de uso y SimulationRepository: no toca el simulador directamente.
 */
@HiltViewModel
class SimulationViewModel @Inject constructor(
    private val observeSimulationSettingsUseCase: ObserveSimulationSettingsUseCase,
    private val applySimulationActionUseCase: ApplySimulationActionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<SimulationSettings>>(UiState.Idle)
    val uiState: StateFlow<UiState<SimulationSettings>> = _uiState.asStateFlow()

    private val _effectFlow = MutableSharedFlow<SimulationEffect>()
    val effectFlow: SharedFlow<SimulationEffect> = _effectFlow.asSharedFlow()

    init {
        observeSettings()
    }

    fun observeSettings() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            observeSimulationSettingsUseCase()
                .catch { e -> _uiState.value = UiState.Error("No se pudo leer el simulador: ${e.message}") }
                .collect { _uiState.value = UiState.Success(it) }
        }
    }

    fun apply(action: SimulationAction) {
        viewModelScope.launch {
            when (val result = applySimulationActionUseCase(action)) {
                is Resource.Success -> describe(action)?.let { _effectFlow.emit(SimulationEffect.ShowSnackbar(it)) }
                is Resource.Error -> _effectFlow.emit(SimulationEffect.ShowSnackbar(result.message))
            }
        }
    }

    private fun describe(action: SimulationAction): String? = when (action) {
        is SimulationAction.LoadScenario -> "Escenario cargado: ${action.scenario.label}. Iniciá una misión desde Control."
        is SimulationAction.SetSpeed -> null
        is SimulationAction.SetLinkDown -> if (action.down) "Enlace cortado" else "Enlace restablecido"
        SimulationAction.RejectNextCommand -> "La próxima orden va a ser rechazada"
        SimulationAction.IgnoreNextCommand -> "La próxima orden no va a tener respuesta"
        SimulationAction.DrainBattery -> "Batería bajada al 22 %"
        SimulationAction.TriggerObstacle -> "Obstáculo disparado"
        SimulationAction.TriggerStorm -> "Tormenta disparada"
        SimulationAction.TriggerHeat -> "Ola de calor disparada"
        SimulationAction.TriggerPersonNearby -> "Persona cerca disparada"
    }
}
