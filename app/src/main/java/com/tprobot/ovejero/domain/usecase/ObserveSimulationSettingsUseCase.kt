package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.domain.model.SimulationSettings
import com.tprobot.ovejero.domain.repository.SimulationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSimulationSettingsUseCase @Inject constructor(
    private val repository: SimulationRepository
) {
    operator fun invoke(): Flow<SimulationSettings> = repository.observeSettings()
}
