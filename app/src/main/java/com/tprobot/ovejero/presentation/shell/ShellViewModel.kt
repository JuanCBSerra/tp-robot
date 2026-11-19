package com.tprobot.ovejero.presentation.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.model.AlertSeverity
import com.tprobot.ovejero.domain.model.CommandStatus
import com.tprobot.ovejero.domain.model.MissionStatus
import com.tprobot.ovejero.domain.model.RobotStatus
import com.tprobot.ovejero.domain.usecase.DisconnectRobotUseCase
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
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ShellEffect {
    data class ShowSnackbar(val message: String) : ShellEffect
    data object NavigateToSelection : ShellEffect
}

/**
 * ViewModel del "marco" de la app (barra superior, banner de conexión, barra inferior).
 * Avisa con un snackbar cuando una orden se resuelve, llega una alerta nueva o la misión
 * termina, esté el operador en la pestaña que esté.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val observeRobotStatusUseCase: ObserveRobotStatusUseCase,
    private val observeAlertsUseCase: ObserveAlertsUseCase,
    private val observeCommandsUseCase: ObserveCommandsUseCase,
    private val disconnectRobotUseCase: DisconnectRobotUseCase
) : ViewModel() {

    private val _status = MutableStateFlow(RobotStatus.disconnected(System.currentTimeMillis()))
    val status: StateFlow<RobotStatus> = _status.asStateFlow()

    private val _unacknowledgedAlerts = MutableStateFlow(0)
    val unacknowledgedAlerts: StateFlow<Int> = _unacknowledgedAlerts.asStateFlow()

    private val _effectFlow = MutableSharedFlow<ShellEffect>()
    val effectFlow: SharedFlow<ShellEffect> = _effectFlow.asSharedFlow()

    private val knownCommandStatus = mutableMapOf<String, CommandStatus>()
    private val knownAlertIds = mutableSetOf<String>()
    private val knownMissionStatus = mutableMapOf<String, MissionStatus>()

    init {
        observeStatus()
        observeAlerts()
        observeCommandResults()
    }

    private fun observeStatus() {
        viewModelScope.launch {
            observeRobotStatusUseCase().collect { status ->
                _status.value = status
                notifyMissionCompleted(status)
            }
        }
    }

    /**
     * Avisa una sola vez cuando una misión pasa de en curso/pausada a completada.
     * No avisa si la primera telemetría que vemos ya la trae completada (p. ej. al reconectar).
     */
    private suspend fun notifyMissionCompleted(status: RobotStatus) {
        val telemetry = status.telemetry ?: return
        val mission = telemetry.mission ?: return
        val previous = knownMissionStatus.put(mission.missionId, mission.status)
        val wasActive = previous == MissionStatus.EN_CURSO || previous == MissionStatus.PAUSADA
        if (wasActive && mission.status == MissionStatus.COMPLETADA) {
            val herd = telemetry.herd
            val message = if (herd.missing > 0) {
                "⚠️ Misión completada con faltantes: ${herd.counted} de ${herd.expected} animales contados"
            } else {
                "✅ Misión completada: ${herd.counted} de ${herd.expected} animales contados"
            }
            _effectFlow.emit(ShellEffect.ShowSnackbar(message))
        }
    }

    private fun observeAlerts() {
        viewModelScope.launch {
            observeAlertsUseCase().collect { alerts ->
                _unacknowledgedAlerts.value = alerts.count { !it.acknowledged }
                val newOnes = alerts.filter { it.id !in knownAlertIds }
                knownAlertIds += newOnes.map { it.id }
                newOnes.sortedBy { it.timestampMillis }.forEach { alert ->
                    val prefix = if (alert.severity == AlertSeverity.CRITICA) "🚨" else "⚠️"
                    _effectFlow.emit(ShellEffect.ShowSnackbar("$prefix ${alert.type.label}: ${alert.message}"))
                }
            }
        }
    }

    private fun observeCommandResults() {
        viewModelScope.launch {
            observeCommandsUseCase().collect { commands ->
                commands.forEach { record ->
                    val previous = knownCommandStatus[record.id]
                    knownCommandStatus[record.id] = record.status
                    if (previous == CommandStatus.PENDIENTE && record.status != CommandStatus.PENDIENTE) {
                        val label = record.command.type.label
                        val message = when (record.status) {
                            CommandStatus.CONFIRMADA -> "✅ $label: confirmada por el robot"
                            CommandStatus.RECHAZADA -> "⛔ $label: rechazada · ${record.rejectionReason ?: "sin motivo"}"
                            CommandStatus.SIN_RESPUESTA -> "❓ $label: sin respuesta (no sabemos si se ejecutó)"
                            CommandStatus.PENDIENTE -> return@forEach
                        }
                        _effectFlow.emit(ShellEffect.ShowSnackbar(message))
                    }
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            when (val result = disconnectRobotUseCase()) {
                is Resource.Success -> {
                    knownAlertIds.clear()
                    knownCommandStatus.clear()
                    knownMissionStatus.clear()
                    _effectFlow.emit(ShellEffect.NavigateToSelection)
                }
                is Resource.Error -> _effectFlow.emit(ShellEffect.ShowSnackbar(result.message))
            }
        }
    }
}
