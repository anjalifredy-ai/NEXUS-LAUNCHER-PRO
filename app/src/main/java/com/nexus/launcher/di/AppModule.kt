package com.nexus.launcher.di

import android.content.Context
import com.google.gson.Gson
import com.nexus.launcher.filesystem.FileSystemManager
import com.nexus.launcher.filesystem.Sha1Verifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFileSystemManager(@ApplicationContext context: Context): FileSystemManager {
        return FileSystemManager(context)
    }

    @Provides
    @Singleton
    fun provideSha1Verifier(): Sha1Verifier = Sha1Verifier()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}
