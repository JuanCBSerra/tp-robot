package com.tprobot.ovejero.testutil

import com.tprobot.ovejero.domain.model.Alert
import com.tprobot.ovejero.domain.model.AlertSeverity
import com.tprobot.ovejero.domain.model.AlertType
import com.tprobot.ovejero.domain.model.CommandRecord
import com.tprobot.ovejero.domain.model.CommandStatus
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.ConnectionState
import com.tprobot.ovejero.domain.model.EventCategory
import com.tprobot.ovejero.domain.model.HerdStatus
import com.tprobot.ovejero.domain.model.HistoryEvent
import com.tprobot.ovejero.domain.model.MissionProgress
import com.tprobot.ovejero.domain.model.MissionRecord
import com.tprobot.ovejero.domain.model.MissionStatus
import com.tprobot.ovejero.domain.model.MissionType
import com.tprobot.ovejero.domain.model.Motion
import com.tprobot.ovejero.domain.model.MotionMode
import com.tprobot.ovejero.domain.model.OperationalState
import com.tprobot.ovejero.domain.model.Position
import com.tprobot.ovejero.domain.model.Robot
import com.tprobot.ovejero.domain.model.RobotAvailability
import com.tprobot.ovejero.domain.model.RobotCommand
import com.tprobot.ovejero.domain.model.RobotStatus
import com.tprobot.ovejero.domain.model.Scenario
import com.tprobot.ovejero.domain.model.SimulationSettings
import com.tprobot.ovejero.domain.model.SimulationSpeed
import com.tprobot.ovejero.domain.model.Telemetry

/** Constructores de datos de prueba con valores por defecto razonables. */
object Fixtures {

    fun robot(
        id: String = "OVJ-01",
        name: String = "Ovejero-01",
        availability: RobotAvailability = RobotAvailability.DISPONIBLE,
        battery: Int = 80
    ) = Robot(id, name, "Go2 EDU", "Galpón", availability, battery, null)

    fun mission(
        status: MissionStatus = MissionStatus.EN_CURSO,
        type: MissionType = MissionType.ARREO,
        id: String = "MIS-1",
        progress: Int = 40
    ) = MissionProgress(id, type, status, progress, 2, 7, 0)

    fun telemetry(
        state: OperationalState = OperationalState.EN_BASE,
        battery: Int = 80,
        mission: MissionProgress? = null,
        herd: HerdStatus = HerdStatus(10, 45, 0),
        temperature: Double = 25.0,
        humidity: Int = 55,
        signal: Int = 90,
        charging: Boolean = false,
        motion: Motion = Motion(MotionMode.CAMINANDO, 1.5),
        zone: String = "Potrero 1",
        sequence: Long = 1,
        timestamp: Long = 0
    ) = Telemetry(
        robotId = "OVJ-01",
        timestampMillis = timestamp,
        sequence = sequence,
        batteryPercent = battery,
        isCharging = charging,
        operationalState = state,
        mission = mission,
        position = Position(0.5f, 0.5f, zone, 0),
        motion = motion,
        herd = herd,
        temperatureCelsius = temperature,
        signalPercent = signal,
        humidityPercent = humidity
    )

    fun status(
        telemetry: Telemetry? = telemetry(),
        connection: ConnectionState = ConnectionState.CONECTADO,
        lastUpdate: Long? = 0,
        checkedAt: Long = 0
    ) = RobotStatus(robot(), connection, telemetry, lastUpdate, checkedAt)

    fun alert(
        id: String = "AL-1",
        type: AlertType = AlertType.OBSTACULO,
        severity: AlertSeverity = AlertSeverity.CRITICA,
        acknowledged: Boolean = false,
        requiresDecision: Boolean = false,
        timestamp: Long = 0,
        message: String = "mensaje"
    ) = Alert(id, type, severity, message, "Potrero 4", timestamp, requiresDecision, acknowledged)

    fun commandRecord(
        id: String = "CMD-1",
        type: CommandType = CommandType.PAUSAR,
        status: CommandStatus = CommandStatus.PENDIENTE,
        alertId: String? = null,
        rejectionReason: String? = null
    ) = CommandRecord(
        id = id,
        command = RobotCommand(type, alertId = alertId),
        status = status,
        sentAtMillis = 0,
        resolvedAtMillis = null,
        rejectionReason = rejectionReason
    )

    fun missionRecord(
        id: String = "MIS-1",
        start: Long = 0,
        end: Long = 600_000
    ) = MissionRecord(
        missionId = id,
        robotName = "Ovejero-01",
        type = MissionType.ARREO,
        result = MissionStatus.COMPLETADA,
        startedAtMillis = start,
        endedAtMillis = end,
        headsCounted = 45,
        headsExpected = 45,
        headsInCorral = 45,
        batteryUsedPercent = 30
    )

    fun historyEvent(id: Long = 1) = HistoryEvent(id, 0, EventCategory.SISTEMA, "Titulo", "Detalle")

    fun settings(scenario: Scenario = Scenario.NORMAL) = SimulationSettings(
        scenario = scenario,
        speed = SimulationSpeed.X1,
        linkDown = false,
        rejectNextCommand = false,
        ignoreNextCommand = false,
        lastTelemetryJson = null,
        lastCommandJson = null,
        lastResponseJson = null,
        lastAlertJson = null
    )
}
