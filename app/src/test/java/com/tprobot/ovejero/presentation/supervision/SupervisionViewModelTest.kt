package com.tprobot.ovejero.presentation.supervision

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.MissionStatus
import com.tprobot.ovejero.domain.model.OperationalState
import com.tprobot.ovejero.domain.usecase.AcknowledgeAlertUseCase
import com.tprobot.ovejero.domain.usecase.CancelMissionUseCase
import com.tprobot.ovejero.domain.usecase.GetCommandAvailabilityUseCase
import com.tprobot.ovejero.domain.usecase.ObserveAlertsUseCase
import com.tprobot.ovejero.domain.usecase.ObserveCommandsUseCase
import com.tprobot.ovejero.domain.usecase.ObserveRobotStatusUseCase
import com.tprobot.ovejero.domain.usecase.OperationalStopUseCase
import com.tprobot.ovejero.domain.usecase.PauseMissionUseCase
import com.tprobot.ovejero.domain.usecase.ResumeMissionUseCase
import com.tprobot.ovejero.domain.usecase.ReturnToBaseUseCase
import com.tprobot.ovejero.presentation.common.StatusPresenter
import com.tprobot.ovejero.testutil.EffectCollector
import com.tprobot.ovejero.testutil.FakeRobotRepository
import com.tprobot.ovejero.testutil.Fixtures
import com.tprobot.ovejero.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SupervisionViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeRobotRepository()

    private fun viewModel(): SupervisionViewModel {
        val availability = GetCommandAvailabilityUseCase()
        return SupervisionViewModel(
            observeRobotStatusUseCase = ObserveRobotStatusUseCase(repository),
            observeAlertsUseCase = ObserveAlertsUseCase(repository),
            observeCommandsUseCase = ObserveCommandsUseCase(repository),
            getCommandAvailabilityUseCase = availability,
            pauseMissionUseCase = PauseMissionUseCase(repository, availability),
            resumeMissionUseCase = ResumeMissionUseCase(repository, availability),
            cancelMissionUseCase = CancelMissionUseCase(repository, availability),
            returnToBaseUseCase = ReturnToBaseUseCase(repository, availability),
            operationalStopUseCase = OperationalStopUseCase(repository, availability),
            acknowledgeAlertUseCase = AcknowledgeAlertUseCase(repository)
        )
    }

    private fun data(vm: SupervisionViewModel) = (vm.uiState.value as UiState.Success).data

    private fun missionInProgress() = Fixtures.status(
        Fixtures.telemetry(OperationalState.EN_MISION, mission = Fixtures.mission(MissionStatus.EN_CURSO))
    )

    @Test
    fun `arranca con el estado desconectado y sin panel de telemetria`() {
        val vm = viewModel()

        val data = data(vm)
        assertNull(data.telemetryPanel)
        assertNull(data.pendingDecision)
        assertNull(data.lastCommand)
        assertEquals(0, data.unacknowledgedAlerts)
    }

    @Test
    fun `con telemetria arma el panel ya resuelto`() {
        val status = Fixtures.status(Fixtures.telemetry(battery = 20))
        repository.statusFlow.value = status

        val panel = data(viewModel()).telemetryPanel

        assertEquals(StatusPresenter.telemetryPanel(status, status.telemetry!!), panel)
    }

    @Test
    fun `cuenta las alertas sin reconocer y expone la ultima orden`() {
        repository.alertsFlow.value = listOf(
            Fixtures.alert(id = "A"),
            Fixtures.alert(id = "B", acknowledged = true)
        )
        repository.commandsFlow.value = listOf(Fixtures.commandRecord(id = "CMD-7"))

        val data = data(viewModel())

        assertEquals(1, data.unacknowledgedAlerts)
        assertEquals("CMD-7", data.lastCommand?.id)
    }

    @Test
    fun `la decision pendiente es la alerta mas nueva que la pide`() {
        repository.statusFlow.value = Fixtures.status(
            Fixtures.telemetry(OperationalState.ESPERANDO_DECISION, mission = Fixtures.mission(MissionStatus.PAUSADA))
        )
        repository.alertsFlow.value = listOf(
            Fixtures.alert(id = "VIEJA", requiresDecision = true, timestamp = 10),
            Fixtures.alert(id = "NUEVA", requiresDecision = true, timestamp = 20),
            Fixtures.alert(id = "INFO", requiresDecision = false, timestamp = 30)
        )

        assertEquals("NUEVA", data(viewModel()).pendingDecision?.id)
    }

    @Test
    fun `sin esperar decision no hay decision pendiente`() {
        repository.statusFlow.value = missionInProgress()
        repository.alertsFlow.value = listOf(Fixtures.alert(requiresDecision = true))

        assertNull(data(viewModel()).pendingDecision)
    }

    @Test
    fun `la disponibilidad de ordenes viene calculada`() {
        repository.statusFlow.value = missionInProgress()

        val availability = data(viewModel()).availability

        assertTrue(availability.getValue(CommandType.PAUSAR).enabled)
        assertTrue(!availability.getValue(CommandType.INICIAR_MISION).enabled)
    }

    @Test
    fun `se actualiza cuando cambia la telemetria`() {
        val vm = viewModel()
        repository.statusFlow.value = Fixtures.status(Fixtures.telemetry(battery = 55))

        assertEquals("55 %", data(vm).telemetryPanel?.battery?.value)
    }

    @Test
    fun `un error al leer el estado se muestra como error`() {
        repository.statusSource = flow { throw IllegalStateException("boom") }

        val vm = viewModel()

        assertEquals(UiState.Error("No se pudo leer el estado del robot: boom"), vm.uiState.value)
    }

    @Test
    fun `las ordenes enviadas avisan que esperan confirmacion`() {
        repository.statusFlow.value = missionInProgress()
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        vm.pause()
        vm.operationalStop()

        assertEquals(
            listOf(
                SupervisionEffect.ShowSnackbar("Pausar: enviada, esperando confirmación…"),
                SupervisionEffect.ShowSnackbar("Detención operativa: enviada, esperando confirmación…")
            ),
            effects.items
        )
        effects.stop()
    }

    @Test
    fun `cada boton manda su orden`() {
        repository.statusFlow.value = Fixtures.status(
            Fixtures.telemetry(OperationalState.PAUSADO, mission = Fixtures.mission(MissionStatus.PAUSADA))
        )
        repository.alertsFlow.value = listOf(Fixtures.alert(id = "AL-1"))
        val vm = viewModel()

        vm.resume()
        vm.cancel()
        vm.returnToBase()
        vm.acknowledge("AL-1")

        assertEquals(
            listOf(
                CommandType.REANUDAR,
                CommandType.CANCELAR,
                CommandType.VOLVER_A_BASE,
                CommandType.RECONOCER_ALERTA
            ),
            repository.sentCommands.map { it.type }
        )
    }

    @Test
    fun `una orden rechazada por reglas muestra el motivo`() {
        val vm = viewModel() // robot desconectado
        val effects = EffectCollector(vm.effectFlow)

        vm.pause()

        assertEquals(
            listOf(SupervisionEffect.ShowSnackbar("Pausar: No hay un robot conectado")),
            effects.items
        )
        effects.stop()
    }

    @Test
    fun `un error del repositorio al enviar tambien se muestra`() {
        repository.statusFlow.value = missionInProgress()
        repository.sendResult = Resource.Error("sin enlace")
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        vm.pause()

        assertEquals(listOf(SupervisionEffect.ShowSnackbar("Pausar: sin enlace")), effects.items)
        effects.stop()
    }
}
