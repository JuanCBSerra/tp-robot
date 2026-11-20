package com.tprobot.ovejero.data.repository

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.data.simulator.RobotSimulator
import com.tprobot.ovejero.domain.model.SimulationAction
import com.tprobot.ovejero.domain.model.SimulationSettings
import com.tprobot.ovejero.domain.repository.SimulationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puente entre el panel de simulación (UI) y el simulador.
 * Existe para que la UI nunca toque el simulador directamente (consigna 4).
 */
@Singleton
class SimulationRepositoryImpl @Inject constructor(
    private val simulator: RobotSimulator,
    private val robotRepository: RobotRepositoryImpl
) : SimulationRepository {

    override fun observeSettings(): Flow<SimulationSettings> = simulator.settings

    override suspend fun apply(action: SimulationAction): Resource<Unit> {
        val error: String? = try {
            when (action) {
                is SimulationAction.LoadScenario -> {
                    simulator.loadScenario(action.scenario)
                    robotRepository.resetSession("Escenario cargado: ${action.scenario.label}")
                    null
                }
                is SimulationAction.SetSpeed -> {
                    simulator.setSpeed(action.speed)
                    null
                }
                is SimulationAction.SetLinkDown -> {
                    simulator.setLinkDown(action.down)
                    null
                }
                SimulationAction.RejectNextCommand -> {
                    simulator.rejectNextCommand()
                    null
                }
                SimulationAction.IgnoreNextCommand -> {
                    simulator.ignoreNextCommand()
                    null
                }
                SimulationAction.DrainBattery -> simulator.drainBattery()
                SimulationAction.TriggerObstacle -> simulator.triggerObstacle()
                SimulationAction.TriggerStorm -> simulator.triggerStorm()
                SimulationAction.TriggerPersonNearby -> simulator.triggerPersonNearby()
            }
        } catch (e: Exception) {
            "Error en el simulador: ${e.localizedMessage}"
        }
        return if (error == null) Resource.Success(Unit) else Resource.Error(error)
    }
}
