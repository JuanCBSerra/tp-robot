package com.tprobot.ovejero.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tprobot.ovejero.data.local.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<EventEntity>>

    @Insert
    suspend fun insert(event: EventEntity)

    @Query("DELETE FROM events")
    suspend fun clear()
}
