package com.tprobot.ovejero.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Fila del historial de eventos (órdenes, alertas, conexión, misiones). */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val category: String,
    val title: String,
    val detail: String
)
