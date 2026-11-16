package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.core.model.Resource
import com.tprobot.ovejero.domain.model.CommandRecord
import com.tprobot.ovejero.domain.model.CommandType
import com.tprobot.ovejero.domain.model.MissionType
import com.tprobot.ovejero.domain.model.RobotCommand
import com.tprobot.ovejero.domain.repository.RobotRepository
import javax.inject.Inject

/** Iniciar misión. Valida las condiciones de habilitación antes de mandar la orden (ver CommandUseCaseSupport). */
class StartMissionUseCase @Inject constructor(
    private val repository: RobotRepository,
    private val availability: GetCommandAvailabilityUseCase
) {
    suspend operator fun invoke(type: MissionType): Resource<CommandRecord> =
        sendIfAvailable(repository, availability, RobotCommand(CommandType.INICIAR_MISION, missionType = type))
}
