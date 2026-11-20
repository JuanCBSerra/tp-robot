package com.tprobot.ovejero.presentation.shell

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.model.AlertSeverity
import com.tprobot.ovejero.domain.model.CommandStatus
import com.tprobot.ovejero.domain.model.HerdStatus
import com.tprobot.ovejero.domain.model.MissionStatus
import com.tprobot.ovejero.domain.model.OperationalState
import com.tprobot.ovejero.domain.usecase.DisconnectRobotUseCase
import com.tprobot.ovejero.domain.usecase.ObserveAlertsUseCase
import com.tprobot.ovejero.domain.usecase.ObserveCommandsUseCase
import com.tprobot.ovejero.domain.usecase.ObserveRobotStatusUseCase
import com.tprobot.ovejero.testutil.EffectCollector
import com.tprobot.ovejero.testutil.FakeRobotRepository
import com.tprobot.ovejero.testutil.Fixtures
import com.tprobot.ovejero.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShellViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeRobotRepository()

    private fun viewModel() = ShellViewModel(
        observeRobotStatusUseCase = ObserveRobotStatusUseCase(repository),
        observeAlertsUseCase = ObserveAlertsUseCase(repository),
        observeCommandsUseCase = ObserveCommandsUseCase(repository),
        disconnectRobotUseCase = DisconnectRobotUseCase(repository)
    )

    private fun snackbar(message: String) = ShellEffect.ShowSnackbar(message)

    private fun missionStatus(
        status: MissionStatus,
        counted: Int = 45,
        id: String = "MIS-1"
    ) = Fixtures.status(
        Fixtures.telemetry(
            OperationalState.EN_MISION,
            mission = Fixtures.mission(status = status, id = id),
            herd = HerdStatus(counted, 45, 0)
        )
    )

    // --- Estado y batería ---

    @Test
    fun `sin telemetria no hay texto de bateria`() {
        val vm = viewModel()
        assertEquals("", vm.batterySuffix.value)
        assertEquals(0, vm.unacknowledgedAlerts.value)
    }

    @Test
    fun `publica el estado y el texto de bateria de la barra superior`() {
        val vm = viewModel()
        val status = Fixtures.status(Fixtures.telemetry(battery = 22))

        repository.statusFlow.value = status

        assertEquals(status, vm.status.value)
        assertEquals(" · 🔋 22 % ⚠️", vm.batterySuffix.value)
    }

    // --- Alertas ---

    @Test
    fun `avisa de cada alerta nueva una sola vez y en orden`() {
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)
        val critical = Fixtures.alert(id = "B", severity = AlertSeverity.CRITICA, timestamp = 20, message = "alambre")
        val warning = Fixtures.alert(id = "A", severity = AlertSeverity.ADVERTENCIA, timestamp = 10, message = "viento")

        repository.alertsFlow.value = listOf(critical, warning)
        repository.alertsFlow.value = listOf(critical, warning, Fixtures.alert(id = "C", acknowledged = true, timestamp = 30))

        assertEquals(
            listOf(
                snackbar("⚠️ Obstáculo en el camino: viento"),
                snackbar("🚨 Obstáculo en el camino: alambre"),
                snackbar("🚨 Obstáculo en el camino: mensaje")
            ),
            effects.items
        )
        assertEquals(2, vm.unacknowledgedAlerts.value)
        effects.stop()
    }

    // --- Resultado de las órdenes ---

    @Test
    fun `avisa cuando una orden pendiente se resuelve`() {
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        repository.commandsFlow.value = listOf(
            Fixtures.commandRecord(id = "1"),
            Fixtures.commandRecord(id = "2"),
            Fixtures.commandRecord(id = "3"),
            Fixtures.commandRecord(id = "4")
        )
        repository.commandsFlow.value = listOf(
            Fixtures.commandRecord(id = "1", status = CommandStatus.CONFIRMADA),
            Fixtures.commandRecord(id = "2", status = CommandStatus.RECHAZADA, rejectionReason = "batería baja"),
            Fixtures.commandRecord(id = "3", status = CommandStatus.RECHAZADA),
            Fixtures.commandRecord(id = "4", status = CommandStatus.SIN_RESPUESTA)
        )

        assertEquals(
            listOf(
                snackbar("✅ Pausar: confirmada por el robot"),
                snackbar("⛔ Pausar: rechazada · batería baja"),
                snackbar("⛔ Pausar: rechazada · sin motivo"),
                snackbar("❓ Pausar: sin respuesta (no sabemos si se ejecutó)")
            ),
            effects.items
        )
        effects.stop()
    }

    @Test
    fun `no avisa de ordenes que ya llegan resueltas ni de las que siguen pendientes`() {
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        repository.commandsFlow.value = listOf(Fixtures.commandRecord(id = "1", status = CommandStatus.CONFIRMADA))
        repository.commandsFlow.value = listOf(
            Fixtures.commandRecord(id = "1", status = CommandStatus.CONFIRMADA),
            Fixtures.commandRecord(id = "2")
        )

        assertTrue(effects.items.isEmpty())
        effects.stop()
    }

    // --- Misión completada ---

    @Test
    fun `avisa cuando la mision se completa con todos los animales`() {
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        repository.statusFlow.value = missionStatus(MissionStatus.EN_CURSO)
        repository.statusFlow.value = missionStatus(MissionStatus.COMPLETADA)

        assertEquals(listOf(snackbar("✅ Misión completada: 45 de 45 animales contados")), effects.items)
        effects.stop()
    }

    @Test
    fun `avisa con faltantes cuando la mision pausada termina sin contar todo`() {
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        repository.statusFlow.value = missionStatus(MissionStatus.PAUSADA, counted = 42)
        repository.statusFlow.value = missionStatus(MissionStatus.COMPLETADA, counted = 42)

        assertEquals(
            listOf(snackbar("⚠️ Misión completada con faltantes: 42 de 45 animales contados")),
            effects.items
        )
        effects.stop()
    }

    @Test
    fun `no avisa si la primera telemetria ya trae la mision completada`() {
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        repository.statusFlow.value = missionStatus(MissionStatus.COMPLETADA)

        assertTrue(effects.items.isEmpty())
        effects.stop()
    }

    @Test
    fun `no avisa dos veces la misma mision ni cuando se cancela o no hay mision`() {
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        repository.statusFlow.value = Fixtures.status(Fixtures.telemetry(OperationalState.EN_BASE))
        repository.statusFlow.value = missionStatus(MissionStatus.EN_CURSO, id = "A")
        repository.statusFlow.value = missionStatus(MissionStatus.CANCELADA, id = "A")
        repository.statusFlow.value = missionStatus(MissionStatus.EN_CURSO, id = "B")
        repository.statusFlow.value = missionStatus(MissionStatus.COMPLETADA, id = "B")
        repository.statusFlow.value = missionStatus(MissionStatus.COMPLETADA, id = "B", counted = 44)

        assertEquals(1, effects.items.size)
        effects.stop()
    }

    @Test
    fun `sin telemetria el estado se publica pero no hay aviso`() {
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        repository.statusFlow.value = Fixtures.status(telemetry = null)

        assertTrue(effects.items.isEmpty())
        effects.stop()
    }

    // --- Desconectar ---

    @Test
    fun `desconectar navega a la seleccion y olvida lo ya avisado`() {
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)
        val alert = Fixtures.alert(id = "A", message = "x")
        repository.alertsFlow.value = listOf(alert)

        vm.disconnect()
        repository.alertsFlow.value = emptyList()
        repository.alertsFlow.value = listOf(alert)

        assertEquals(
            listOf(
                snackbar("🚨 Obstáculo en el camino: x"),
                ShellEffect.NavigateToSelection,
                snackbar("🚨 Obstáculo en el camino: x")
            ),
            effects.items
        )
        assertEquals(1, repository.disconnectCalls)
        effects.stop()
    }

    @Test
    fun `desconectar tambien olvida las misiones y las ordenes vistas`() {
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)
        repository.statusFlow.value = missionStatus(MissionStatus.EN_CURSO)
        repository.commandsFlow.value = listOf(Fixtures.commandRecord(id = "1"))

        vm.disconnect()
        repository.statusFlow.value = missionStatus(MissionStatus.COMPLETADA)
        repository.commandsFlow.value = listOf(Fixtures.commandRecord(id = "1", status = CommandStatus.CONFIRMADA))

        assertEquals(listOf<ShellEffect>(ShellEffect.NavigateToSelection), effects.items)
        effects.stop()
    }

    @Test
    fun `si no se puede desconectar avisa el error`() {
        repository.disconnectResult = Resource.Error("no se pudo")
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        vm.disconnect()

        assertEquals(listOf(snackbar("no se pudo")), effects.items)
        effects.stop()
    }
}
