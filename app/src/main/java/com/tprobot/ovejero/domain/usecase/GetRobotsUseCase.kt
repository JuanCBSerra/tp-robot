package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.model.Robot
import com.tprobot.ovejero.domain.repository.RobotRepository
import javax.inject.Inject

/** Lista la flota: los disponibles primero. */
class GetRobotsUseCase @Inject constructor(
    private val repository: RobotRepository
) {
    suspend operator fun invoke(): Resource<List<Robot>> =
        when (val result = repository.getRobots()) {
            is Resource.Success -> Resource.Success(result.data.sortedBy { it.availability.ordinal })
            is Resource.Error -> result
        }
}
