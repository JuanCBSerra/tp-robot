package com.tprobot.ovejero.data.simulator

import com.squareup.moshi.Moshi
import com.tprobot.ovejero.core.coroutines.ApplicationScope
import com.tprobot.ovejero.data.remote.RobotDataSource
import com.tprobot.ovejero.data.remote.RobotProtocol
import com.tprobot.ovejero.data.remote.RobotProtocol.AlertCode
import com.tprobot.ovejero.data.remote.RobotProtocol.MissionKind
import com.tprobot.ovejero.data.remote.RobotProtocol.Severity
import com.tprobot.ovejero.data.remote.dto.AlertDto
import com.tprobot.ovejero.data.remote.dto.BatteryDto
import com.tprobot.ovejero.data.remote.dto.CommandAckDto
import com.tprobot.ovejero.data.remote.dto.CommandRequestDto
import com.tprobot.ovejero.data.remote.dto.FleetResponseDto
import com.tprobot.ovejero.data.remote.dto.LinkDto
import com.tprobot.ovejero.data.remote.dto.MissionDto
import com.tprobot.ovejero.data.remote.dto.MotionDto
import com.tprobot.ovejero.data.remote.dto.PositionDto
import com.tprobot.ovejero.data.remote.dto.RobotSummaryDto
import com.tprobot.ovejero.data.remote.dto.ServiceDataDto
import com.tprobot.ovejero.data.remote.dto.TelemetryDto
import com.tprobot.ovejero.domain.model.FieldMap
import com.tprobot.ovejero.domain.model.Scenario
import com.tprobot.ovejero.domain.model.SimulationSettings
import com.tprobot.ovejero.domain.model.SimulationSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Robot Unitree Go2 SIMULADO ("Ovejero-01").
 *
 * Hace de robot del otro lado del canal: recibe órdenes en JSON, las valida con sus
 * propias reglas, se mueve por el plano de la chacra, gasta batería, cuenta hacienda
 * y manda telemetría, alertas y respuestas en JSON.
 *
 * Importante para el TP:
 * - NO hay visión por computadora ni algoritmo de arreo: los conteos salen de una tabla
 *   fija por potrero (FieldMap) y las "detecciones" son eventos guionados por escenario.
 * - La UI no conoce esta clase: la ve a través de RobotDataSource -> Repository -> UseCase.
 * - Los métodos de control (escenarios, forzar fallas) solo los usa SimulationRepositoryImpl.
 */
@Singleton
class RobotSimulator @Inject constructor(
    moshi: Moshi,
    @ApplicationScope private val scope: CoroutineScope
) : RobotDataSource {

    private val map = FieldMap.LA_COLORADA
    private val route = map.route
    private val metersPerUnit = map.widthMeters.toDouble()
    private val routeMeters: Double = route.zipWithNext().sumOf { (a, b) ->
        hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()) * metersPerUnit
    }

    private val telemetryAdapter = moshi.adapter(TelemetryDto::class.java).indent("  ")
    private val alertAdapter = moshi.adapter(AlertDto::class.java).indent("  ")
    private val ackAdapter = moshi.adapter(CommandAckDto::class.java).indent("  ")
    private val commandAdapter = moshi.adapter(CommandRequestDto::class.java)
    private val fleetAdapter = moshi.adapter(FleetResponseDto::class.java)

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incoming: Flow<String> = _incoming.asSharedFlow()

    private val _settings = MutableStateFlow(
        SimulationSettings(
            scenario = Scenario.NORMAL,
            speed = SimulationSpeed.X5,
            linkDown = false,
            rejectNextCommand = false,
            ignoreNextCommand = false,
            lastTelemetryJson = null,
            lastCommandJson = null,
            lastResponseJson = null,
            lastAlertJson = null
        )
    )
    val settings: StateFlow<SimulationSettings> = _settings.asStateFlow()

    private val mutex = Mutex()
    private var robot = SimRobot()

    @Volatile
    private var connectedRobotId: String? = null
    private var sequence = 0L
    private var alertCounter = 0
    private var linkJob: Job? = null
    private val pendingAlerts = mutableListOf<String>()

    init {
        robot = newRobotFor(Scenario.NORMAL)
        scope.launch(Dispatchers.Default) { physicsLoop() }
        scope.launch(Dispatchers.Default) { telemetryLoop() }
    }

    // =====================================================================
    //  RobotDataSource (lo que ve el Repository)
    // =====================================================================

    override suspend fun fetchFleet(): String {
        delay(600) // latencia simulada
        val battery = mutex.withLock { robot.battery }
        val fleet = FleetResponseDto(
            robots = listOf(
                RobotSummaryDto(
                    robotId = ROBOT_ID,
                    name = "Ovejero-01",
                    model = "Unitree Go2 EDU",
                    location = "Chacra La Colorada · Galpón",
                    status = RobotProtocol.RobotStatus.AVAILABLE,
                    batteryPercent = round1(battery),
                    note = null
                ),
                RobotSummaryDto(
                    robotId = "OVJ-02",
                    name = "Ovejero-02",
                    model = "Unitree Go2 EDU",
                    location = "Taller en Oberá",
                    status = RobotProtocol.RobotStatus.MAINTENANCE,
                    batteryPercent = null,
                    note = "Cambio del motor de la pata trasera izquierda"
                ),
                RobotSummaryDto(
                    robotId = "OVJ-03",
                    name = "Ovejero-03",
                    model = "Unitree Go2 Air",
                    location = "Chacra del vecino (préstamo)",
                    status = RobotProtocol.RobotStatus.OUT_OF_RANGE,
                    batteryPercent = 41.0,
                    note = "Sin señal desde hace 2 días"
                )
            )
        )
        return fleetAdapter.toJson(fleet)
    }

    override suspend fun connect(robotId: String): Boolean {
        delay(1_200) // handshake simulado
        if (robotId != ROBOT_ID) return false
        connectedRobotId = robotId
        flushPendingAlerts()
        return true
    }

    override suspend fun disconnect() {
        connectedRobotId = null
    }

    override suspend fun send(json: String) {
        val request = try {
            commandAdapter.fromJson(json)
        } catch (e: Exception) {
            null
        } ?: return
        _settings.update { it.copy(lastCommandJson = commandAdapter.indent("  ").toJson(request)) }

        // Sin enlace la orden se pierde en el camino: la app la va a marcar "sin respuesta".
        if (connectedRobotId != request.robotId || _settings.value.linkDown) return
        if (_settings.value.ignoreNextCommand) {
            _settings.update { it.copy(ignoreNextCommand = false) }
            return
        }

        scope.launch(Dispatchers.Default) {
            delay(Random.nextLong(ACK_MIN_MILLIS, ACK_MAX_MILLIS))
            val forcedReject = _settings.value.rejectNextCommand
            val (accepted, reason) = if (forcedReject) {
                _settings.update { it.copy(rejectNextCommand = false) }
                false to "Rechazada por el robot (forzado desde el panel de simulación)"
            } else {
                mutex.withLock { execute(request) }
            }
            val ack = CommandAckDto(
                type = RobotProtocol.MessageType.COMMAND_ACK,
                commandId = request.commandId,
                robotId = request.robotId,
                timestamp = System.currentTimeMillis(),
                status = if (accepted) RobotProtocol.AckStatus.ACCEPTED else RobotProtocol.AckStatus.REJECTED,
                reason = reason
            )
            val ackJson = ackAdapter.toJson(ack)
            // Si se cortó el enlace mientras procesaba, la respuesta tampoco llega.
            if (_settings.value.linkDown || connectedRobotId == null) return@launch
            _settings.update { it.copy(lastResponseJson = ackJson) }
            _incoming.emit(ackJson)
        }
    }

    // =====================================================================
    //  Control del simulador (solo para el panel de simulación)
    // =====================================================================

    suspend fun loadScenario(scenario: Scenario) {
        linkJob?.cancel()
        mutex.withLock {
            robot = newRobotFor(scenario)
            synchronized(pendingAlerts) { pendingAlerts.clear() }
        }
        _settings.update {
            it.copy(
                scenario = scenario,
                linkDown = false,
                rejectNextCommand = false,
                ignoreNextCommand = false,
                lastAlertJson = null
            )
        }
    }

    fun setSpeed(speed: SimulationSpeed) {
        _settings.update { it.copy(speed = speed) }
    }

    suspend fun setLinkDown(down: Boolean) {
        linkJob?.cancel()
        applyLinkDown(down)
    }

    fun rejectNextCommand() {
        _settings.update { it.copy(rejectNextCommand = true) }
    }

    fun ignoreNextCommand() {
        _settings.update { it.copy(ignoreNextCommand = true) }
    }

    /** Baja la batería a 22 % para disparar la advertencia sin esperar. Devuelve un error o null. */
    suspend fun drainBattery(): String? {
        val error = mutex.withLock {
            if (robot.state == RobotProtocol.State.DOCKED) {
                "El robot está cargando en el galpón: iniciá una misión primero"
            } else {
                robot.battery = min(robot.battery, 22.0)
                null
            }
        }
        return error
    }

    suspend fun triggerObstacle(): String? {
        var alert: AlertDto? = null
        val error = mutex.withLock {
            if (robot.state != RobotProtocol.State.ON_MISSION) {
                "El robot tiene que estar en misión (caminando)"
            } else {
                alert = raiseObstacle()
                null
            }
        }
        alert?.let { emitAlert(it) }
        return error
    }

    suspend fun triggerStorm(): String? {
        var alert: AlertDto? = null
        val error = mutex.withLock {
            if (robot.state != RobotProtocol.State.ON_MISSION) {
                "El robot tiene que estar en misión (caminando)"
            } else {
                alert = raiseStorm()
                null
            }
        }
        alert?.let { emitAlert(it) }
        return error
    }

    suspend fun triggerPersonNearby(): String? {
        var alert: AlertDto? = null
        val error = mutex.withLock {
            if (robot.state == RobotProtocol.State.DOCKED) {
                "El robot está en el galpón"
            } else {
                robot.personSlowdownSeconds = PERSON_SLOWDOWN_SECONDS
                alert = raise(
                    code = AlertCode.PERSON_NEARBY,
                    severity = Severity.WARNING,
                    message = "Hay una persona a menos de 5 m. El robot bajó la velocidad a ${PERSON_SPEED_MPS} m/s.",
                    requiresDecision = false
                )
                null
            }
        }
        alert?.let { emitAlert(it) }
        return error
    }

    // =====================================================================
    //  Bucles del "robot"
    // =====================================================================

    private suspend fun physicsLoop() {
        while (true) {
            delay(TICK_MILLIS)
            val dt = TICK_MILLIS / 1000.0 * _settings.value.speed.multiplier
            val raised = mutex.withLock { step(dt) }
            raised.forEach { emitAlert(it) }
        }
    }

    private suspend fun telemetryLoop() {
        while (true) {
            delay(TELEMETRY_MILLIS)
            val robotId = connectedRobotId ?: continue
            if (_settings.value.linkDown) continue
            val json = mutex.withLock { telemetryAdapter.toJson(buildTelemetry(robotId)) }
            _settings.update { it.copy(lastTelemetryJson = json) }
            _incoming.emit(json)
        }
    }

    /** Avanza la simulación dt segundos simulados. Devuelve las alertas que se dispararon. */
    private fun step(dt: Double): List<AlertDto> {
        val out = mutableListOf<AlertDto>()
        val r = robot
        r.clockSeconds += dt

        // Clima: temperatura que sube y baja despacio; con tormenta refresca.
        val targetTemp = if (r.stormFired) 23.0 else 31.0 + 3.0 * sin(r.clockSeconds / 300.0)
        r.temperature += (targetTemp - r.temperature) * min(1.0, dt / 30.0)

        // Señal: en el monte (Potrero 3) es peor.
        val linkDown = _settings.value.linkDown
        val zone = currentZone()
        val (minSignal, maxSignal) = if (zone == "Potrero 3" || zone == "Monte nativo") 35 to 60 else 70 to 97
        r.signal = if (linkDown) 0 else (r.signal + Random.nextInt(-4, 5)).coerceIn(minSignal, maxSignal)

        // Batería
        if (r.state == RobotProtocol.State.DOCKED) {
            r.battery = min(100.0, r.battery + CHARGE_PER_SECOND * dt)
        } else {
            val drain = if (r.speed > 0.05) DRAIN_MOVING_PER_SECOND else DRAIN_IDLE_PER_SECOND
            r.battery = max(0.0, r.battery - drain * dt)
            val pct = r.battery.roundToInt()
            if (!r.lowWarned && r.battery <= LOW_BATTERY) {
                r.lowWarned = true
                out += raise(
                    AlertCode.LOW_BATTERY, Severity.WARNING,
                    "Batería al $pct %. Conviene mandarlo al galpón.", requiresDecision = false
                )
            }
            if (!r.criticalWarned && r.battery <= CRITICAL_BATTERY) {
                r.criticalWarned = true
                out += raise(
                    AlertCode.CRITICAL_BATTERY, Severity.CRITICAL,
                    "Batería crítica ($pct %). Al llegar al 10 % el robot vuelve solo al galpón.",
                    requiresDecision = false
                )
            }
            if (!r.autoReturned && r.battery <= AUTO_RETURN_BATTERY && r.state != RobotProtocol.State.RETURNING) {
                r.autoReturned = true
                endMission(RobotProtocol.MissionStatus.ABORTED)
                r.state = RobotProtocol.State.RETURNING
                out += raise(
                    AlertCode.AUTO_RETURN, Severity.CRITICAL,
                    "Batería al $pct %: el robot suspendió la misión y vuelve solo al galpón.",
                    requiresDecision = false
                )
            }
        }

        // Pérdida de enlace prolongada: el robot vuelve solo (medida de seguridad).
        if (linkDown) {
            r.linkDownSeconds += dt
            val missionActive = r.mission?.isActive == true
            if (r.linkDownSeconds > LINK_LOSS_RETURN_SECONDS && missionActive &&
                r.state != RobotProtocol.State.RETURNING
            ) {
                endMission(RobotProtocol.MissionStatus.ABORTED)
                r.state = RobotProtocol.State.RETURNING
                out += raise(
                    AlertCode.AUTO_RETURN, Severity.WARNING,
                    "Pasaron más de 2 minutos sin enlace: el robot suspendió la misión y volvió al galpón.",
                    requiresDecision = false
                )
            }
        } else {
            r.linkDownSeconds = 0.0
        }

        // Movimiento
        when (r.state) {
            RobotProtocol.State.ON_MISSION -> advanceOnRoute(dt, out)
            RobotProtocol.State.RETURNING -> moveToBase(dt)
            else -> {
                r.speed = 0.0
                r.motion = RobotProtocol.Motion.IDLE
            }
        }
        return out
    }

    private fun advanceOnRoute(dt: Double, out: MutableList<AlertDto>) {
        val r = robot
        val mission = r.mission ?: return
        if (r.checkpointPauseSeconds > 0) {
            // Parado contando la hacienda del potrero.
            r.checkpointPauseSeconds -= dt
            r.speed = 0.0
            r.motion = RobotProtocol.Motion.IDLE
            return
        }
        val herding = mission.kind == MissionKind.HERDING && r.following > 0
        var cruise = if (herding) HERD_SPEED_MPS else WALK_SPEED_MPS
        if (r.personSlowdownSeconds > 0) {
            r.personSlowdownSeconds -= dt
            cruise = min(cruise, PERSON_SPEED_MPS)
        }
        r.speed = cruise
        r.motion = if (herding) RobotProtocol.Motion.HERDING else RobotProtocol.Motion.WALKING

        var budget = cruise * dt
        while (budget > 0 && r.state == RobotProtocol.State.ON_MISSION) {
            val target = route[r.nextCheckpoint]
            val dx = target.x - r.x
            val dy = target.y - r.y
            val dist = hypot(dx, dy) * metersPerUnit
            if (dist > 0.01) r.heading = headingOf(dx, dy)
            if (dist <= budget) {
                r.x = target.x.toDouble()
                r.y = target.y.toDouble()
                budget -= dist
                r.distanceDone += dist
                arriveAt(r.nextCheckpoint, out)
                if (r.checkpointPauseSeconds > 0) break
            } else {
                val f = budget / dist
                r.x += dx * f
                r.y += dy * f
                r.distanceDone += budget
                budget = 0.0
            }
        }
    }

    private fun arriveAt(index: Int, out: MutableList<AlertDto>) {
        val r = robot
        val mission = r.mission ?: return
        val checkpoint = route[index]
        val scenario = _settings.value.scenario
        mission.checkpointsReached = index

        if (checkpoint.expectedHeads > 0) {
            val found = checkpoint.expectedHeads - missingAt(checkpoint.name, scenario)
            r.counted += found
            if (mission.kind == MissionKind.HERDING) r.following += found
            r.checkpointPauseSeconds = COUNT_PAUSE_SECONDS
        }
        if (checkpoint.isCorral && mission.kind == MissionKind.HERDING) {
            r.inCorral += r.following
            r.following = 0
            r.checkpointPauseSeconds = COUNT_PAUSE_SECONDS
        }

        // Disparadores guionados de cada escenario
        when {
            scenario == Scenario.INCIDENTE && checkpoint.name == "Potrero 4" && !r.obstacleFired ->
                out += raiseObstacle()
            scenario == Scenario.TORMENTA && checkpoint.name == "Potrero 3" && !r.stormFired ->
                out += raiseStorm()
            scenario == Scenario.DESCONEXION && checkpoint.name == "Potrero 3" && !r.disconnectFired -> {
                r.disconnectFired = true
                scheduleLinkOutage(SCENARIO_OUTAGE_MILLIS)
            }
        }

        if (index == route.lastIndex) {
            mission.status = RobotProtocol.MissionStatus.COMPLETED
            r.state = RobotProtocol.State.DOCKED
            r.speed = 0.0
            r.motion = RobotProtocol.Motion.IDLE
            r.distanceDone = routeMeters
            r.checkpointPauseSeconds = 0.0
            if (r.counted < map.totalHeads) {
                val missing = map.totalHeads - r.counted
                out += raise(
                    AlertCode.MISSING_ANIMALS, Severity.WARNING,
                    "Terminó la recorrida con ${r.counted}/${map.totalHeads}: faltan $missing animales. Revisá el monte.",
                    requiresDecision = false
                )
            }
        } else {
            r.nextCheckpoint = index + 1
        }
    }

    private fun moveToBase(dt: Double) {
        val r = robot
        val base = map.base
        val dx = base.x - r.x
        val dy = base.y - r.y
        val dist = hypot(dx, dy) * metersPerUnit
        val budget = WALK_SPEED_MPS * dt
        r.speed = WALK_SPEED_MPS
        r.motion = RobotProtocol.Motion.WALKING
        if (dist > 0.01) r.heading = headingOf(dx, dy)
        if (dist <= budget) {
            r.x = base.x.toDouble()
            r.y = base.y.toDouble()
            r.state = RobotProtocol.State.DOCKED
            r.speed = 0.0
            r.motion = RobotProtocol.Motion.IDLE
            r.following = 0
        } else {
            val f = budget / dist
            r.x += dx * f
            r.y += dy * f
        }
    }

    // =====================================================================
    //  Órdenes: el robot tiene sus propias reglas (puede rechazar)
    // =====================================================================

    private fun execute(request: CommandRequestDto): Pair<Boolean, String?> {
        val r = robot
        val m = r.mission
        val active = m != null && m.isActive
        return when (request.command) {
            RobotProtocol.Command.START_MISSION -> {
                val kind = request.params?.missionKind
                when {
                    kind != MissionKind.HEAD_COUNT && kind != MissionKind.HERDING ->
                        false to "Tipo de misión desconocido"
                    active -> false to "Ya hay una misión en curso"
                    r.state != RobotProtocol.State.DOCKED -> false to "El robot no está en el galpón"
                    r.battery < MIN_BATTERY_TO_START ->
                        false to "Batería insuficiente (${r.battery.roundToInt()} %). Mínimo ${MIN_BATTERY_TO_START.roundToInt()} %"
                    else -> {
                        startMission(kind)
                        true to null
                    }
                }
            }

            RobotProtocol.Command.PAUSE_MISSION ->
                if (m != null && m.status == RobotProtocol.MissionStatus.RUNNING &&
                    r.state == RobotProtocol.State.ON_MISSION
                ) {
                    m.status = RobotProtocol.MissionStatus.PAUSED
                    r.state = RobotProtocol.State.PAUSED
                    r.speed = 0.0
                    r.motion = RobotProtocol.Motion.IDLE
                    true to null
                } else {
                    false to "No hay una misión en marcha"
                }

            RobotProtocol.Command.RESUME_MISSION -> when {
                m == null || m.status != RobotProtocol.MissionStatus.PAUSED -> false to "No hay una misión pausada"
                r.battery < MIN_BATTERY_TO_RESUME -> false to "Batería muy baja para continuar"
                r.alerts.values.any { it.missionId == m.id && it.requiresDecision && !it.acknowledged } ->
                    false to "Hay una alerta sin reconocer"
                else -> {
                    m.status = RobotProtocol.MissionStatus.RUNNING
                    r.state = RobotProtocol.State.ON_MISSION
                    true to null
                }
            }

            RobotProtocol.Command.CANCEL_MISSION ->
                if (m != null && active) {
                    m.status = RobotProtocol.MissionStatus.CANCELLED
                    if (r.state != RobotProtocol.State.DOCKED) r.state = RobotProtocol.State.RETURNING
                    true to null
                } else {
                    false to "No hay misión para cancelar"
                }

            RobotProtocol.Command.RETURN_TO_BASE -> when (r.state) {
                RobotProtocol.State.DOCKED -> false to "El robot ya está en el galpón"
                RobotProtocol.State.RETURNING -> false to "Ya está volviendo al galpón"
                else -> {
                    if (m != null && active) m.status = RobotProtocol.MissionStatus.ABORTED
                    r.state = RobotProtocol.State.RETURNING
                    true to null
                }
            }

            RobotProtocol.Command.OPERATIONAL_STOP ->
                if (r.state == RobotProtocol.State.DOCKED) {
                    false to "El robot ya está quieto en el galpón"
                } else {
                    if (m != null && m.status == RobotProtocol.MissionStatus.RUNNING) {
                        m.status = RobotProtocol.MissionStatus.PAUSED
                    }
                    r.state = RobotProtocol.State.STOPPED
                    r.speed = 0.0
                    r.motion = RobotProtocol.Motion.IDLE
                    true to null
                }

            RobotProtocol.Command.ACK_ALERT -> {
                val alert = request.params?.alertId?.let { r.alerts[it] }
                when {
                    alert == null -> false to "Alerta desconocida"
                    alert.acknowledged -> false to "La alerta ya estaba reconocida"
                    else -> {
                        alert.acknowledged = true
                        true to null
                    }
                }
            }

            else -> false to "Orden desconocida: ${request.command}"
        }
    }

    private fun startMission(kind: String) {
        val r = robot
        val id = "MIS-" + SimpleDateFormat("ddMM-HHmmss", Locale.US).format(Date())
        r.mission = SimMission(id = id, kind = kind, status = RobotProtocol.MissionStatus.RUNNING,
            startedAt = System.currentTimeMillis())
        r.state = RobotProtocol.State.ON_MISSION
        r.x = map.base.x.toDouble()
        r.y = map.base.y.toDouble()
        r.distanceDone = 0.0
        r.nextCheckpoint = 1
        r.checkpointPauseSeconds = 0.0
        r.counted = 0
        r.following = 0
        r.inCorral = 0
        r.lowWarned = r.battery <= LOW_BATTERY
        r.criticalWarned = r.battery <= CRITICAL_BATTERY
        r.autoReturned = false
        r.obstacleFired = false
        r.stormFired = false
        r.disconnectFired = false
        r.personSlowdownSeconds = 0.0
    }

    // =====================================================================
    //  Alertas
    // =====================================================================

    private fun raiseObstacle(): AlertDto {
        robot.obstacleFired = true
        pauseForDecision()
        return raise(
            AlertCode.OBSTACLE, Severity.CRITICAL,
            "Alambrado caído entre el ${currentZone()} y el monte. El robot se detuvo: ¿rodear, esperar o volver?",
            requiresDecision = true
        )
    }

    private fun raiseStorm(): AlertDto {
        robot.stormFired = true
        pauseForDecision()
        val missing = map.totalHeads - robot.counted - remainingHeadsAfterCurrent()
        val detail = if (missing > 0) " Hay $missing animales sin encontrar en el monte." else ""
        return raise(
            AlertCode.STORM, Severity.CRITICAL,
            "Pronóstico de tormenta fuerte en 20 minutos.$detail El Go2 no es impermeable: ¿seguir o volver?",
            requiresDecision = true
        )
    }

    private fun remainingHeadsAfterCurrent(): Int =
        route.drop(robot.nextCheckpoint).sumOf { it.expectedHeads }

    private fun pauseForDecision() {
        val r = robot
        r.mission?.let { if (it.status == RobotProtocol.MissionStatus.RUNNING) it.status = RobotProtocol.MissionStatus.PAUSED }
        r.state = RobotProtocol.State.AWAITING_DECISION
        r.speed = 0.0
        r.motion = RobotProtocol.Motion.IDLE
    }

    private fun raise(code: String, severity: String, message: String, requiresDecision: Boolean): AlertDto {
        alertCounter++
        val id = "AL-%03d".format(Locale.US, alertCounter)
        robot.alerts[id] = SimAlert(code = code, missionId = robot.mission?.id, requiresDecision = requiresDecision)
        return AlertDto(
            type = RobotProtocol.MessageType.ALERT,
            alertId = id,
            robotId = ROBOT_ID,
            timestamp = System.currentTimeMillis(),
            code = code,
            severity = severity,
            message = message,
            zone = currentZone(),
            requiresDecision = requiresDecision
        )
    }

    private suspend fun emitAlert(alert: AlertDto) {
        val json = alertAdapter.toJson(alert)
        _settings.update { it.copy(lastAlertJson = json) }
        if (connectedRobotId != null && !_settings.value.linkDown) {
            _incoming.emit(json)
        } else {
            // Se guarda y se manda cuando vuelve el enlace.
            synchronized(pendingAlerts) { pendingAlerts.add(json) }
        }
    }

    private suspend fun flushPendingAlerts() {
        val toSend = synchronized(pendingAlerts) {
            val copy = pendingAlerts.toList()
            pendingAlerts.clear()
            copy
        }
        toSend.forEach { _incoming.emit(it) }
    }

    // =====================================================================
    //  Enlace
    // =====================================================================

    private fun scheduleLinkOutage(millis: Long) {
        linkJob?.cancel()
        linkJob = scope.launch(Dispatchers.Default) {
            applyLinkDown(true)
            delay(millis)
            applyLinkDown(false)
        }
    }

    private suspend fun applyLinkDown(down: Boolean) {
        _settings.update { it.copy(linkDown = down) }
        if (!down) flushPendingAlerts()
    }

    // =====================================================================
    //  Telemetría
    // =====================================================================

    private fun buildTelemetry(robotId: String): TelemetryDto {
        val r = robot
        return TelemetryDto(
            type = RobotProtocol.MessageType.TELEMETRY,
            protocolVersion = RobotProtocol.VERSION,
            robotId = robotId,
            sequence = ++sequence,
            timestamp = System.currentTimeMillis(),
            state = r.state,
            battery = BatteryDto(
                percent = round1(r.battery),
                charging = r.state == RobotProtocol.State.DOCKED && r.battery < 100.0
            ),
            mission = r.mission?.let {
                MissionDto(
                    missionId = it.id,
                    kind = it.kind,
                    status = it.status,
                    progress = round3((r.distanceDone / routeMeters).coerceIn(0.0, 1.0)),
                    checkpointsReached = it.checkpointsReached,
                    checkpointsTotal = route.lastIndex,
                    startedAt = it.startedAt
                )
            },
            position = PositionDto(
                x = round3(r.x),
                y = round3(r.y),
                zone = currentZone(),
                headingDeg = r.heading
            ),
            motion = MotionDto(mode = r.motion, speedMps = round1(r.speed)),
            service = ServiceDataDto(
                headsCounted = r.counted,
                headsExpected = map.totalHeads,
                headsInCorral = r.inCorral,
                temperatureC = round1(r.temperature)
            ),
            link = LinkDto(signalPercent = r.signal)
        )
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private fun newRobotFor(scenario: Scenario): SimRobot = SimRobot().apply {
        x = map.base.x.toDouble()
        y = map.base.y.toDouble()
        battery = if (scenario == Scenario.BATERIA_BAJA) 34.0 else 86.0
    }

    private fun currentZone(): String = map.zoneAt(robot.x.toFloat(), robot.y.toFloat())

    /** Animales que "no se encuentran" (están en el monte). Solo en el escenario de tormenta. */
    private fun missingAt(checkpointName: String, scenario: Scenario): Int =
        if (scenario == Scenario.TORMENTA && checkpointName == "Potrero 3") 3 else 0

    private fun headingOf(dx: Double, dy: Double): Int {
        val degrees = Math.toDegrees(atan2(dx, -dy)).roundToInt()
        return (degrees + 360) % 360
    }

    private fun round1(value: Double) = (value * 10).roundToInt() / 10.0
    private fun round3(value: Double) = (value * 1000).roundToInt() / 1000.0

    private class SimRobot {
        var battery = 100.0
        var state = RobotProtocol.State.DOCKED
        var x = 0.0
        var y = 0.0
        var heading = 0
        var speed = 0.0
        var motion = RobotProtocol.Motion.IDLE
        var mission: SimMission? = null
        var distanceDone = 0.0
        var nextCheckpoint = 1
        var checkpointPauseSeconds = 0.0
        var counted = 0
        var following = 0
        var inCorral = 0
        var temperature = 30.0
        var signal = 90
        var personSlowdownSeconds = 0.0
        var lowWarned = false
        var criticalWarned = false
        var autoReturned = false
        var obstacleFired = false
        var stormFired = false
        var disconnectFired = false
        var linkDownSeconds = 0.0
        var clockSeconds = 0.0
        val alerts = LinkedHashMap<String, SimAlert>()
    }

    private class SimMission(
        val id: String,
        val kind: String,
        var status: String,
        val startedAt: Long
    ) {
        var checkpointsReached = 0
        val isActive: Boolean
            get() = status == RobotProtocol.MissionStatus.RUNNING || status == RobotProtocol.MissionStatus.PAUSED
    }

    private class SimAlert(
        val code: String,
        val missionId: String?,
        val requiresDecision: Boolean,
        var acknowledged: Boolean = false
    )

    private fun endMission(status: String) {
        robot.mission?.let { if (it.isActive) it.status = status }
    }

    companion object {
        const val ROBOT_ID = "OVJ-01"

        private const val TICK_MILLIS = 250L
        private const val TELEMETRY_MILLIS = 500L
        private const val ACK_MIN_MILLIS = 400L
        private const val ACK_MAX_MILLIS = 1_200L
        private const val SCENARIO_OUTAGE_MILLIS = 12_000L

        private const val WALK_SPEED_MPS = 1.5
        private const val HERD_SPEED_MPS = 1.0
        private const val PERSON_SPEED_MPS = 0.4
        private const val PERSON_SLOWDOWN_SECONDS = 20.0
        private const val COUNT_PAUSE_SECONDS = 8.0

        private const val DRAIN_MOVING_PER_SECOND = 0.03
        private const val DRAIN_IDLE_PER_SECOND = 0.006
        private const val CHARGE_PER_SECOND = 0.25
        private const val MIN_BATTERY_TO_START = 30.0
        private const val MIN_BATTERY_TO_RESUME = 15.0
        private const val LOW_BATTERY = 25.0
        private const val CRITICAL_BATTERY = 15.0
        private const val AUTO_RETURN_BATTERY = 10.0
        private const val LINK_LOSS_RETURN_SECONDS = 120.0
    }
}
