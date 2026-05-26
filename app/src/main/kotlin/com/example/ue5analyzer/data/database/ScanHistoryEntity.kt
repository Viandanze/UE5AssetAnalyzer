package com.example.ue5analyzer.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Scan History Entity - stores snapshots of scan results for comparison
 */
@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey val id: String,
    val projectName: String,
    val projectPath: String,
    val scanTime: Long,
    val totalAssets: Int,
    val totalSize: Long,
    val orphanCount: Int,
    val healthScore: Int,
    val typeDistribution: String,  // JSON: {"STATIC_MESH":12,"MATERIAL":5,...}
    val largestAssets: String,     // JSON: [{"name":"SM_X","size":1234},...]
    val assetSnapshot: String      // JSON: [{"name":"SM_X","type":"STATIC_MESH","size":1234,"isOrphan":false},...]
)

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history WHERE projectName = :projectName ORDER BY scanTime DESC")
    fun getHistoryByProject(projectName: String): kotlinx.coroutines.flow.Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE projectName = :projectName ORDER BY scanTime DESC")
    suspend fun getHistoryListByProject(projectName: String): List<ScanHistoryEntity>

    @Query("SELECT * FROM scan_history WHERE id = :id")
    suspend fun getById(id: String): ScanHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ScanHistoryEntity)

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM scan_history WHERE projectName = :projectName")
    suspend fun deleteByProject(projectName: String)
}
