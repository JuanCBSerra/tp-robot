package com.tprobot.ovejero.data.mapper

import com.tprobot.ovejero.data.local.entity.EventEntity
import com.tprobot.ovejero.data.local.entity.MissionEntity
import com.tprobot.ovejero.data.remote.RobotProtocol
import com.tprobot.ovejero.data.remote.dto.AlertDto
import com.tprobot.ovejero.data.remote.dto.RobotSummaryDto
import com.tprobot.ovejero.domain.model.AlertSeverity
import com.tprobot.ovejero.domain.model.AlertType
import com.tprobot.ovejero.domain.model.CommandStatus
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.EventCategory
import com.tprobot.ovejero.domain.model.MissionStatus
import com.tprobot.ovejero.domain.model.MissionType
import com.tprobot.ovejero.domain.model.RobotAvailability
import com.tprobot.ovejero.domain.model.RobotCommand
import com.tprobot.ovejero.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {

    // --- Alertas ---

    private fun alertDto(
        code: String = RobotProtocol.AlertCode.OBSTACLE,
        severity: String = RobotProtocol.Severity.CRITICAL,
        message: String? = "Alambrado caído",
        requiresDecision: Boolean? = true
    ) = AlertDto(
        type = RobotProtocol.MessageType.ALERT,
        alertId = "AL-001",
        robotId = "OVJ-01",
        timestamp = 1_000,
        code = code,
        severity = severity,
        message = message,
        zone = "Potrero 4",
        requiresDecision = requiresDecision
    )

    @Test
    fun `alerta del robot pasa al dominio sin reconocer`() {
        val alert = AlertMapper.toDomain(alertDto())

        assertEquals("AL-001", alert.id)
        assertEquals(AlertType.OBSTACULO, alert.type)
        assertEquals(AlertSeverity.CRITICA, alert.severity)
        assertEquals("Alambrado caído", alert.message)
        assertEquals("Potrero 4", alert.zoneName)
        assertEquals(1_000L, alert.timestampMillis)
        assertTrue(alert.requiresDecision)
        assertFalse(alert.acknowledged)
    }

    @Test
    fun `alerta sin mensaje ni decision usa valores por defecto`() {
        val alert = AlertMapper.toDomain(alertDto(message = null, requiresDecision = null))

        assertEquals(AlertType.OBSTACULO.label, alert.message)
        assertFalse(alert.requiresDecision)
    }

    @Test
    fun `cada codigo de alerta tiene su tipo`() {
        val expected = mapOf(
            RobotProtocol.AlertCode.LOW_BATTERY to AlertType.BATERIA_BAJA,
            RobotProtocol.AlertCode.CRITICAL_BATTERY to AlertType.BATERIA_CRITICA,
            RobotProtocol.AlertCode.AUTO_RETURN to AlertType.REGRESO_AUTOMATICO,
            RobotProtocol.AlertCode.OBSTACLE to AlertType.OBSTACULO,
            RobotProtocol.AlertCode.STORM to AlertType.TORMENTA,
            RobotProtocol.AlertCode.PERSON_NEARBY to AlertType.PERSONA_CERCA,
            RobotProtocol.AlertCode.MISSING_ANIMALS to AlertType.ANIMALES_FALTANTES,
            "OTRO_CODIGO" to AlertType.DESCONOCIDA
        )
        expected.forEach { (code, type) -> assertEquals(code, type, AlertMapper.toAlertType(code)) }
    }

    @Test
    fun `cada gravedad tiene su nivel`() {
        assertEquals(AlertSeverity.CRITICA, AlertMapper.toSeverity(RobotProtocol.Severity.CRITICAL))
        assertEquals(AlertSeverity.ADVERTENCIA, AlertMapper.toSeverity(RobotProtocol.Severity.WARNING))
        assertEquals(AlertSeverity.INFO, AlertMapper.toSeverity(RobotProtocol.Severity.INFO))
        assertEquals(AlertSeverity.INFO, AlertMapper.toSeverity("DESCONOCIDA"))
    }

    // --- Órdenes ---

    @Test
    fun `cada orden tiene su codigo del contrato`() {
        val expected = mapOf(
            CommandType.INICIAR_MISION to RobotProtocol.Command.START_MISSION,
            CommandType.PAUSAR to RobotProtocol.Command.PAUSE_MISSION,
            CommandType.REANUDAR to RobotProtocol.Command.RESUME_MISSION,
            CommandType.CANCELAR to RobotProtocol.Command.CANCEL_MISSION,
            CommandType.VOLVER_A_BASE to RobotProtocol.Command.RETURN_TO_BASE,
            CommandType.DETENCION_OPERATIVA to RobotProtocol.Command.OPERATIONAL_STOP,
            CommandType.RECONOCER_ALERTA to RobotProtocol.Command.ACK_ALERT
        )
        assertEquals(CommandType.values().size, expected.size)
        expected.forEach { (type, code) -> assertEquals(code, CommandMapper.toCode(type)) }
    }

    @Test
    fun `la respuesta del robot se traduce al estado de la orden`() {
        assertEquals(CommandStatus.CONFIRMADA, CommandMapper.toStatus(RobotProtocol.AckStatus.ACCEPTED))
        assertEquals(CommandStatus.RECHAZADA, CommandMapper.toStatus(RobotProtocol.AckStatus.REJECTED))
        assertEquals(CommandStatus.RECHAZADA, CommandMapper.toStatus("ALGO_RARO"))
    }

    @Test
    fun `iniciar mision lleva el tipo de mision en los parametros`() {
        val dto = CommandMapper.toDto(
            "CMD-1", "OVJ-01", RobotCommand(CommandType.INICIAR_MISION, missionType = MissionType.ARREO), 99
        )

        assertEquals(RobotProtocol.MessageType.COMMAND, dto.type)
        assertEquals("CMD-1", dto.commandId)
        assertEquals("OVJ-01", dto.robotId)
        assertEquals(99L, dto.timestamp)
        assertEquals(RobotProtocol.Command.START_MISSION, dto.command)
        assertEquals(RobotProtocol.MissionKind.HERDING, dto.params?.missionKind)
        assertNull(dto.params?.alertId)
    }

    @Test
    fun `reconocer alerta lleva el id de la alerta en los parametros`() {
        val dto = CommandMapper.toDto(
            "CMD-2", "OVJ-01", RobotCommand(CommandType.RECONOCER_ALERTA, alertId = "AL-001"), 99
        )

        assertEquals("AL-001", dto.params?.alertId)
        assertNull(dto.params?.missionKind)
    }

    @Test
    fun `una orden sin datos extra no lleva parametros`() {
        val dto = CommandMapper.toDto("CMD-3", "OVJ-01", RobotCommand(CommandType.PAUSAR), 99)

        assertNull(dto.params)
        assertEquals(RobotProtocol.Command.PAUSE_MISSION, dto.command)
    }

    // --- Flota ---

    private fun robotDto(status: String, battery: Double? = 86.4, location: String? = "Galpón") = RobotSummaryDto(
        robotId = "OVJ-01", name = "Ovejero-01", model = "Go2 EDU",
        location = location, status = status, batteryPercent = battery, note = "nota"
    )

    @Test
    fun `robot disponible con todos los datos`() {
        val robot = RobotMapper.toDomain(robotDto(RobotProtocol.RobotStatus.AVAILABLE))

        assertEquals("OVJ-01", robot.id)
        assertEquals("Ovejero-01", robot.name)
        assertEquals("Go2 EDU", robot.model)
        assertEquals("Galpón", robot.location)
        assertEquals(RobotAvailability.DISPONIBLE, robot.availability)
        assertEquals(86, robot.batteryPercent)
        assertEquals("nota", robot.note)
    }

    @Test
    fun `robot en mantenimiento o fuera de alcance`() {
        assertEquals(
            RobotAvailability.EN_MANTENIMIENTO,
            RobotMapper.toDomain(robotDto(RobotProtocol.RobotStatus.MAINTENANCE)).availability
        )
        assertEquals(
            RobotAvailability.FUERA_DE_ALCANCE,
            RobotMapper.toDomain(robotDto(RobotProtocol.RobotStatus.OUT_OF_RANGE)).availability
        )
        assertEquals(RobotAvailability.FUERA_DE_ALCANCE, RobotMapper.toDomain(robotDto("???")).availability)
    }

    @Test
    fun `robot sin bateria ni ubicacion usa valores por defecto`() {
        val robot = RobotMapper.toDomain(robotDto(RobotProtocol.RobotStatus.AVAILABLE, battery = null, location = null))

        assertEquals(0, robot.batteryPercent)
        assertEquals("Ubicación desconocida", robot.location)
    }

    // --- Historial (Room) ---

    @Test
    fun `evento de historial va y vuelve de Room`() {
        val entity = HistoryMapper.toEntity(5_000, EventCategory.ALERTA, "Titulo", "Detalle")

        assertEquals(0L, entity.id)
        assertEquals("ALERTA", entity.category)

        val event = HistoryMapper.toDomain(entity.copy(id = 7))
        assertEquals(7L, event.id)
        assertEquals(5_000L, event.timestampMillis)
        assertEquals(EventCategory.ALERTA, event.category)
        assertEquals("Titulo", event.title)
        assertEquals("Detalle", event.detail)
    }

    @Test
    fun `categoria de evento desconocida cae en sistema`() {
        val entity = EventEntity(id = 1, timestamp = 0, category = "VIEJA", title = "t", detail = "d")
        assertEquals(EventCategory.SISTEMA, HistoryMapper.toDomain(entity).category)
    }

    @Test
    fun `mision va y vuelve de Room sin perder datos`() {
        val record = Fixtures.missionRecord(id = "MIS-9", start = 1_000, end = 601_000)

        val entity = HistoryMapper.toEntity(record)
        assertEquals("ARREO", entity.type)
        assertEquals("COMPLETADA", entity.result)
        assertEquals(1_000L, entity.startedAt)
        assertEquals(601_000L, entity.endedAt)

        assertEquals(record, HistoryMapper.toDomain(entity))
    }

    @Test
    fun `tipo y resultado de mision desconocidos usan los valores por defecto`() {
        val entity = MissionEntity(
            missionId = "M", robotName = "R", type = "RARO", result = "RARO",
            startedAt = 0, endedAt = 0, headsCounted = 0, headsExpected = 0, headsInCorral = 0,
            batteryUsedPercent = 0
        )

        val record = HistoryMapper.toDomain(entity)

        assertEquals(MissionType.RECUENTO, record.type)
        assertEquals(MissionStatus.ABORTADA, record.result)
    }
}
