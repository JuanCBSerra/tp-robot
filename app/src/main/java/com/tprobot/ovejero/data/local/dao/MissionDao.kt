package com.tprobot.ovejero.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tprobot.ovejero.data.local.entity.MissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionDao {
    @Query("SELECT * FROM missions ORDER BY endedAt DESC")
    fun observeAll(): Flow<List<MissionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mission: MissionEntity)

    @Query("DELETE FROM missions")
    suspend fun clear()
}
