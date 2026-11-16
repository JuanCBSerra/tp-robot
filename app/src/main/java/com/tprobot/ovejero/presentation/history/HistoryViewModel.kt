package com.tprobot.ovejero.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.domain.model.HistoryEvent
import com.tprobot.ovejero.domain.model.MissionRecord
import com.tprobot.ovejero.domain.usecase.ClearHistoryUseCase
import com.tprobot.ovejero.domain.usecase.ObserveEventHistoryUseCase
import com.tprobot.ovejero.domain.usecase.ObserveMissionHistoryUseCase
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

data class HistoryData(
    val missions: List<MissionRecord>,
    val events: List<HistoryEvent>
)

sealed interface HistoryEffect {
    data class ShowSnackbar(val message: String) : HistoryEffect
}

/** Historial persistido en Room: sobrevive al cierre de la app. */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val observeMissionHistoryUseCase: ObserveMissionHistoryUseCase,
    private val observeEventHistoryUseCase: ObserveEventHistoryUseCase,
    private val clearHistoryUseCase: ClearHistoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<HistoryData>>(UiState.Idle)
    val uiState: StateFlow<UiState<HistoryData>> = _uiState.asStateFlow()

    private val _effectFlow = MutableSharedFlow<HistoryEffect>()
    val effectFlow: SharedFlow<HistoryEffect> = _effectFlow.asSharedFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            combine(observeMissionHistoryUseCase(), observeEventHistoryUseCase()) { missions, events ->
                HistoryData(missions, events)
            }
                .catch { e -> _uiState.value = UiState.Error("No se pudo leer el historial: ${e.message}") }
                .collect { _uiState.value = UiState.Success(it) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            when (val result = clearHistoryUseCase()) {
                is Resource.Success -> _effectFlow.emit(HistoryEffect.ShowSnackbar("Historial borrado"))
                is Resource.Error -> _effectFlow.emit(HistoryEffect.ShowSnackbar(result.message))
            }
        }
    }
}
