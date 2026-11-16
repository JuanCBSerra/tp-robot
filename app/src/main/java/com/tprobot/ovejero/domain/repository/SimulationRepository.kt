package com.tprobot.ovejero.domain.repository

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.model.SimulationAction
import com.tprobot.ovejero.domain.model.SimulationSettings
import kotlinx.coroutines.flow.Flow

/**
 * Contrato para manejar el simulador desde la UI (panel de simulación).
 * La UI nunca toca el simulador directamente: pasa por este contrato.
 */
interface SimulationRepository {
    fun observeSettings(): Flow<SimulationSettings>
    suspend fun apply(action: SimulationAction): Resource<Unit>
}
