package com.b3n00n.snorlax.di

import android.content.Context
import com.b3n00n.snorlax.config.ServerConfigurationManager
import com.b3n00n.snorlax.models.DeviceInfo
import com.b3n00n.snorlax.network.NetworkClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideServerConfigurationManager(
        @ApplicationContext context: Context
    ): ServerConfigurationManager {
        return ServerConfigurationManager(context)
    }

    @Provides
    @Singleton
    fun provideNetworkClient(
        configManager: ServerConfigurationManager
    ): NetworkClient {
        val serverIp = configManager.getServerIp()
        val serverPort = configManager.getServerPort()
        return NetworkClient(serverIp, serverPort)
    }

    @Provides
    @Singleton
    fun provideDeviceInfo(
        @ApplicationContext context: Context
    ): DeviceInfo {
        return DeviceInfo(context)
    }
}
