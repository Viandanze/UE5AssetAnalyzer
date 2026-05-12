package com.example.ue5analyzer.data.manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

/**
 * 主题模式
 */
enum class ThemeMode {
    SYSTEM,  // 跟随系统
    LIGHT,   // 浅色
    DARK     // 深色
}

/**
 * 主题偏好管理器
 * 负责主题偏好的持久化和读取
 */
class ThemePreferencesManager(private val context: Context) {
    
    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
    }
    
    /**
     * 获取主题模式 Flow
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
     * 保存主题模式
     */
    suspend fun saveThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.name
        }
    }
}
