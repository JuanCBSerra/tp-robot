package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.model.CommandStatus
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.ConnectionState
import com.tprobot.ovejero.domain.model.MissionStatus
import com.tprobot.ovejero.domain.model.MissionType
import com.tprobot.ovejero.domain.model.OperationalState
import com.tprobot.ovejero.testutil.FakeRobotRepository
import com.tprobot.ovejero.testutil.Fixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los casos de uso de órdenes validan las condiciones de habilitación antes de mandar nada. */
class CommandUseCasesTest {

    private val repository = FakeRobotRepository()
    private val availability = GetCommandAvailabilityUseCase()

    private fun errorOf(result: Resource<*>): String = (result as Resource.Error).message

    // --- Iniciar ---

    @Test
    fun `iniciar mision manda la orden con el tipo elegido`() = runBlocking {
        repository.statusFlow.value = Fixtures.status(Fixtures.telemetry(OperationalState.EN_BASE, battery = 80))

        val result = StartMissionUseCase(repository, availability)(MissionType.RECUENTO)

        assertTrue(result is Resource.Success)
        assertEquals(1, repository.sentCommands.size)
        assertEquals(CommandType.INICIAR_MISION, repository.sentCommands[0].type)
        assertEquals(MissionType.RECUENTO, repository.sentCommands[0].missionType)
    }

    @Test
    fun `iniciar mision con bateria insuficiente no manda nada`() = runBlocking {
        repository.statusFlow.value = Fixtures.status(Fixtures.telemetry(OperationalState.EN_BASE, battery = 10))

        val result = StartMissionUseCase(repository, availability)(MissionType.ARREO)

        assertTrue(errorOf(result).contains("Batería insuficiente"))
        assertTrue(repository.sentCommands.isEmpty())
    }

    @Test
    fun `iniciar mision sin robot conectado da el motivo`() = runBlocking {
        val result = StartMissionUseCase(repository, availability)(MissionType.ARREO)
        assertEquals("No hay un robot conectado", errorOf(result))
    }

    @Test
    fun `una orden pendiente bloquea otra igual`() = runBlocking {
        repository.statusFlow.value = Fixtures.status(Fixtures.telemetry(OperationalState.EN_BASE))
        repository.commandsFlow.value = listOf(Fixtures.commandRecord(type = CommandType.INICIAR_MISION))

        val result = StartMissionUseCase(repository, availability)(MissionType.ARREO)

        assertEquals("Esperando respuesta del robot…", errorOf(result))
    }

    // --- Pausar ---

    @Test
    fun `pausar con una mision en marcha manda la orden`() = runBlocking {
        repository.statusFlow.value = Fixtures.status(
            Fixtures.telemetry(OperationalState.EN_MISION, mission = Fixtures.mission(MissionStatus.EN_CURSO))
        )
        val result = PauseMissionUseCase(repository, availability)()
        assertTrue(result is Resource.Success)
        assertEquals(CommandType.PAUSAR, repository.sentCommands.single().type)
    }

    @Test
    fun `pausar sin mision da el motivo`() = runBlocking {
        repository.statusFlow.value = Fixtures.status(Fixtures.telemetry(OperationalState.EN_BASE))
        val result = PauseMissionUseCase(repository, availability)()
        assertEquals("No hay una misión en marcha", errorOf(result))
        assertTrue(repository.sentCommands.isEmpty())
    }

    // --- Reanudar ---

    @Test
    fun `reanudar una mision pausada manda la orden`() = runBlocking {
        repository.statusFlow.value = Fixtures.status(
            Fixtures.telemetry(OperationalState.PAUSADO, mission = Fixtures.mission(MissionStatus.PAUSADA))
        )
        val result = ResumeMissionUseCase(repository, availability)()
        assertTrue(result is Resource.Success)
        assertEquals(CommandType.REANUDAR, repository.sentCommands.single().type)
    }

    @Test
    fun `reanudar con una decision pendiente pide reconocer la alerta primero`() = runBlocking {
        repository.statusFlow.value = Fixtures.status(
            Fixtures.telemetry(
                OperationalState.ESPERANDO_DECISION,
                mission = Fixtures.mission(MissionStatus.PAUSADA)
            )
        )
        repository.alertsFlow.value = listOf(Fixtures.alert(requiresDecision = true))

        val result = ResumeMissionUseCase(repository, availability)()

        assertEquals("Primero reconocé la alerta que pide tu decisión", errorOf(result))
    }

    // --- Cancelar ---

    @Test
    fun `cancelar una mision activa manda la orden`() = runBlocking {
        repository.statusFlow.value = Fixtures.status(
            Fixtures.telemetry(OperationalState.EN_MISION, mission = Fixtures.mission())
        )
        val result = CancelMissionUseCase(repository, availability)()
        assertTrue(result is Resource.Success)
        assertEquals(CommandType.CANCELAR, repository.sentCommands.single().type)
    }

    @Test
    fun `cancelar sin mision da el motivo`() = runBlocking {
        repository.statusFlow.value = Fixtures.status(Fixtures.telemetry(OperationalState.EN_BASE))
        val result = CancelMissionUseCase(repository, availability)()
        assertEquals("No hay una misión para cancelar", errorOf(result))
    }

    // --- Volver al galpón ---

    @Test
    fun `volver al galpon desde el campo manda la orden`() = runBlocking {
        repository.statusFlow.value = Fixtures.status(
            Fixtures.telemetry(OperationalState.EN_MISION, mission = Fixtures.mission())
        )
        val result = ReturnToBaseUseCase(repository, availability)()
        assertTrue(result is Resource.Success)
        assertEquals(CommandType.VOLVER_A_BASE, repository.sentCommands.single().type)
    }

    @Test
    fun `volver al galpon estando en el galpon da el motivo`() = runBlocking {
        repository.statusFlow.value = Fixtures.status(Fixtures.telemetry(OperationalState.EN_BASE))
        val result = ReturnToBaseUseCase(repository, availability)()
        assertEquals("El robot ya está en el galpón", errorOf(result))
    }

    // --- Detención operativa ---

    @Test
    fun `la detencion se intenta aun sin senial`() = runBlocking {
        repository.statusFlow.value = Fixtures.status(
            Fixtures.telemetry(OperationalState.EN_MISION, mission = Fixtures.mission()),
            connection = ConnectionState.SIN_SENIAL
        )
        val result = OperationalStopUseCase(repository, availability)()
        assertTrue(result is Resource.Success)
        assertEquals(CommandType.DETENCION_OPERATIVA, repository.sentCommands.single().type)
    }

    @Test
    fun `la detencion sin robot conectado da el motivo`() = runBlocking {
        val result = OperationalStopUseCase(repository, availability)()
        assertEquals("No hay un robot conectado", errorOf(result))
    }

    @Test
    fun `la detencion con el robot quieto no se manda`() = runBlocking {
        repository.statusFlow.value = Fixtures.status(Fixtures.telemetry(OperationalState.EN_BASE))
        val result = OperationalStopUseCase(repository, availability)()
        assertEquals("El robot no está en movimiento", errorOf(result))
    }

    // --- Reconocer alerta ---

    private fun connect() {
        repository.statusFlow.value = Fixtures.status(Fixtures.telemetry(OperationalState.EN_MISION))
    }

    @Test
    fun `reconocer con id vacio es invalido`() = runBlocking {
        val result = AcknowledgeAlertUseCase(repository)("  ")
        assertEquals("Alerta inválida", errorOf(result))
    }

    @Test
    fun `reconocer sin conexion no se manda`() = runBlocking {
        repository.alertsFlow.value = listOf(Fixtures.alert())
        val result = AcknowledgeAlertUseCase(repository)("AL-1")
        assertTrue(errorOf(result).startsWith("Sin conexión"))
        assertTrue(repository.sentCommands.isEmpty())
    }

    @Test
    fun `reconocer una alerta que ya no existe`() = runBlocking {
        connect()
        val result = AcknowledgeAlertUseCase(repository)("AL-9")
        assertEquals("La alerta ya no existe", errorOf(result))
    }

    @Test
    fun `reconocer una alerta ya reconocida`() = runBlocking {
        connect()
        repository.alertsFlow.value = listOf(Fixtures.alert(acknowledged = true))
        val result = AcknowledgeAlertUseCase(repository)("AL-1")
        assertEquals("La alerta ya estaba reconocida", errorOf(result))
    }

    @Test
    fun `reconocer con un reconocimiento pendiente espera la respuesta`() = runBlocking {
        connect()
        repository.alertsFlow.value = listOf(Fixtures.alert())
        repository.commandsFlow.value = listOf(
            Fixtures.commandRecord(type = CommandType.RECONOCER_ALERTA, alertId = "AL-1")
        )
        val result = AcknowledgeAlertUseCase(repository)("AL-1")
        assertEquals("Ya se mandó el reconocimiento, esperando respuesta…", errorOf(result))
    }

    @Test
    fun `un reconocimiento ya resuelto no bloquea uno nuevo`() = runBlocking {
        connect()
        repository.alertsFlow.value = listOf(Fixtures.alert())
        repository.commandsFlow.value = listOf(
            Fixtures.commandRecord(
                type = CommandType.RECONOCER_ALERTA,
                alertId = "AL-1",
                status = CommandStatus.RECHAZADA
            )
        )
        val result = AcknowledgeAlertUseCase(repository)("AL-1")
        assertTrue(result is Resource.Success)
    }

    @Test
    fun `reconocer manda la orden con el id de la alerta`() = runBlocking {
        connect()
        repository.alertsFlow.value = listOf(Fixtures.alert())
        val result = AcknowledgeAlertUseCase(repository)("AL-1")
        assertTrue(result is Resource.Success)
        val sent = repository.sentCommands.single()
        assertEquals(CommandType.RECONOCER_ALERTA, sent.type)
        assertEquals("AL-1", sent.alertId)
    }
}
