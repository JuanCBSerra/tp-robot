package com.tprobot.ovejero.di

import com.tprobot.ovejero.data.remote.RobotDataSource
import com.tprobot.ovejero.data.simulator.RobotSimulator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Acá se decide con qué robot habla la app.
 * Para un Go2 real alcanzaría con cambiar RobotSimulator por otra implementación de RobotDataSource.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    @Binds
    @Singleton
    abstract fun bindRobotDataSource(
        robotSimulator: RobotSimulator
    ): RobotDataSource
}
