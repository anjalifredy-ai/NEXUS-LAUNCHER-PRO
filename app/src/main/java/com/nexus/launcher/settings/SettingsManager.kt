package com.nexus.launcher.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "nexus_settings")

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val currentProfileId: Flow<String> = context.dataStore.data.map {
        it[KEY_CURRENT_PROFILE] ?: "default"
    }

    suspend fun setCurrentProfile(id: String) {
        context.dataStore.edit { it[KEY_CURRENT_PROFILE] = id }
    }

    val javaRuntimePath: Flow<String> = context.dataStore.data.map {
        it[KEY_JAVA_RUNTIME] ?: "auto"
    }

    val jvmArguments: Flow<String> = context.dataStore.data.map {
        it[KEY_JVM_ARGS] ?: "-Xmx2G -Djava.awt.headless=true"
    }

    suspend fun setJvmArgs(args: String) {
        context.dataStore.edit { it[KEY_JVM_ARGS] = args }
    }

    val renderer: Flow<String> = context.dataStore.data.map {
        it[KEY_RENDERER] ?: "gl4es"
    }

    suspend fun setRenderer(renderer: String) {
        context.dataStore.edit { it[KEY_RENDERER] = renderer }
    }

    val memoryMb: Flow<Int> = context.dataStore.data.map {
        it[KEY_MEMORY] ?: 2048
    }

    suspend fun setMemory(memoryMb: Int) {
        context.dataStore.edit { it[KEY_MEMORY] = memoryMb }
    }

    val windowWidth: Flow<Int> = context.dataStore.data.map { it[KEY_WINDOW_WIDTH] ?: 1280 }
    val windowHeight: Flow<Int> = context.dataStore.data.map { it[KEY_WINDOW_HEIGHT] ?: 720 }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_DARK_THEME] ?: true
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.dataStore.edit { it[KEY_DARK_THEME] = dark }
    }

    val userName: Flow<String> = context.dataStore.data.map {
        it[KEY_USERNAME] ?: "Player"
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[KEY_USERNAME] = name }
    }

    val controlMapId: Flow<String> = context.dataStore.data.map {
        it[KEY_CONTROL_MAP] ?: "default"
    }

    suspend fun setControlMap(id: String) {
        context.dataStore.edit { it[KEY_CONTROL_MAP] = id }
    }

    val sharedGameData: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_SHARED_DATA] ?: true
    }

    companion object {
        val KEY_CURRENT_PROFILE = stringPreferencesKey("current_profile")
        val KEY_JAVA_RUNTIME = stringPreferencesKey("java_runtime")
        val KEY_JVM_ARGS = stringPreferencesKey("jvm_args")
        val KEY_RENDERER = stringPreferencesKey("renderer")
        val KEY_MEMORY = intPreferencesKey("memory_mb")
        val KEY_WINDOW_WIDTH = intPreferencesKey("window_width")
        val KEY_WINDOW_HEIGHT = intPreferencesKey("window_height")
        val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_CONTROL_MAP = stringPreferencesKey("control_map")
        val KEY_SHARED_DATA = booleanPreferencesKey("shared_data")
    }
}
