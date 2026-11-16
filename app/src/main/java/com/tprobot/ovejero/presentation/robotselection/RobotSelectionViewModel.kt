package com.tprobot.ovejero.presentation.robotselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.domain.model.Robot
import com.tprobot.ovejero.domain.usecase.ConnectRobotUseCase
import com.tprobot.ovejero.domain.usecase.GetRobotsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RobotSelectionEffect {
    data class ShowSnackbar(val message: String) : RobotSelectionEffect
    data object NavigateToDashboard : RobotSelectionEffect
}

@HiltViewModel
class RobotSelectionViewModel @Inject constructor(
    private val getRobotsUseCase: GetRobotsUseCase,
    private val connectRobotUseCase: ConnectRobotUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Robot>>>(UiState.Idle)
    val uiState: StateFlow<UiState<List<Robot>>> = _uiState.asStateFlow()

    /** Id del robot al que se está conectando (para mostrar el spinner en su tarjeta). */
    private val _connectingRobotId = MutableStateFlow<String?>(null)
    val connectingRobotId: StateFlow<String?> = _connectingRobotId.asStateFlow()

    private val _effectFlow = MutableSharedFlow<RobotSelectionEffect>()
    val effectFlow: SharedFlow<RobotSelectionEffect> = _effectFlow.asSharedFlow()

    init {
        loadRobots()
    }

    fun loadRobots() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            when (val result = getRobotsUseCase()) {
                is Resource.Success -> _uiState.value = UiState.Success(result.data)
                is Resource.Error -> _uiState.value = UiState.Error(result.message)
            }
        }
    }

    fun connect(robot: Robot) {
        if (_connectingRobotId.value != null) return
        _connectingRobotId.value = robot.id
        viewModelScope.launch {
            when (val result = connectRobotUseCase(robot)) {
                is Resource.Success -> {
                    _effectFlow.emit(RobotSelectionEffect.ShowSnackbar("Conectado a ${result.data.name}"))
                    _effectFlow.emit(RobotSelectionEffect.NavigateToDashboard)
                }
                is Resource.Error -> _effectFlow.emit(RobotSelectionEffect.ShowSnackbar(result.message))
            }
            _connectingRobotId.value = null
        }
    }
}
