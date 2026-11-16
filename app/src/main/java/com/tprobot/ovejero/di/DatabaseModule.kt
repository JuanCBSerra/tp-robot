package com.tprobot.ovejero.di

import android.content.Context
import androidx.room.Room
import com.tprobot.ovejero.data.local.OvejeroDatabase
import com.tprobot.ovejero.data.local.dao.EventDao
import com.tprobot.ovejero.data.local.dao.MissionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OvejeroDatabase {
        return Room.databaseBuilder(context, OvejeroDatabase::class.java, OvejeroDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideEventDao(database: OvejeroDatabase): EventDao = database.eventDao()

    @Provides
    fun provideMissionDao(database: OvejeroDatabase): MissionDao = database.missionDao()
}
