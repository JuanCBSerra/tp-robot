package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.model.CommandRecord
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.RobotCommand
import com.tprobot.ovejero.domain.repository.RobotRepository
import javax.inject.Inject

class ReturnToBaseUseCase @Inject constructor(
    private val repository: RobotRepository,
    private val availability: GetCommandAvailabilityUseCase
) {
    suspend operator fun invoke(): Resource<CommandRecord> =
        sendIfAvailable(repository, availability, RobotCommand(CommandType.VOLVER_A_BASE))
}
