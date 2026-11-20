package com.tprobot.ovejero.presentation.control

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.MissionStatus
import com.tprobot.ovejero.domain.model.MissionType
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
import com.tprobot.ovejero.domain.usecase.StartMissionUseCase
import com.tprobot.ovejero.presentation.common.StatusPresenter
import com.tprobot.ovejero.presentation.common.components.Tone
import com.tprobot.ovejero.testutil.EffectCollector
import com.tprobot.ovejero.testutil.FakeRobotRepository
import com.tprobot.ovejero.testutil.Fixtures
import com.tprobot.ovejero.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ControlViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeRobotRepository()

    private fun viewModel(): ControlViewModel {
        val availability = GetCommandAvailabilityUseCase()
        return ControlViewModel(
            observeRobotStatusUseCase = ObserveRobotStatusUseCase(repository),
            observeAlertsUseCase = ObserveAlertsUseCase(repository),
            observeCommandsUseCase = ObserveCommandsUseCase(repository),
            getCommandAvailabilityUseCase = availability,
            startMissionUseCase = StartMissionUseCase(repository, availability),
            pauseMissionUseCase = PauseMissionUseCase(repository, availability),
            resumeMissionUseCase = ResumeMissionUseCase(repository, availability),
            cancelMissionUseCase = CancelMissionUseCase(repository, availability),
            returnToBaseUseCase = ReturnToBaseUseCase(repository, availability),
            operationalStopUseCase = OperationalStopUseCase(repository, availability),
            acknowledgeAlertUseCase = AcknowledgeAlertUseCase(repository)
        )
    }

    private fun data(vm: ControlViewModel) = (vm.uiState.value as UiState.Success).data

    private fun atBase(battery: Int = 80) =
        Fixtures.status(Fixtures.telemetry(OperationalState.EN_BASE, battery = battery))

    @Test
    fun `por defecto esta elegido el arreo`() {
        assertEquals(MissionType.ARREO, data(viewModel()).selectedMissionType)
    }

    @Test
    fun `se puede elegir otro tipo de mision`() {
        val vm = viewModel()

        vm.selectMissionType(MissionType.RECUENTO)

        assertEquals(MissionType.RECUENTO, data(vm).selectedMissionType)
    }

    @Test
    fun `iniciar manda el tipo elegido y avisa con el id de la orden`() {
        repository.statusFlow.value = atBase()
        repository.sendResult = Resource.Success(Fixtures.commandRecord(id = "CMD-9", type = CommandType.INICIAR_MISION))
        val vm = viewModel()
        vm.selectMissionType(MissionType.RECUENTO)
        val effects = EffectCollector(vm.effectFlow)

        vm.startMission()

        assertEquals(MissionType.RECUENTO, repository.sentCommands.single().missionType)
        assertEquals(
            listOf(ControlEffect.ShowSnackbar("Iniciar misión: enviada (CMD-9), esperando confirmación…")),
            effects.items
        )
        effects.stop()
    }

    @Test
    fun `iniciar con poca bateria muestra el motivo`() {
        repository.statusFlow.value = atBase(battery = 20)
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        vm.startMission()

        assertEquals(
            listOf(ControlEffect.ShowSnackbar("Iniciar misión: Batería insuficiente: mínimo 30 %")),
            effects.items
        )
        effects.stop()
    }

    @Test
    fun `la bateria frente al minimo viene resuelta`() {
        repository.statusFlow.value = atBase(battery = 29)

        val requirement = data(viewModel()).batteryRequirement

        assertEquals(StatusPresenter.batteryRequirement(atBase(battery = 29)), requirement)
        assertEquals(Tone.ERROR, requirement.tone)
    }

    @Test
    fun `sin robot la bateria no tiene color`() {
        assertNull(data(viewModel()).batteryRequirement.tone)
    }

    @Test
    fun `expone la alerta sin reconocer mas vieja`() {
        repository.alertsFlow.value = listOf(
            Fixtures.alert(id = "NUEVA", timestamp = 20),
            Fixtures.alert(id = "VIEJA", timestamp = 10),
            Fixtures.alert(id = "RECONOCIDA", timestamp = 1, acknowledged = true)
        )

        assertEquals("VIEJA", data(viewModel()).oldestUnacknowledgedAlert?.id)
    }

    @Test
    fun `sin alertas no hay alerta pendiente`() {
        assertNull(data(viewModel()).oldestUnacknowledgedAlert)
    }

    @Test
    fun `expone el registro de ordenes y la disponibilidad`() {
        repository.statusFlow.value = atBase()
        repository.commandsFlow.value = listOf(Fixtures.commandRecord(id = "CMD-1"))

        val data = data(viewModel())

        assertEquals(listOf("CMD-1"), data.commands.map { it.id })
        assertEquals(true, data.availability.getValue(CommandType.INICIAR_MISION).enabled)
    }

    @Test
    fun `un error al leer el estado se muestra como error`() {
        repository.alertsSource = flow { throw IllegalStateException("boom") }

        val vm = viewModel()

        assertEquals(UiState.Error("No se pudo leer el estado del robot: boom"), vm.uiState.value)
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
            listOf(CommandType.REANUDAR, CommandType.CANCELAR, CommandType.VOLVER_A_BASE, CommandType.RECONOCER_ALERTA),
            repository.sentCommands.map { it.type }
        )
    }

    @Test
    fun `pausar y detener avisan del resultado`() {
        repository.statusFlow.value = Fixtures.status(
            Fixtures.telemetry(OperationalState.EN_MISION, mission = Fixtures.mission())
        )
        repository.sendResult = Resource.Success(Fixtures.commandRecord(id = "CMD-2"))
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        vm.pause()
        vm.operationalStop()

        assertEquals(
            listOf(
                ControlEffect.ShowSnackbar("Pausar: enviada (CMD-2), esperando confirmación…"),
                ControlEffect.ShowSnackbar("Detención operativa: enviada (CMD-2), esperando confirmación…")
            ),
            effects.items
        )
        effects.stop()
    }
}
