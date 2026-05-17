package com.example.ue5analyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ue5analyzer.data.manager.ThemeMode
import com.example.ue5analyzer.data.manager.ThemePreferencesManager
import com.example.ue5analyzer.ui.MainScreen
import com.example.ue5analyzer.ui.theme.UE5AnalyzerTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var themePreferencesManager: ThemePreferencesManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize theme preferences manager
        themePreferencesManager = ThemePreferencesManager(this)
        
        enableEdgeToEdge()
        setContent {
            // Collect Theme Preferences
            val themeMode by themePreferencesManager.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            
            // Calculate actual dark theme state
            val systemDarkTheme = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDarkTheme
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> systemDarkTheme
            }
            
            UE5AnalyzerTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        themePreferencesManager = themePreferencesManager
                    )
                }
            }
        }
    }
    
    /**
     * Get current theme mode
     */
    fun getThemeMode(): ThemeMode {
        return ThemeMode.SYSTEM
    }
}
