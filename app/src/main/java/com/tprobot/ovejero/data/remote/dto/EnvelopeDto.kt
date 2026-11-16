package com.tprobot.ovejero.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Lectura mínima de cualquier mensaje: solo el campo "type",
 * para saber con qué DTO parsear el resto.
 */
@JsonClass(generateAdapter = true)
data class EnvelopeDto(
    @Json(name = "type") val type: String?
)
