package com.tprobot.ovejero.testutil

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Junta los efectos de un SharedFlow (snackbars, navegación). Como un SharedFlow sin replay
 * pierde lo que se emite sin suscriptores, hay que crearlo ANTES de disparar la acción.
 */
class EffectCollector<T>(flow: Flow<T>) {
    val items = mutableListOf<T>()
    private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    private val job: Job = scope.launch { flow.collect { items += it } }

    fun stop() = job.cancel()
}
