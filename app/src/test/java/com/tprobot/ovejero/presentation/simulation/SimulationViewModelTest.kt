package com.tprobot.ovejero.presentation.simulation

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.domain.model.Scenario
import com.tprobot.ovejero.domain.model.SimulationAction
import com.tprobot.ovejero.domain.model.SimulationSpeed
import com.tprobot.ovejero.domain.usecase.ApplySimulationActionUseCase
import com.tprobot.ovejero.domain.usecase.ObserveSimulationSettingsUseCase
import com.tprobot.ovejero.testutil.EffectCollector
import com.tprobot.ovejero.testutil.FakeSimulationRepository
import com.tprobot.ovejero.testutil.Fixtures
import com.tprobot.ovejero.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SimulationViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeSimulationRepository()

    private fun viewModel() = SimulationViewModel(
        observeSimulationSettingsUseCase = ObserveSimulationSettingsUseCase(repository),
        applySimulationActionUseCase = ApplySimulationActionUseCase(repository)
    )

    private fun messageFor(action: SimulationAction): List<SimulationEffect> {
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)
        vm.apply(action)
        effects.stop()
        return effects.items
    }

    @Test
    fun `expone los ajustes del simulador`() {
        val vm = viewModel()
        assertEquals(UiState.Success(Fixtures.settings()), vm.uiState.value)

        repository.settingsFlow.value = Fixtures.settings(Scenario.CALOR)

        assertEquals(UiState.Success(Fixtures.settings(Scenario.CALOR)), vm.uiState.value)
    }

    @Test
    fun `un error al leer el simulador se muestra como error`() {
        repository.settingsSource = flow { throw IllegalStateException("boom") }

        val vm = viewModel()

        assertEquals(UiState.Error("No se pudo leer el simulador: boom"), vm.uiState.value)
    }

    @Test
    fun `cada accion avisa lo que hizo`() {
        val expected = mapOf(
            SimulationAction.LoadScenario(Scenario.CALOR) to
                "Escenario cargado: 6 · Ola de calor (extra). Iniciá una misión desde Control.",
            SimulationAction.SetLinkDown(true) to "Enlace cortado",
            SimulationAction.SetLinkDown(false) to "Enlace restablecido",
            SimulationAction.RejectNextCommand to "La próxima orden va a ser rechazada",
            SimulationAction.IgnoreNextCommand to "La próxima orden no va a tener respuesta",
            SimulationAction.DrainBattery to "Batería bajada al 22 %",
            SimulationAction.TriggerObstacle to "Obstáculo disparado",
            SimulationAction.TriggerStorm to "Tormenta disparada",
            SimulationAction.TriggerHeat to "Ola de calor disparada",
            SimulationAction.TriggerPersonNearby to "Persona cerca disparada"
        )
        expected.forEach { (action, message) ->
            assertEquals(listOf<SimulationEffect>(SimulationEffect.ShowSnackbar(message)), messageFor(action))
        }
    }

    @Test
    fun `cambiar la velocidad no muestra aviso pero se aplica`() {
        val effects = messageFor(SimulationAction.SetSpeed(SimulationSpeed.X10))

        assertEquals(emptyList<SimulationEffect>(), effects)
        assertEquals(SimulationAction.SetSpeed(SimulationSpeed.X10), repository.applied.single())
    }

    @Test
    fun `si la accion falla muestra el error`() {
        repository.applyResult = Resource.Error("no se pudo")

        val effects = messageFor(SimulationAction.TriggerHeat)

        assertEquals(listOf<SimulationEffect>(SimulationEffect.ShowSnackbar("no se pudo")), effects)
    }
}
