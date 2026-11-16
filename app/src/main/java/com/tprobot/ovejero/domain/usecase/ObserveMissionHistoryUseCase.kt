package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.domain.model.MissionRecord
import com.tprobot.ovejero.domain.repository.RobotRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMissionHistoryUseCase @Inject constructor(
    private val repository: RobotRepository
) {
    operator fun invoke(): Flow<List<MissionRecord>> = repository.observeMissionHistory()
}
