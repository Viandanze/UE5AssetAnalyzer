package com.example.ue5analyzer.data.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ue5analyzer.model.AssetType
import kotlinx.coroutines.flow.Flow

/**
 * Asset Entity
 */
@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    val type: String,
    val size: Long,
    val dependencies: String, // JSON string
    val references: String,   // JSON string
    val isOrphan: Boolean,
    val orphanRiskLevel: String,
    val lastModified: Long,
    val projectId: String
)

/**
 * Project Entity
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    val totalAssets: Int,
    val totalSize: Long,
    val lastScanned: Long
)

/**
 * Asset DAO
 */
@Dao
interface AssetDao {
    @Query("SELECT * FROM assets WHERE projectId = :projectId ORDER BY name")
    fun getAssetsByProject(projectId: String): Flow<List<AssetEntity>>
    
    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getAssetById(id: String): AssetEntity?
    
    @Query("SELECT * FROM assets WHERE projectId = :projectId AND isOrphan = 1")
    suspend fun getOrphanAssets(projectId: String): List<AssetEntity>
    
    @Query("SELECT type, COUNT(*) as count FROM assets WHERE projectId = :projectId GROUP BY type")
    suspend fun getAssetTypeStats(projectId: String): List<AssetTypeCount>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssets(assets: List<AssetEntity>)
    
    @Query("DELETE FROM assets WHERE projectId = :projectId")
    suspend fun deleteAssetsByProject(projectId: String)
}

/**
 * Project DAO
 */
@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastScanned DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>
    
    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?
    
    // Optimization: Direct query by name, avoid loading all projects then filtering
    @Query("SELECT * FROM projects WHERE name = :name LIMIT 1")
    suspend fun getProjectByName(name: String): ProjectEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)
    
    @Delete
    suspend fun deleteProject(project: ProjectEntity)
    
    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)
}

/**
 * Asset Type Statistics
 */
data class AssetTypeCount(
    val type: String,
    val count: Int
)

/**
 * Database
 */
@Database(entities = [AssetEntity::class, ProjectEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun projectDao(): ProjectDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        // Database migration strategy placeholder
        // Version 2 reserved: Current table structures remain unchanged, no actual migration needed
        // Add migration logic here if fields need to be added or table structures modified in the future
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Both table structures are the same as version 1, no migration needed
                // Example if adding new field:
                // db.execSQL("ALTER TABLE assets ADD COLUMN new_field TEXT")
            }
        }
        
        fun getDatabase(context: android.content.Context): AppDatabase {
            // Double-Checked Locking Pattern
            // Since context parameter cannot use by lazy, DCL pattern is used here
            // INSTANCE uses @Volatile to guarantee visibility
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ue5_analyzer_db"
                )
                    .addMigrations(MIGRATION_1_2)  // Add Migration Strategy
                    .fallbackToDestructiveMigration()  // Fallback: Rebuild database when no migration exists
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
