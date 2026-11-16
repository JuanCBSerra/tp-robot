package com.tprobot.ovejero.core.coroutines

import javax.inject.Qualifier

/**
 * Identifica el CoroutineScope que vive mientras vive la app (lo provee Hilt en AppModule).
 *
 * Lo usan el simulador del robot y el repositorio, que tienen que seguir escuchando
 * telemetría y guardando historial aunque cambie la pantalla. Los ViewModels NO lo usan:
 * ellos siguen usando solo viewModelScope.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
