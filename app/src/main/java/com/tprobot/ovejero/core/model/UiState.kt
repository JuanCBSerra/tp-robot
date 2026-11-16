package com.tprobot.ovejero.core.model

/**
 * Estado genérico para representar los diferentes momentos de la UI
 * en la arquitectura MVVM:
 * - Idle: Estado inicial
 * - Loading: Mientras se espera el primer dato
 * - Success: Con los datos ya obtenidos
 * - Error: Si ocurrió una falla (con mensaje amigable)
 */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
