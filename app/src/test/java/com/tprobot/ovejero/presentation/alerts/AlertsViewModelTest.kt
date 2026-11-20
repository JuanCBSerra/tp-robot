package com.tprobot.ovejero.presentation.alerts

import com.tprobot.ovejero.core.model.UiState
import com.tprobot.ovejero.domain.model.CommandStatus
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.ConnectionState
import com.tprobot.ovejero.domain.model.OperationalState
import com.tprobot.ovejero.domain.usecase.AcknowledgeAlertUseCase
import com.tprobot.ovejero.domain.usecase.ObserveAlertsUseCase
import com.tprobot.ovejero.domain.usecase.ObserveCommandsUseCase
import com.tprobot.ovejero.domain.usecase.ObserveRobotStatusUseCase
import com.tprobot.ovejero.testutil.EffectCollector
import com.tprobot.ovejero.testutil.FakeRobotRepository
import com.tprobot.ovejero.testutil.Fixtures
import com.tprobot.ovejero.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlertsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeRobotRepository()

    private fun viewModel() = AlertsViewModel(
        observeAlertsUseCase = ObserveAlertsUseCase(repository),
        observeCommandsUseCase = ObserveCommandsUseCase(repository),
        observeRobotStatusUseCase = ObserveRobotStatusUseCase(repository),
        acknowledgeAlertUseCase = AcknowledgeAlertUseCase(repository)
    )

    private fun data(vm: AlertsViewModel) = (vm.uiState.value as UiState.Success).data

    @Test
    fun `separa alertas activas y reconocidas`() {
        repository.alertsFlow.value = listOf(
            Fixtures.alert(id = "A"),
            Fixtures.alert(id = "B", acknowledged = true)
        )

        val data = data(viewModel())

        assertEquals(listOf("A"), data.active.map { it.id })
        assertEquals(listOf("B"), data.acknowledged.map { it.id })
    }

    @Test
    fun `marca los reconocimientos que todavia esperan al robot`() {
        repository.commandsFlow.value = listOf(
            Fixtures.commandRecord(id = "1", type = CommandType.RECONOCER_ALERTA, alertId = "A"),
            Fixtures.commandRecord(
                id = "2", type = CommandType.RECONOCER_ALERTA, alertId = "B", status = CommandStatus.CONFIRMADA
            ),
            Fixtures.commandRecord(id = "3", type = CommandType.PAUSAR)
        )

        assertEquals(setOf("A"), data(viewModel()).pendingAckIds)
    }

    @Test
    fun `solo se puede reconocer con conexion`() {
        val vm = viewModel()
        assertFalse(data(vm).canAcknowledge)

        repository.statusFlow.value = Fixtures.status(connection = ConnectionState.CONECTADO)
        assertTrue(data(vm).canAcknowledge)

        repository.statusFlow.value = Fixtures.status(connection = ConnectionState.SIN_SENIAL)
        assertFalse(data(vm).canAcknowledge)
    }

    @Test
    fun `un error al leer las alertas se muestra como error`() {
        repository.alertsSource = flow { throw IllegalStateException("boom") }

        val vm = viewModel()

        assertEquals(UiState.Error("No se pudieron leer las alertas: boom"), vm.uiState.value)
    }

    @Test
    fun `reconocer una alerta avisa que espera confirmacion`() {
        repository.statusFlow.value = Fixtures.status(Fixtures.telemetry(OperationalState.EN_MISION))
        repository.alertsFlow.value = listOf(Fixtures.alert(id = "AL-1"))
        val vm = viewModel()
        val effects = EffectCollector(vm.effectFlow)

        vm.acknowledge("AL-1")

        assertEquals(
            listOf(AlertsEffect.ShowSnackbar("Reconocimiento de AL-1 enviado, esperando confirmación…")),
            effects.items
        )
        assertEquals("AL-1", repository.sentCommands.single().alertId)
        effects.stop()
    }

    @Test
    fun `si no se puede reconocer muestra el motivo`() {
        val vm = viewModel() // sin conexión
        val effects = EffectCollector(vm.effectFlow)

        vm.acknowledge("AL-1")

        val message = (effects.items.single() as AlertsEffect.ShowSnackbar).message
        assertTrue(message.startsWith("Sin conexión"))
        effects.stop()
    }
}
