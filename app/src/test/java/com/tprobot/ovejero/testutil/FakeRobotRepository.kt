package com.tprobot.ovejero.testutil

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.model.Alert
import com.tprobot.ovejero.domain.model.CommandRecord
import com.tprobot.ovejero.domain.model.HistoryEvent
import com.tprobot.ovejero.domain.model.MissionRecord
import com.tprobot.ovejero.domain.model.Robot
import com.tprobot.ovejero.domain.model.RobotCommand
import com.tprobot.ovejero.domain.model.RobotStatus
import com.tprobot.ovejero.domain.repository.RobotRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Repositorio falso: guarda lo que se le manda y deja controlar lo que devuelve. */
class FakeRobotRepository : RobotRepository {

    val statusFlow = MutableStateFlow(RobotStatus.disconnected(0))
    val alertsFlow = MutableStateFlow<List<Alert>>(emptyList())
    val commandsFlow = MutableStateFlow<List<CommandRecord>>(emptyList())
    val missionsFlow = MutableStateFlow<List<MissionRecord>>(emptyList())
    val eventsFlow = MutableStateFlow<List<HistoryEvent>>(emptyList())

    /** Para probar errores: reemplazar por un flow que falla. */
    var statusSource: Flow<RobotStatus> = statusFlow
    var alertsSource: Flow<List<Alert>> = alertsFlow
    var commandsSource: Flow<List<CommandRecord>> = commandsFlow
    var missionsSource: Flow<List<MissionRecord>> = missionsFlow
    var eventsSource: Flow<List<HistoryEvent>> = eventsFlow

    var robotsResult: Resource<List<Robot>> = Resource.Success(emptyList())
    var connectResult: Resource<Robot>? = null
    var disconnectResult: Resource<Unit> = Resource.Success(Unit)
    var clearHistoryResult: Resource<Unit> = Resource.Success(Unit)
    var sendResult: Resource<CommandRecord>? = null

    /** Si no es nulo, connect() espera a que se complete (para probar "ya conectando"). */
    var connectGate: CompletableDeferred<Unit>? = null

    val sentCommands = mutableListOf<RobotCommand>()
    val connectCalls = mutableListOf<String>()
    var disconnectCalls = 0
    var clearHistoryCalls = 0

    override suspend fun getRobots(): Resource<List<Robot>> = robotsResult

    override suspend fun connect(robotId: String): Resource<Robot> {
        connectCalls += robotId
        connectGate?.await()
        return connectResult ?: Resource.Success(Fixtures.robot(id = robotId))
    }

    override suspend fun disconnect(): Resource<Unit> {
        disconnectCalls++
        return disconnectResult
    }

    override fun observeRobotStatus(): Flow<RobotStatus> = statusSource
    override fun observeAlerts(): Flow<List<Alert>> = alertsSource
    override fun observeCommands(): Flow<List<CommandRecord>> = commandsSource

    override suspend fun sendCommand(command: RobotCommand): Resource<CommandRecord> {
        sentCommands += command
        return sendResult ?: Resource.Success(Fixtures.commandRecord(type = command.type))
    }

    override fun observeMissionHistory(): Flow<List<MissionRecord>> = missionsSource
    override fun observeEventHistory(): Flow<List<HistoryEvent>> = eventsSource

    override suspend fun clearHistory(): Resource<Unit> {
        clearHistoryCalls++
        return clearHistoryResult
    }
}
