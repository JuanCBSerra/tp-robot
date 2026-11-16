package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.model.SimulationAction
import com.tprobot.ovejero.domain.repository.SimulationRepository
import javax.inject.Inject

class ApplySimulationActionUseCase @Inject constructor(
    private val repository: SimulationRepository
) {
    suspend operator fun invoke(action: SimulationAction): Resource<Unit> = repository.apply(action)
}
