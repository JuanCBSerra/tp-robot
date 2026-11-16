package com.tprobot.ovejero.di

import com.tprobot.ovejero.data.repository.RobotRepositoryImpl
import com.tprobot.ovejero.data.repository.SimulationRepositoryImpl
import com.tprobot.ovejero.domain.repository.RobotRepository
import com.tprobot.ovejero.domain.repository.SimulationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRobotRepository(
        robotRepositoryImpl: RobotRepositoryImpl
    ): RobotRepository

    @Binds
    @Singleton
    abstract fun bindSimulationRepository(
        simulationRepositoryImpl: SimulationRepositoryImpl
    ): SimulationRepository
}
