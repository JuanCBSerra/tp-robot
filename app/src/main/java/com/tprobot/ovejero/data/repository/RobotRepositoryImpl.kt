package com.tprobot.ovejero.data.repository

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.tprobot.ovejero.core.coroutines.ApplicationScope
import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.data.local.dao.EventDao
import com.tprobot.ovejero.data.local.dao.MissionDao
import com.tprobot.ovejero.data.mapper.AlertMapper
import com.tprobot.ovejero.data.mapper.CommandMapper
import com.tprobot.ovejero.data.mapper.HistoryMapper
import com.tprobot.ovejero.data.mapper.RobotMapper
import com.tprobot.ovejero.data.mapper.TelemetryMapper
import com.tprobot.ovejero.data.remote.RobotDataSource
import com.tprobot.ovejero.data.remote.RobotProtocol
import com.tprobot.ovejero.data.remote.dto.AlertDto
import com.tprobot.ovejero.data.remote.dto.CommandAckDto
import com.tprobot.ovejero.data.remote.dto.CommandRequestDto
import com.tprobot.ovejero.data.remote.dto.EnvelopeDto
import com.tprobot.ovejero.data.remote.dto.FleetResponseDto
import com.tprobot.ovejero.data.remote.dto.TelemetryDto
import com.tprobot.ovejero.domain.model.Alert
import com.tprobot.ovejero.domain.model.CommandRecord
import com.tprobot.ovejero.domain.model.CommandStatus
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.ConnectionState
import com.tprobot.ovejero.domain.model.EventCategory
import com.tprobot.ovejero.domain.model.HistoryEvent
import com.tprobot.ovejero.domain.model.MissionRecord
import com.tprobot.ovejero.domain.model.MissionType
import com.tprobot.ovejero.domain.model.OperationRules
import com.tprobot.ovejero.domain.model.Robot
import com.tprobot.ovejero.domain.model.RobotCommand
import com.tprobot.ovejero.domain.model.RobotStatus
import com.tprobot.ovejero.domain.model.Telemetry
import com.tprobot.ovejero.domain.repository.RobotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del repositorio del robot.
 *
 * Responsabilidades:
 * 1. Parsear el JSON que llega del robot (Moshi + DTOs) y mapearlo al dominio.
 * 2. Llevar el estado de la conexión: si pasan más de 3 s sin telemetría, marca SIN_SENIAL
 *    y los datos quedan como "desactualizados".
 * 3. Llevar el ciclo de vida de cada orden: PENDIENTE -> CONFIRMADA / RECHAZADA, o
 *    SIN_RESPUESTA si pasan 5 s sin ack.
 * 4. Guardar en Room el historial de eventos y el resumen de cada misión.
 *
 * No sabe que del otro lado hay un simulador: solo conoce RobotDataSource.
 */
@Singleton
class RobotRepositoryImpl @Inject constructor(
    private val dataSource: RobotDataSource,
    moshi: Moshi,
    private val eventDao: EventDao,
    private val missionDao: MissionDao,
    @ApplicationScope private val appScope: CoroutineScope
) : RobotRepository {

    private val envelopeAdapter = moshi.adapter(EnvelopeDto::class.java)
    private val telemetryAdapter = moshi.adapter(TelemetryDto::class.java)
    private val alertAdapter = moshi.adapter(AlertDto::class.java)
    private val ackAdapter = moshi.adapter(CommandAckDto::class.java)
    private val commandAdapter = moshi.adapter(CommandRequestDto::class.java)
    private val fleetAdapter = moshi.adapter(FleetResponseDto::class.java)

    private val _status = MutableStateFlow(RobotStatus.disconnected(System.currentTimeMillis()))
    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    private val _commands = MutableStateFlow<List<CommandRecord>>(emptyList())

    private val commandsMutex = Mutex()
    private val commandCounter = AtomicLong(0)
    private var fleetCache: List<Robot> = emptyList()
    private val missionStartBattery = mutableMapOf<String, Int>()
    private val savedMissions = mutableSetOf<String>()

    init {
        appScope.launch { dataSource.incoming.collect { handleIncoming(it) } }
        appScope.launch {
            while (true) {
                delay(1_000)
                refreshConnection()
            }
        }
    }

    // =====================================================================
    //  Flota y conexión
    // =====================================================================

    override suspend fun getRobots(): Resource<List<Robot>> = withContext(Dispatchers.IO) {
        try {
            val body = fleetAdapter.fromJson(dataSource.fetchFleet())
            if (body != null) {
                val robots = body.robots.map { RobotMapper.toDomain(it) }
                fleetCache = robots
                Resource.Success(robots)
            } else {
                Resource.Error("Respuesta vacía de la flota")
            }
        } catch (e: JsonDataException) {
            Resource.Error("La flota respondió un mensaje inválido", e)
        } catch (e: IOException) {
            Resource.Error("Error de conexión al consultar la flota.", e)
        } catch (e: Exception) {
            Resource.Error("Error inesperado: ${e.localizedMessage}", e)
        }
    }

    override suspend fun connect(robotId: String): Resource<Robot> = withContext(Dispatchers.IO) {
        try {
            val robot = fleetCache.firstOrNull { it.id == robotId }
                ?: return@withContext Resource.Error("Robot desconocido: $robotId")
            val startedAt = System.currentTimeMillis()
            _status.value = RobotStatus(robot, ConnectionState.CONECTANDO, null, null, startedAt)
            val accepted = dataSource.connect(robotId)
            if (accepted) {
                val now = System.currentTimeMillis()
                _alerts.value = emptyList()
                commandsMutex.withLock { _commands.value = emptyList() }
                _status.value = RobotStatus(robot, ConnectionState.CONECTADO, null, now, now)
                log(EventCategory.CONEXION, "Conectado a ${robot.name}", "${robot.model} · ${robot.location}")
                Resource.Success(robot)
            } else {
                _status.value = RobotStatus.disconnected(System.currentTimeMillis())
                Resource.Error("${robot.name} no aceptó la conexión")
            }
        } catch (e: IOException) {
            _status.value = RobotStatus.disconnected(System.currentTimeMillis())
            Resource.Error("Error de conexión con el robot.", e)
        } catch (e: Exception) {
            _status.value = RobotStatus.disconnected(System.currentTimeMillis())
            Resource.Error("Error inesperado al conectar: ${e.localizedMessage}", e)
        }
    }

    override suspend fun disconnect(): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val robotName = _status.value.robot?.name ?: "el robot"
            dataSource.disconnect()
            _status.value = RobotStatus.disconnected(System.currentTimeMillis())
            _alerts.value = emptyList()
            commandsMutex.withLock { _commands.value = emptyList() }
            log(
                EventCategory.CONEXION, "Desconectado de $robotName",
                "Desconexión pedida por el operador. El robot sigue con lo que estaba haciendo."
            )
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("No se pudo desconectar: ${e.localizedMessage}", e)
        }
    }

    // =====================================================================
    //  Datos en vivo
    // =====================================================================

    override fun observeRobotStatus(): Flow<RobotStatus> = _status.asStateFlow()

    override fun observeAlerts(): Flow<List<Alert>> = _alerts.asStateFlow()

    override fun observeCommands(): Flow<List<CommandRecord>> = _commands.asStateFlow()

    // =====================================================================
    //  Órdenes
    // =====================================================================

    override suspend fun sendCommand(command: RobotCommand): Resource<CommandRecord> =
        withContext(Dispatchers.IO) {
            val robot = _status.value.robot
                ?: return@withContext Resource.Error("No hay un robot conectado")
            try {
                val now = System.currentTimeMillis()
                val id = "CMD-%04d".format(Locale.US, commandCounter.incrementAndGet())
                val record = CommandRecord(
                    id = id,
                    command = command,
                    status = CommandStatus.PENDIENTE,
                    sentAtMillis = now,
                    resolvedAtMillis = null,
                    rejectionReason = null
                )
                commandsMutex.withLock {
                    _commands.value = (listOf(record) + _commands.value).take(MAX_COMMANDS)
                }
                val json = commandAdapter.toJson(CommandMapper.toDto(id, robot.id, command, now))
                dataSource.send(json)
                log(EventCategory.COMANDO, "Orden enviada: ${describe(command)}", "$id · esperando confirmación")

                // Si en 5 s no hay respuesta, queda SIN_RESPUESTA (no sabemos si se ejecutó).
                appScope.launch {
                    delay(OperationRules.COMMAND_TIMEOUT_MILLIS)
                    expireIfPending(id)
                }
                Resource.Success(record)
            } catch (e: IOException) {
                Resource.Error("No se pudo enviar la orden: error de conexión.", e)
            } catch (e: Exception) {
                Resource.Error("No se pudo enviar la orden: ${e.localizedMessage}", e)
            }
        }

    // =====================================================================
    //  Historial (Room)
    // =====================================================================

    override fun observeMissionHistory(): Flow<List<MissionRecord>> =
        missionDao.observeAll().map { list -> list.map { HistoryMapper.toDomain(it) } }

    override fun observeEventHistory(): Flow<List<HistoryEvent>> =
        eventDao.observeLatest(MAX_EVENTS).map { list -> list.map { HistoryMapper.toDomain(it) } }

    override suspend fun clearHistory(): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            missionDao.clear()
            eventDao.clear()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("No se pudo borrar el historial: ${e.localizedMessage}", e)
        }
    }

    /**
     * Llamado al cargar un escenario nuevo en el simulador: las alertas y órdenes
     * del escenario anterior ya no tienen sentido.
     */
    suspend fun resetSession(reason: String) {
        _alerts.value = emptyList()
        commandsMutex.withLock { _commands.value = emptyList() }
        log(EventCategory.SISTEMA, "Simulación reiniciada", reason)
    }

    // =====================================================================
    //  Mensajes entrantes
    // =====================================================================

    private suspend fun handleIncoming(json: String) {
        try {
            when (envelopeAdapter.fromJson(json)?.type) {
                RobotProtocol.MessageType.TELEMETRY ->
                    telemetryAdapter.fromJson(json)?.let { onTelemetry(TelemetryMapper.toDomain(it)) }
                RobotProtocol.MessageType.ALERT ->
                    alertAdapter.fromJson(json)?.let { onAlert(AlertMapper.toDomain(it)) }
                RobotProtocol.MessageType.COMMAND_ACK ->
                    ackAdapter.fromJson(json)?.let { onAck(it) }
                else -> log(EventCategory.SISTEMA, "Mensaje desconocido del robot", json.take(160))
            }
        } catch (e: JsonDataException) {
            log(EventCategory.SISTEMA, "Mensaje inválido del robot", e.message ?: "JSON inválido")
        } catch (e: IOException) {
            log(EventCategory.SISTEMA, "Mensaje ilegible del robot", e.message ?: "JSON mal formado")
        }
    }

    private suspend fun onTelemetry(telemetry: Telemetry) {
        val current = _status.value
        val robot = current.robot ?: return
        if (robot.id != telemetry.robotId || current.connection == ConnectionState.DESCONECTADO) return
        val now = System.currentTimeMillis()
        if (current.connection == ConnectionState.SIN_SENIAL) {
            val lostSeconds = (now - (current.lastUpdateMillis ?: now)) / 1000
            log(EventCategory.CONEXION, "Señal recuperada", "Estuvo $lostSeconds s sin datos. Telemetría al día otra vez.")
        }
        _status.update {
            it.copy(
                connection = ConnectionState.CONECTADO,
                telemetry = telemetry,
                lastUpdateMillis = now,
                checkedAtMillis = now
            )
        }
        trackMission(telemetry)
    }

    private fun onAlert(alert: Alert) {
        _alerts.update { list -> listOf(alert) + list.filterNot { it.id == alert.id } }
        log(EventCategory.ALERTA, "${alert.type.label} (${alert.severity.label.lowercase()})", alert.message)
    }

    private suspend fun onAck(ack: CommandAckDto) {
        val now = System.currentTimeMillis()
        var resolved: CommandRecord? = null
        var late = false
        commandsMutex.withLock {
            _commands.value = _commands.value.map { record ->
                if (record.id != ack.commandId) return@map record
                if (record.status != CommandStatus.PENDIENTE) {
                    late = true
                    return@map record
                }
                record.copy(
                    status = CommandMapper.toStatus(ack.status),
                    resolvedAtMillis = now,
                    rejectionReason = ack.reason
                ).also { resolved = it }
            }
        }
        resolved?.let { record ->
            if (record.status == CommandStatus.CONFIRMADA && record.command.type == CommandType.RECONOCER_ALERTA) {
                val alertId = record.command.alertId
                _alerts.update { list -> list.map { if (it.id == alertId) it.copy(acknowledged = true) else it } }
            }
            val elapsed = now - record.sentAtMillis
            if (record.status == CommandStatus.CONFIRMADA) {
                log(EventCategory.COMANDO, "Orden confirmada: ${describe(record.command)}", "${record.id} · el robot respondió en $elapsed ms")
            } else {
                log(EventCategory.COMANDO, "Orden rechazada: ${describe(record.command)}", "${record.id} · ${record.rejectionReason ?: "sin motivo"}")
            }
        }
        if (late) {
            log(EventCategory.COMANDO, "Respuesta tardía", "${ack.commandId} llegó después del timeout (${ack.status}). Se mantiene como sin respuesta.")
        }
    }

    private suspend fun expireIfPending(commandId: String) {
        var expired: CommandRecord? = null
        commandsMutex.withLock {
            _commands.value = _commands.value.map { record ->
                if (record.id == commandId && record.status == CommandStatus.PENDIENTE) {
                    record.copy(status = CommandStatus.SIN_RESPUESTA, resolvedAtMillis = System.currentTimeMillis())
                        .also { expired = it }
                } else {
                    record
                }
            }
        }
        expired?.let {
            log(
                EventCategory.COMANDO, "Orden sin respuesta: ${describe(it.command)}",
                "${it.id} · pasaron ${OperationRules.COMMAND_TIMEOUT_MILLIS / 1000} s sin confirmación. No sabemos si el robot la ejecutó."
            )
        }
    }

    private fun refreshConnection() {
        val now = System.currentTimeMillis()
        var lostAfter: Long? = null
        _status.update { status ->
            if (status.connection == ConnectionState.CONECTADO || status.connection == ConnectionState.SIN_SENIAL) {
                val last = status.lastUpdateMillis ?: now
                val stale = now - last > OperationRules.STALE_AFTER_MILLIS
                lostAfter = if (stale && status.connection == ConnectionState.CONECTADO) (now - last) / 1000 else null
                status.copy(
                    connection = if (stale) ConnectionState.SIN_SENIAL else ConnectionState.CONECTADO,
                    checkedAtMillis = now
                )
            } else {
                status.copy(checkedAtMillis = now)
            }
        }
        lostAfter?.let {
            log(
                EventCategory.CONEXION, "Se perdió la señal",
                "Última telemetría hace $it s. Los datos en pantalla quedan marcados como desactualizados."
            )
        }
    }

    // =====================================================================
    //  Misiones -> historial
    // =====================================================================

    private suspend fun trackMission(telemetry: Telemetry) {
        val mission = telemetry.mission ?: return
        if (mission.isActive && mission.missionId !in missionStartBattery) {
            missionStartBattery[mission.missionId] = telemetry.batteryPercent
            log(EventCategory.MISION, "Misión iniciada: ${mission.type.label}", "${mission.missionId} · batería ${telemetry.batteryPercent} %")
        }
        if (!mission.isActive && mission.missionId !in savedMissions) {
            savedMissions += mission.missionId
            val startBattery = missionStartBattery[mission.missionId] ?: telemetry.batteryPercent
            val record = MissionRecord(
                missionId = mission.missionId,
                robotName = _status.value.robot?.name ?: telemetry.robotId,
                type = mission.type,
                result = mission.status,
                startedAtMillis = mission.startedAtMillis,
                endedAtMillis = telemetry.timestampMillis,
                headsCounted = telemetry.herd.counted,
                headsExpected = telemetry.herd.expected,
                headsInCorral = telemetry.herd.inCorral,
                batteryUsedPercent = (startBattery - telemetry.batteryPercent).coerceAtLeast(0)
            )
            withContext(Dispatchers.IO) {
                try {
                    missionDao.insert(HistoryMapper.toEntity(record))
                } catch (e: Exception) {
                    // El historial es secundario: si falla, la operación sigue.
                }
            }
            val corral = if (mission.type == MissionType.ARREO) " · en corral ${telemetry.herd.inCorral}" else ""
            log(
                EventCategory.MISION,
                "Misión ${mission.status.label.lowercase()}: ${mission.type.label}",
                "${mission.missionId} · contados ${telemetry.herd.counted}/${telemetry.herd.expected}$corral · ${mission.progressPercent} % del recorrido"
            )
        }
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private fun describe(command: RobotCommand): String = when {
        command.missionType != null -> "${command.type.label} (${command.missionType.label})"
        command.alertId != null -> "${command.type.label} ${command.alertId}"
        else -> command.type.label
    }

    private fun log(category: EventCategory, title: String, detail: String) {
        appScope.launch(Dispatchers.IO) {
            try {
                eventDao.insert(HistoryMapper.toEntity(System.currentTimeMillis(), category, title, detail))
            } catch (e: Exception) {
                // No bloqueamos la operación por un fallo del historial.
            }
        }
    }

    companion object {
        private const val MAX_COMMANDS = 30
        private const val MAX_EVENTS = 300
    }
}
