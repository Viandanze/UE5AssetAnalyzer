package com.example.ue5analyzer.data.manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

/**
 * Theme Mode
 */
enum class ThemeMode {
    SYSTEM,  // Follow System
    LIGHT,   // Light
    DARK     // Dark
}

/**
 * Theme Preferences Manager
 * Responsible for theme preference persistence and reading
 */
class ThemePreferencesManager(private val context: Context) {
    
    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
    }
    
    /**
     * Get Theme Mode Flow
     */
    val themeModeFlow: Flow<ThemeMode> = context.themeDataStore.data.map { preferences ->
        val modeName = preferences[THEME_MODE] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeName)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }
    
    /**
     * Save Theme Mode
     */
    suspend fun saveThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.name
        }
    }
}
