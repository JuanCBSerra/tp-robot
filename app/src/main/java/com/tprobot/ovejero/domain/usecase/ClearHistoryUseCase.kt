package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.repository.RobotRepository
import javax.inject.Inject

class ClearHistoryUseCase @Inject constructor(
    private val repository: RobotRepository
) {
    suspend operator fun invoke(): Resource<Unit> = repository.clearHistory()
}
