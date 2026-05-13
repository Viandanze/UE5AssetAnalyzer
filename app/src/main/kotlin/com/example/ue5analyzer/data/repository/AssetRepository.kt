package com.example.ue5analyzer.data.repository

import com.example.ue5analyzer.data.database.AssetEntity
import com.example.ue5analyzer.data.database.AssetTypeCount
import com.example.ue5analyzer.data.database.AppDatabase
import com.example.ue5analyzer.data.network.RetrofitClient
import kotlinx.coroutines.flow.Flow

/**
 * Asset Repository
 * Single source of truth for asset data
 * Implements read-from-DB-then-network-sync pattern
 */
class AssetRepository(private val database: AppDatabase) {
    
    private val assetDao = database.assetDao()
    private val api = RetrofitClient.api
    
    /**
     * Get all assets for a project as Flow
     * Reads from local database (single source of truth)
     */
    fun getAssetsByProject(projectId: String): Flow<List<AssetEntity>> {
        return assetDao.getAssetsByProject(projectId)
    }
    
    /**
     * Get asset by ID
     * Reads from local database
     */
    suspend fun getAssetById(id: String): AssetEntity? {
        return assetDao.getAssetById(id)
    }
    
    /**
     * Get orphan assets for a project
     * Reads from local database
     */
    suspend fun getOrphanAssets(projectId: String): List<AssetEntity> {
        return assetDao.getOrphanAssets(projectId)
    }
    
    /**
     * Get asset type statistics for a project
     * Reads from local database
     */
    suspend fun getAssetTypeStats(projectId: String): List<AssetTypeCount> {
        return assetDao.getAssetTypeStats(projectId)
    }
    
    /**
     * Insert or update assets
     * Writes to local database
     */
    suspend fun insertAssets(assets: List<AssetEntity>) {
        assetDao.insertAssets(assets)
    }
    
    /**
     * Delete all assets for a project
     * Writes to local database
     */
    suspend fun deleteAssetsByProject(projectId: String) {
        assetDao.deleteAssetsByProject(projectId)
    }
    
    /**
     * Sync assets to cloud
     * Reads from database, then makes network request
     * In real scenario, would batch upload asset metadata
     */
    suspend fun syncAssetsToCloud(projectId: String): Result<Int> {
        return try {
            // Read current assets from database (single source of truth)
            val orphanAssets = assetDao.getOrphanAssets(projectId)
            val typeStats = assetDao.getAssetTypeStats(projectId)
            
            // Make network request to sync asset summary
            val request = com.example.ue5analyzer.data.network.CreatePostRequest(
                title = "Asset Sync - Project $projectId",
                body = "Orphan assets: ${orphanAssets.size}, Types: ${typeStats.joinToString { "${it.type}(${it.count})" }}",
                userId = projectId.hashCode().let { if (it < 0) -it else it } % 100
            )
            
            val response = api.createPost(request)
            
            if (response.id > 0) {
                Result.success(orphanAssets.size)
            } else {
                Result.failure(Exception("Sync failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Fetch asset type distribution from cloud
     * Makes network request and returns mock data for demonstration
     * Could be used to compare local vs cloud statistics
     */
    suspend fun fetchAssetTypesFromCloud(): Result<List<AssetTypeCount>> {
        return try {
            // Fetch todos from mock API and map to asset type distribution
            val todos = api.getTodos()
            
            // Simulate asset type distribution based on todo completion status
            val completed = todos.count { it.completed }
            val pending = todos.size - completed
            
            val assetTypes = listOf(
                AssetTypeCount(type = "Completed", count = completed),
                AssetTypeCount(type = "Pending", count = pending),
                AssetTypeCount(type = "Total", count = todos.size)
            )
            
            Result.success(assetTypes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
