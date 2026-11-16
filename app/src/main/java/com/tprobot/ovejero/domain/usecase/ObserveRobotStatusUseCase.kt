package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.domain.model.RobotStatus
import com.tprobot.ovejero.domain.repository.RobotRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRobotStatusUseCase @Inject constructor(
    private val repository: RobotRepository
) {
    operator fun invoke(): Flow<RobotStatus> = repository.observeRobotStatus()
}
