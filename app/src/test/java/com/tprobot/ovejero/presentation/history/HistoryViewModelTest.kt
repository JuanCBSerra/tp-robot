package com.tprobot.ovejero.presentation.history

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.domain.usecase.ClearHistoryUseCase
import com.tprobot.ovejero.domain.usecase.ObserveEventHistoryUseCase
import com.tprobot.ovejero.domain.usecase.ObserveMissionHistoryUseCase
import com.tprobot.ovejero.testutil.EffectCollector
import com.tprobot.ovejero.testutil.FakeRobotRepository
import com.tprobot.ovejero.testutil.Fixtures
import com.tprobot.ovejero.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HistoryViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeRobotRepository()

    private fun viewModel() = HistoryViewModel(
        observeMissionHistoryUseCase = ObserveMissionHistoryUseCase(repository),
        observeEventHistoryUseCase = ObserveEventHistoryUseCase(repository),
        clearHistoryUseCase = ClearHistoryUseCase(repository)
    )

    @Test
    fun `carga misiones y eventos`() {
        val missions = listOf(Fixtures.missionRecord())
        val events = listOf(Fixtures.historyEvent())
        repository.missionsFlow.value = missions
        repository.eventsFlow.value = events

        val vm = viewModel()

        assertEquals(UiState.Success(HistoryData(missions, events)), vm.uiState.value)
    }

    @Test
    fun `se actualiza cuando llegan eventos nuevos`() {
        val vm = viewModel()
        val events = listOf(Fixtures.historyEvent(id = 5))

        repository.eventsFlow.value = events

        assertEquals(UiState.Success(HistoryData(emptyList(), events)), vm.uiState.value)
    }

    @Test
    fun `un error al leer el historial se muestra como error`() {
        repository.eventsSource = flow { throw IllegalStateException("boom") }

        val vm = viewModel()

        assertEquals(UiState.Error("No se pudo leer el historial: boom"), vm.uiState.value)
    }

    @Test
    fun `borrar el historial avisa`() {
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        vm.clearHistory()

        assertEquals(1, repository.clearHistoryCalls)
        assertEquals(listOf(HistoryEffect.ShowSnackbar("Historial borrado")), effects.items)
        effects.stop()
    }

    @Test
    fun `si falla el borrado muestra el error`() {
        repository.clearHistoryResult = Resource.Error("disco lleno")
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        vm.clearHistory()

        assertEquals(listOf(HistoryEffect.ShowSnackbar("disco lleno")), effects.items)
        effects.stop()
    }
}
