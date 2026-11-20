package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.model.AlertSeverity
import com.tprobot.ovejero.domain.model.RobotAvailability
import com.tprobot.ovejero.domain.model.Scenario
import com.tprobot.ovejero.domain.model.SimulationAction
import com.tprobot.ovejero.testutil.FakeRobotRepository
import com.tprobot.ovejero.testutil.FakeSimulationRepository
import com.tprobot.ovejero.testutil.Fixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casos de uso de consulta, conexión, historial y simulación. */
class QueryUseCasesTest {

    private val repository = FakeRobotRepository()
    private val simulation = FakeSimulationRepository()

    // --- Alertas ordenadas ---

    @Test
    fun `alertas sin reconocer primero, despues criticas y mas nuevas`() = runBlocking {
        val oldAcknowledged = Fixtures.alert(id = "A", acknowledged = true, timestamp = 50)
        val warningNew = Fixtures.alert(id = "B", severity = AlertSeverity.ADVERTENCIA, timestamp = 40)
        val criticalOld = Fixtures.alert(id = "C", severity = AlertSeverity.CRITICA, timestamp = 10)
        val criticalNew = Fixtures.alert(id = "D", severity = AlertSeverity.CRITICA, timestamp = 30)
        val warningOld = Fixtures.alert(id = "E", severity = AlertSeverity.ADVERTENCIA, timestamp = 20)
        repository.alertsFlow.value = listOf(oldAcknowledged, warningNew, criticalOld, criticalNew, warningOld)

        val ids = ObserveAlertsUseCase(repository)().first().map { it.id }

        assertEquals(listOf("D", "C", "B", "E", "A"), ids)
    }

    // --- Pasan los datos tal cual ---

    @Test
    fun `observar estado, ordenes e historial devuelve lo del repositorio`() = runBlocking {
        val status = Fixtures.status()
        val commands = listOf(Fixtures.commandRecord())
        val missions = listOf(Fixtures.missionRecord())
        val events = listOf(Fixtures.historyEvent())
        repository.statusFlow.value = status
        repository.commandsFlow.value = commands
        repository.missionsFlow.value = missions
        repository.eventsFlow.value = events

        assertEquals(status, ObserveRobotStatusUseCase(repository)().first())
        assertEquals(commands, ObserveCommandsUseCase(repository)().first())
        assertEquals(missions, ObserveMissionHistoryUseCase(repository)().first())
        assertEquals(events, ObserveEventHistoryUseCase(repository)().first())
    }

    // --- Flota ---

    @Test
    fun `la flota se ordena con los disponibles primero`() = runBlocking {
        repository.robotsResult = Resource.Success(
            listOf(
                Fixtures.robot(id = "3", availability = RobotAvailability.FUERA_DE_ALCANCE),
                Fixtures.robot(id = "1", availability = RobotAvailability.DISPONIBLE),
                Fixtures.robot(id = "2", availability = RobotAvailability.EN_MANTENIMIENTO)
            )
        )

        val result = GetRobotsUseCase(repository)() as Resource.Success

        assertEquals(listOf("1", "2", "3"), result.data.map { it.id })
    }

    @Test
    fun `un error al listar la flota se propaga`() = runBlocking {
        repository.robotsResult = Resource.Error("sin datos")
        val result = GetRobotsUseCase(repository)()
        assertEquals("sin datos", (result as Resource.Error).message)
    }

    // --- Conexión ---

    @Test
    fun `no se conecta a un robot que no esta disponible`() = runBlocking {
        val robot = Fixtures.robot(name = "Ovejero-02", availability = RobotAvailability.EN_MANTENIMIENTO)

        val result = ConnectRobotUseCase(repository)(robot)

        assertEquals("Ovejero-02 no está disponible: en mantenimiento", (result as Resource.Error).message)
        assertTrue(repository.connectCalls.isEmpty())
    }

    @Test
    fun `se conecta a un robot disponible`() = runBlocking {
        val robot = Fixtures.robot(id = "OVJ-01")
        val result = ConnectRobotUseCase(repository)(robot)
        assertTrue(result is Resource.Success)
        assertEquals(listOf("OVJ-01"), repository.connectCalls)
    }

    @Test
    fun `desconectar y borrar historial pasan por el repositorio`() = runBlocking {
        assertTrue(DisconnectRobotUseCase(repository)() is Resource.Success)
        assertTrue(ClearHistoryUseCase(repository)() is Resource.Success)
        assertEquals(1, repository.disconnectCalls)
        assertEquals(1, repository.clearHistoryCalls)

        repository.disconnectResult = Resource.Error("falló")
        assertEquals("falló", (DisconnectRobotUseCase(repository)() as Resource.Error).message)
    }

    // --- Simulación ---

    @Test
    fun `aplicar una accion de simulacion y observar los ajustes`() = runBlocking {
        val action = SimulationAction.LoadScenario(Scenario.TORMENTA)

        val result = ApplySimulationActionUseCase(simulation)(action)

        assertTrue(result is Resource.Success)
        assertSame(action, simulation.applied.single())
        assertEquals(simulation.settingsFlow.value, ObserveSimulationSettingsUseCase(simulation)().first())
    }
}
