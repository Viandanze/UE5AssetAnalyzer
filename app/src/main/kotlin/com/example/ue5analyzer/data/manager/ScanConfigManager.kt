package com.example.ue5analyzer.data.manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.ue5analyzer.model.ScanConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scan_config")

/**
 * ScanConfig Manager
 * Responsible for ScanConfig persistence and reading
 */
class ScanConfigManager(private val context: Context) {
    
    companion object {
        private val IGNORED_DIRECTORIES = stringSetPreferencesKey("ignored_directories")
        private val IGNORED_EXTENSIONS = stringSetPreferencesKey("ignored_extensions")
        private val MAX_FILE_SIZE = longPreferencesKey("max_file_size")
        private val ENABLED = booleanPreferencesKey("enabled")
        
        private val DEFAULT_DIRECTORIES = setOf(
            "Intermediate",
            "Saved",
            "DerivedDataCache",
            ".git",
            "Build"
        )
    }
    
    /**
     * Get ScanConfig Flow
     */
    val scanConfigFlow: Flow<ScanConfig> = context.dataStore.data.map { preferences ->
        ScanConfig(
            ignoredDirectories = preferences[IGNORED_DIRECTORIES] ?: DEFAULT_DIRECTORIES,
            ignoredExtensions = preferences[IGNORED_EXTENSIONS] ?: emptySet(),
            maxFileSize = preferences[MAX_FILE_SIZE] ?: 0L,
            enabled = preferences[ENABLED] ?: true
        )
    }
    
    /**
     * Save ScanConfig
     */
    suspend fun saveScanConfig(config: ScanConfig) {
        context.dataStore.edit { preferences ->
            preferences[IGNORED_DIRECTORIES] = config.ignoredDirectories
            preferences[IGNORED_EXTENSIONS] = config.ignoredExtensions
            preferences[MAX_FILE_SIZE] = config.maxFileSize
            preferences[ENABLED] = config.enabled
        }
    }
    
    /**
     * Update Ignored Directories
     */
    suspend fun updateIgnoredDirectories(directories: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[IGNORED_DIRECTORIES] = directories
        }
    }
    
    /**
     * Update Ignored Extensions
     */
    suspend fun updateIgnoredExtensions(extensions: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[IGNORED_EXTENSIONS] = extensions
        }
    }
}
