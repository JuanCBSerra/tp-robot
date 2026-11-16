package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.model.Robot
import com.tprobot.ovejero.domain.repository.RobotRepository
import javax.inject.Inject

class ConnectRobotUseCase @Inject constructor(
    private val repository: RobotRepository
) {
    suspend operator fun invoke(robot: Robot): Resource<Robot> {
        if (!robot.isSelectable) {
            return Resource.Error("${robot.name} no está disponible: ${robot.availability.label.lowercase()}")
        }
        return repository.connect(robot.id)
    }
}
