package com.tprobot.ovejero.data.simulator

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.data.local.dao.EventDao
import com.tprobot.ovejero.data.local.dao.MissionDao
import com.tprobot.ovejero.data.local.entity.EventEntity
import com.tprobot.ovejero.data.local.entity.MissionEntity
import com.tprobot.ovejero.data.repository.RobotRepositoryImpl
import com.tprobot.ovejero.data.repository.SimulationRepositoryImpl
import com.tprobot.ovejero.domain.model.AlertType
import com.tprobot.ovejero.domain.model.CommandStatus
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.ConnectionState
import com.tprobot.ovejero.domain.model.MissionStatus
import com.tprobot.ovejero.domain.model.MissionType
import com.tprobot.ovejero.domain.model.OperationalState
import com.tprobot.ovejero.domain.model.RobotCommand
import com.tprobot.ovejero.domain.model.Scenario
import com.tprobot.ovejero.domain.model.SimulationAction
import com.tprobot.ovejero.domain.model.SimulationSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test de integración (JVM, tiempo real): simulador + repositorio + contrato JSON.
 * Corre misiones a x10, así que tarda ~2 minutos en total.
 */
class SimulatorScenariosTest {

    private lateinit var scope: CoroutineScope
    private lateinit var repository: RobotRepositoryImpl
    private lateinit var simulation: SimulationRepositoryImpl
    private val missions = MutableStateFlow<List<MissionEntity>>(emptyList())

    @Before
    fun setUp() = runBlocking {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val simulator = RobotSimulator(moshi, scope)
        repository = RobotRepositoryImpl(simulator, moshi, FakeEventDao(), FakeMissionDao(missions), scope)
        simulation = SimulationRepositoryImpl(simulator, repository)
        simulation.apply(SimulationAction.SetSpeed(SimulationSpeed.X10))
        assertTrue(repository.getRobots() is Resource.Success)
        assertTrue(repository.connect("OVJ-01") is Resource.Success)
        waitUntil(5_000) { repository.observeRobotStatus().first().telemetry != null }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `mision normal de arreo termina con 45 de 45 en el corral`() = runBlocking {
        simulation.apply(SimulationAction.LoadScenario(Scenario.NORMAL))
        sendAndAwait(RobotCommand(CommandType.INICIAR_MISION, missionType = MissionType.ARREO), CommandStatus.CONFIRMADA)

        waitUntil(150_000) { missions.value.isNotEmpty() }
        val record = missions.value.first()
        assertEquals(MissionStatus.COMPLETADA.name, record.result)
        assertEquals(45, record.headsCounted)
        assertEquals(45, record.headsInCorral)
        val telemetry = repository.observeRobotStatus().first().telemetry!!
        assertEquals(OperationalState.EN_BASE, telemetry.operationalState)
    }

    @Test
    fun `alambrado caido frena al robot y pide decision`() = runBlocking {
        simulation.apply(SimulationAction.LoadScenario(Scenario.INCIDENTE))
        sendAndAwait(RobotCommand(CommandType.INICIAR_MISION, missionType = MissionType.RECUENTO), CommandStatus.CONFIRMADA)

        waitUntil(90_000) { repository.observeAlerts().first().any { it.type == AlertType.OBSTACULO } }
        waitUntil(3_000) {
            repository.observeRobotStatus().first().telemetry?.operationalState == OperationalState.ESPERANDO_DECISION
        }
        val alert = repository.observeAlerts().first().first { it.type == AlertType.OBSTACULO }

        // Sin reconocer, el robot rechaza reanudar
        sendAndAwait(RobotCommand(CommandType.REANUDAR), CommandStatus.RECHAZADA)
        // Reconocer y reanudar
        sendAndAwait(RobotCommand(CommandType.RECONOCER_ALERTA, alertId = alert.id), CommandStatus.CONFIRMADA)
        assertTrue(repository.observeAlerts().first().first { it.id == alert.id }.acknowledged)
        sendAndAwait(RobotCommand(CommandType.REANUDAR), CommandStatus.CONFIRMADA)
        waitUntil(3_000) {
            repository.observeRobotStatus().first().telemetry?.operationalState == OperationalState.EN_MISION
        }
    }

    @Test
    fun `sin enlace los datos quedan viejos y la orden queda sin respuesta`() = runBlocking {
        simulation.apply(SimulationAction.LoadScenario(Scenario.NORMAL))
        sendAndAwait(RobotCommand(CommandType.INICIAR_MISION, missionType = MissionType.RECUENTO), CommandStatus.CONFIRMADA)

        simulation.apply(SimulationAction.SetLinkDown(true))
        waitUntil(6_000) { repository.observeRobotStatus().first().connection == ConnectionState.SIN_SENIAL }
        sendAndAwait(RobotCommand(CommandType.DETENCION_OPERATIVA), CommandStatus.SIN_RESPUESTA, timeout = 8_000)

        simulation.apply(SimulationAction.SetLinkDown(false))
        waitUntil(3_000) { repository.observeRobotStatus().first().connection == ConnectionState.CONECTADO }
    }

    @Test
    fun `rechazo forzado y bateria insuficiente`() = runBlocking {
        simulation.apply(SimulationAction.LoadScenario(Scenario.NORMAL))
        simulation.apply(SimulationAction.RejectNextCommand)
        sendAndAwait(RobotCommand(CommandType.INICIAR_MISION, missionType = MissionType.ARREO), CommandStatus.RECHAZADA)

        simulation.apply(SimulationAction.LoadScenario(Scenario.BATERIA_BAJA))
        sendAndAwait(RobotCommand(CommandType.INICIAR_MISION, missionType = MissionType.ARREO), CommandStatus.CONFIRMADA)
        simulation.apply(SimulationAction.DrainBattery)
        waitUntil(5_000) { repository.observeAlerts().first().any { it.type == AlertType.BATERIA_BAJA } }
    }

    private suspend fun sendAndAwait(command: RobotCommand, expected: CommandStatus, timeout: Long = 4_000) {
        val sent = repository.sendCommand(command)
        assertTrue("No se pudo enviar $command: $sent", sent is Resource.Success)
        val id = (sent as Resource.Success).data.id
        waitUntil(timeout) {
            repository.observeCommands().first().first { it.id == id }.status != CommandStatus.PENDIENTE
        }
        val record = repository.observeCommands().first().first { it.id == id }
        assertEquals("Orden ${command.type} (${record.rejectionReason})", expected, record.status)
    }

    private suspend fun waitUntil(timeoutMillis: Long, condition: suspend () -> Boolean) {
        withTimeout(timeoutMillis) {
            while (!condition()) kotlinx.coroutines.delay(100)
        }
    }

    private class FakeEventDao : EventDao {
        private val events = MutableStateFlow<List<EventEntity>>(emptyList())
        override fun observeLatest(limit: Int): Flow<List<EventEntity>> = events
        override suspend fun insert(event: EventEntity) { events.value = listOf(event) + events.value }
        override suspend fun clear() { events.value = emptyList() }
    }

    private class FakeMissionDao(private val missions: MutableStateFlow<List<MissionEntity>>) : MissionDao {
        override fun observeAll(): Flow<List<MissionEntity>> = missions
        override suspend fun insert(mission: MissionEntity) { missions.value = listOf(mission) + missions.value }
        override suspend fun clear() { missions.value = emptyList() }
    }
}
