package com.tprobot.ovejero.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tprobot.ovejero.data.local.dao.EventDao
import com.tprobot.ovejero.data.local.dao.MissionDao
import com.tprobot.ovejero.data.local.entity.EventEntity
import com.tprobot.ovejero.data.local.entity.MissionEntity

/**
 * Base local con el historial. Elegimos Room (lo sugiere la consigna) porque:
 * - devuelve Flow, así la pantalla de historial se actualiza sola;
 * - las consultas se validan en compilación;
 * - funciona offline.
 */
@Database(
    entities = [EventEntity::class, MissionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OvejeroDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun missionDao(): MissionDao

    companion object {
        const val NAME = "ovejero.db"
    }
}
