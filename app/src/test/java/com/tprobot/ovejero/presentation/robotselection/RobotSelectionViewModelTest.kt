package com.tprobot.ovejero.presentation.robotselection

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.domain.model.RobotAvailability
import com.tprobot.ovejero.domain.usecase.ConnectRobotUseCase
import com.tprobot.ovejero.domain.usecase.GetRobotsUseCase
import com.tprobot.ovejero.testutil.EffectCollector
import com.tprobot.ovejero.testutil.FakeRobotRepository
import com.tprobot.ovejero.testutil.Fixtures
import com.tprobot.ovejero.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RobotSelectionViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeRobotRepository()

    private fun viewModel() = RobotSelectionViewModel(
        getRobotsUseCase = GetRobotsUseCase(repository),
        connectRobotUseCase = ConnectRobotUseCase(repository)
    )

    @Test
    fun `carga la flota con los disponibles primero`() {
        val unavailable = Fixtures.robot(id = "2", availability = RobotAvailability.EN_MANTENIMIENTO)
        val available = Fixtures.robot(id = "1")
        repository.robotsResult = Resource.Success(listOf(unavailable, available))

        val vm = viewModel()

        assertEquals(UiState.Success(listOf(available, unavailable)), vm.uiState.value)
    }

    @Test
    fun `si no se puede listar la flota muestra el error y reintenta`() {
        repository.robotsResult = Resource.Error("sin datos")
        val vm = viewModel()
        assertEquals(UiState.Error("sin datos"), vm.uiState.value)

        repository.robotsResult = Resource.Success(listOf(Fixtures.robot()))
        vm.loadRobots()

        assertEquals(UiState.Success(listOf(Fixtures.robot())), vm.uiState.value)
    }

    @Test
    fun `conectar avisa y navega al panel`() {
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        vm.connect(Fixtures.robot(id = "OVJ-01", name = "Ovejero-01"))

        assertEquals(
            listOf(
                RobotSelectionEffect.ShowSnackbar("Conectado a Ovejero-01"),
                RobotSelectionEffect.NavigateToDashboard
            ),
            effects.items
        )
        assertNull(vm.connectingRobotId.value)
        effects.stop()
    }

    @Test
    fun `si la conexion falla avisa el error y no navega`() {
        repository.connectResult = Resource.Error("sin señal")
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        vm.connect(Fixtures.robot())

        assertEquals(listOf(RobotSelectionEffect.ShowSnackbar("sin señal")), effects.items)
        assertNull(vm.connectingRobotId.value)
        effects.stop()
    }

    @Test
    fun `un robot no disponible no se conecta`() {
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        vm.connect(Fixtures.robot(name = "Ovejero-02", availability = RobotAvailability.FUERA_DE_ALCANCE))

        assertEquals(
            listOf(RobotSelectionEffect.ShowSnackbar("Ovejero-02 no está disponible: fuera de alcance")),
            effects.items
        )
        assertEquals(emptyList<String>(), repository.connectCalls)
        effects.stop()
    }

    @Test
    fun `mientras conecta ignora otro intento`() {
        val gate = CompletableDeferred<Unit>()
        repository.connectGate = gate
        val vm = viewModel()

        vm.connect(Fixtures.robot(id = "OVJ-01"))
        assertEquals("OVJ-01", vm.connectingRobotId.value)
        vm.connect(Fixtures.robot(id = "OVJ-02"))
        gate.complete(Unit)

        assertEquals(listOf("OVJ-01"), repository.connectCalls)
        assertNull(vm.connectingRobotId.value)
    }
}
