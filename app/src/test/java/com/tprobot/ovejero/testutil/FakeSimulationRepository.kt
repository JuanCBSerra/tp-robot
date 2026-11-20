package com.tprobot.ovejero.testutil

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.model.SimulationAction
import com.tprobot.ovejero.domain.model.SimulationSettings
import com.tprobot.ovejero.domain.repository.SimulationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSimulationRepository : SimulationRepository {
    val settingsFlow = MutableStateFlow(Fixtures.settings())
    var settingsSource: Flow<SimulationSettings> = settingsFlow
    var applyResult: Resource<Unit> = Resource.Success(Unit)
    val applied = mutableListOf<SimulationAction>()

    override fun observeSettings(): Flow<SimulationSettings> = settingsSource

    override suspend fun apply(action: SimulationAction): Resource<Unit> {
        applied += action
        return applyResult
    }
}
