package com.tprobot.ovejero.domain.model

import com.tprobot.ovejero.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelsTest {

    @Test
    fun `RobotStatus esta viejo solo sin senial`() {
        assertTrue(Fixtures.status(connection = ConnectionState.SIN_SENIAL).isStale)
        assertFalse(Fixtures.status(connection = ConnectionState.CONECTADO).isStale)
        assertFalse(Fixtures.status(connection = ConnectionState.DESCONECTADO).isStale)
    }

    @Test
    fun `segundos desde la ultima actualizacion`() {
        val status = Fixtures.status(lastUpdate = 1_000, checkedAt = 6_500)
        assertEquals(5L, status.secondsSinceUpdate)
        assertNull(Fixtures.status(lastUpdate = null).secondsSinceUpdate)
        // Si el reloj va para atrás nunca da negativo.
        assertEquals(0L, Fixtures.status(lastUpdate = 9_000, checkedAt = 1_000).secondsSinceUpdate)
    }

    @Test
    fun `estado desconectado de fabrica`() {
        val status = RobotStatus.disconnected(now = 123)
        assertNull(status.robot)
        assertNull(status.telemetry)
        assertNull(status.lastUpdateMillis)
        assertEquals(ConnectionState.DESCONECTADO, status.connection)
        assertEquals(123L, status.checkedAtMillis)
    }

    @Test
    fun `etiquetas de conexion`() {
        assertEquals("Sin señal", ConnectionState.SIN_SENIAL.label)
        assertEquals("Conectado", ConnectionState.CONECTADO.label)
    }

    @Test
    fun `indice de calor usa la humedad que informa el robot`() {
        val telemetry = Fixtures.telemetry(temperature = 33.0, humidity = 62)
        assertEquals(HeatStress.thi(33.0, 62), telemetry.heatIndex, 0.0001)
    }

    @Test
    fun `humedad por defecto es la asumida`() {
        val telemetry = Telemetry(
            robotId = "OVJ-01", timestampMillis = 0, sequence = 1, batteryPercent = 50, isCharging = false,
            operationalState = OperationalState.EN_BASE, mission = null,
            position = Position(0f, 0f, "Galpón", 0), motion = Motion(MotionMode.QUIETO, 0.0),
            herd = HerdStatus(0, 45, 0), temperatureCelsius = 25.0, signalPercent = 80
        )
        assertEquals(OperationRules.ASSUMED_HUMIDITY_PERCENT, telemetry.humidityPercent)
    }

    @Test
    fun `animales faltantes nunca es negativo`() {
        assertEquals(15, HerdStatus(30, 45, 0).missing)
        assertEquals(0, HerdStatus(45, 45, 0).missing)
        assertEquals(0, HerdStatus(50, 45, 0).missing)
    }

    @Test
    fun `mision activa solo en curso o pausada`() {
        assertTrue(Fixtures.mission(MissionStatus.EN_CURSO).isActive)
        assertTrue(Fixtures.mission(MissionStatus.PAUSADA).isActive)
        assertFalse(Fixtures.mission(MissionStatus.COMPLETADA).isActive)
        assertFalse(Fixtures.mission(MissionStatus.CANCELADA).isActive)
        assertFalse(Fixtures.mission(MissionStatus.ABORTADA).isActive)
    }

    @Test
    fun `duracion de la mision en minutos`() {
        assertEquals(10L, Fixtures.missionRecord(start = 0, end = 600_000).durationMinutes)
        assertEquals(0L, Fixtures.missionRecord(start = 600_000, end = 0).durationMinutes)
    }

    @Test
    fun `solo los robots disponibles se pueden elegir`() {
        assertTrue(Fixtures.robot(availability = RobotAvailability.DISPONIBLE).isSelectable)
        assertFalse(Fixtures.robot(availability = RobotAvailability.EN_MANTENIMIENTO).isSelectable)
        assertFalse(Fixtures.robot(availability = RobotAvailability.FUERA_DE_ALCANCE).isSelectable)
    }

    @Test
    fun `los umbrales de bateria son coherentes`() {
        assertTrue(OperationRules.MIN_BATTERY_TO_RESUME < OperationRules.LOW_BATTERY_WARNING)
        assertTrue(OperationRules.LOW_BATTERY_WARNING < OperationRules.MIN_BATTERY_TO_START)
        assertTrue(OperationRules.HEAT_THI_WARNING < OperationRules.HEAT_THI_DANGER)
    }

    @Test
    fun `etiquetas de enums que se muestran en pantalla`() {
        assertEquals("Calor: riesgo para los animales", AlertType.ESTRES_TERMICO.label)
        assertEquals("Crítica", AlertSeverity.CRITICA.label)
        assertEquals("Pendiente", CommandStatus.PENDIENTE.label)
        assertEquals("Volver al galpón", CommandType.VOLVER_A_BASE.label)
        assertEquals("Misión", EventCategory.MISION.label)
        assertEquals("x10", SimulationSpeed.X10.label)
        assertEquals(10, SimulationSpeed.X10.multiplier)
        assertEquals(6, Scenario.values().size)
    }
}
