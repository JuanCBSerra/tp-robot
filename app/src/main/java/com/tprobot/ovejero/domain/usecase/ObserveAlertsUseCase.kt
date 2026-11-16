package com.tprobot.ovejero.domain.usecase

import com.tprobot.ovejero.domain.model.Alert
import com.tprobot.ovejero.domain.model.AlertSeverity
import com.tprobot.ovejero.domain.repository.RobotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Alertas ordenadas: primero las no reconocidas, después por gravedad y por hora. */
class ObserveAlertsUseCase @Inject constructor(
    private val repository: RobotRepository
) {
    operator fun invoke(): Flow<List<Alert>> = repository.observeAlerts().map { alerts ->
        alerts.sortedWith(
            compareBy<Alert> { it.acknowledged }
                .thenByDescending { it.severity == AlertSeverity.CRITICA }
                .thenByDescending { it.timestampMillis }
        )
    }
}
