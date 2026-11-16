package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.domain.model.Alert
import com.tprobot.ovejero.domain.model.AlertSeverity
import com.tprobot.ovejero.domain.model.AlertType
import com.tprobot.ovejero.domain.model.CommandRecord
import com.tprobot.ovejero.domain.model.CommandStatus
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.ConnectionState
import com.tprobot.ovejero.domain.model.HerdStatus
import com.tprobot.ovejero.domain.model.MissionProgress
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
import com.tprobot.ovejero.domain.model.Telemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests de las condiciones de habilitación de órdenes (función pura, sin Android). */
class GetCommandAvailabilityUseCaseTest {

    private val useCase = GetCommandAvailabilityUseCase()
    private val robot = Robot("OVJ-01", "Ovejero-01", "Go2", "Galpón", RobotAvailability.DISPONIBLE, 80, null)

    private fun telemetry(
        state: OperationalState,
        battery: Int = 80,
        missionStatus: MissionStatus? = null
    ) = Telemetry(
        robotId = "OVJ-01",
        timestampMillis = 0,
        sequence = 1,
        batteryPercent = battery,
        isCharging = false,
        operationalState = state,
        mission = missionStatus?.let {
            MissionProgress("MIS-1", MissionType.ARREO, it, 40, 2, 7, 0)
        },
        position = Position(0.5f, 0.5f, "Potrero 1", 0),
        motion = Motion(MotionMode.CAMINANDO, 1.5),
        herd = HerdStatus(10, 45, 0),
        temperatureCelsius = 30.0,
        signalPercent = 90
    )

    private fun status(telemetry: Telemetry?, connection: ConnectionState = ConnectionState.CONECTADO) =
        RobotStatus(robot, connection, telemetry, 0, 0)

    private fun decisionAlert(acknowledged: Boolean) = Alert(
        id = "AL-001",
        type = AlertType.OBSTACULO,
        severity = AlertSeverity.CRITICA,
        message = "Alambrado caído",
        zoneName = "Potrero 4",
        timestampMillis = 0,
        requiresDecision = true,
        acknowledged = acknowledged
    )

    @Test
    fun `iniciar mision habilitado en el galpon con bateria suficiente`() {
        val result = useCase(status(telemetry(OperationalState.EN_BASE)), emptyList(), emptyList())
        assertTrue(result.getValue(CommandType.INICIAR_MISION).enabled)
    }

    @Test
    fun `iniciar mision deshabilitado con bateria menor a 30`() {
        val result = useCase(status(telemetry(OperationalState.EN_BASE, battery = 29)), emptyList(), emptyList())
        val availability = result.getValue(CommandType.INICIAR_MISION)
        assertFalse(availability.enabled)
        assertTrue(availability.reason!!.contains("Batería"))
    }

    @Test
    fun `todo deshabilitado sin senial salvo la detencion operativa`() {
        val t = telemetry(OperationalState.EN_MISION, missionStatus = MissionStatus.EN_CURSO)
        val result = useCase(status(t, ConnectionState.SIN_SENIAL), emptyList(), emptyList())
        assertFalse(result.getValue(CommandType.PAUSAR).enabled)
        assertFalse(result.getValue(CommandType.VOLVER_A_BASE).enabled)
        assertTrue(result.getValue(CommandType.DETENCION_OPERATIVA).enabled)
    }

    @Test
    fun `reanudar requiere reconocer la alerta de decision`() {
        val t = telemetry(OperationalState.ESPERANDO_DECISION, missionStatus = MissionStatus.PAUSADA)
        val blocked = useCase(status(t), listOf(decisionAlert(acknowledged = false)), emptyList())
        assertFalse(blocked.getValue(CommandType.REANUDAR).enabled)

        val allowed = useCase(status(t), listOf(decisionAlert(acknowledged = true)), emptyList())
        assertTrue(allowed.getValue(CommandType.REANUDAR).enabled)
    }

    @Test
    fun `una orden pendiente deshabilita repetirla`() {
        val t = telemetry(OperationalState.EN_MISION, missionStatus = MissionStatus.EN_CURSO)
        val pending = CommandRecord("CMD-1", RobotCommand(CommandType.PAUSAR), CommandStatus.PENDIENTE, 0, null, null)
        val result = useCase(status(t), emptyList(), listOf(pending))
        val availability = result.getValue(CommandType.PAUSAR)
        assertFalse(availability.enabled)
        assertEquals("Esperando respuesta del robot…", availability.reason)
    }

    @Test
    fun `volver al galpon deshabilitado si ya esta en el galpon`() {
        val result = useCase(status(telemetry(OperationalState.EN_BASE)), emptyList(), emptyList())
        assertFalse(result.getValue(CommandType.VOLVER_A_BASE).enabled)
    }
}
