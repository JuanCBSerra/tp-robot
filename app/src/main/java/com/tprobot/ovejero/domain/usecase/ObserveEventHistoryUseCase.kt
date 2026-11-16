package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.domain.model.HistoryEvent
import com.tprobot.ovejero.domain.repository.RobotRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveEventHistoryUseCase @Inject constructor(
    private val repository: RobotRepository
) {
    operator fun invoke(): Flow<List<HistoryEvent>> = repository.observeEventHistory()
}
