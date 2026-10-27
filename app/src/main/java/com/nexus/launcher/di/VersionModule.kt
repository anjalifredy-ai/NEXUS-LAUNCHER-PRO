package com.nexus.launcher.di

import com.nexus.launcher.version.VersionInheritanceResolver
import com.nexus.launcher.version.VersionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VersionModule {

    @Provides
    @Singleton
    fun provideInheritanceWiring(
        versionManager: VersionManager,
        resolver: VersionInheritanceResolver
    ): Boolean {
        versionManager.attachInheritanceResolver(resolver)
        return true
    }
}
