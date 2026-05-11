package com.example.ue5analyzer.data.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ue5analyzer.model.AssetType
import kotlinx.coroutines.flow.Flow

/**
 * 资源实体
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
 * 项目实体
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
 * 资源 DAO
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
 * 项目 DAO
 */
@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastScanned DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>
    
    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): ProjectEntity?
    
    // 优化：直接通过名称查询，避免加载所有项目后过滤
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
 * 资源类型统计
 */
data class AssetTypeCount(
    val type: String,
    val count: Int
)

/**
 * 数据库
 */
@Database(entities = [AssetEntity::class, ProjectEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun projectDao(): ProjectDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        // 数据库迁移策略占位
        // 版本2预留：当前两个表结构保持不变，无需实际迁移
        // 如后续需要添加字段或修改表结构，在此添加迁移逻辑
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 两个表结构与版本1相同，无需迁移
                // 如需添加新字段，示例：
                // db.execSQL("ALTER TABLE assets ADD COLUMN new_field TEXT")
            }
        }
        
        fun getDatabase(context: android.content.Context): AppDatabase {
            // 双重检查锁定模式（Double-Checked Locking）
            // 由于 context 参数无法使用 by lazy，此处采用 DCL 模式
            // INSTANCE 使用 @Volatile 保证可见性
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ue5_analyzer_db"
                )
                    .addMigrations(MIGRATION_1_2)  // 添加迁移策略
                    .fallbackToDestructiveMigration()  // 兜底：没有迁移时重建数据库
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
