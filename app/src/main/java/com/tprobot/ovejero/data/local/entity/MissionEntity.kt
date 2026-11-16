package com.tprobot.ovejero.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Resumen de una misión terminada (completada, cancelada o abortada). */
@Entity(tableName = "missions")
data class MissionEntity(
    @PrimaryKey val missionId: String,
    val robotName: String,
    val type: String,
    val result: String,
    val startedAt: Long,
    val endedAt: Long,
    val headsCounted: Int,
    val headsExpected: Int,
    val headsInCorral: Int,
    val batteryUsedPercent: Int
)
