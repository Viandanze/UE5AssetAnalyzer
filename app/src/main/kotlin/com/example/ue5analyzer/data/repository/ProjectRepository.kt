package com.example.ue5analyzer.data.repository

import com.example.ue5analyzer.data.database.AppDatabase
import com.example.ue5analyzer.data.database.ProjectEntity
import com.example.ue5analyzer.data.network.AssetMetadataRequest
import com.example.ue5analyzer.data.network.CreatePostRequest
import com.example.ue5analyzer.data.network.RetrofitClient
import kotlinx.coroutines.flow.Flow

/**
 * Project Repository
 * Single source of truth for project data
 * Implements read-from-DB-then-network-sync pattern
 */
class ProjectRepository(private val database: AppDatabase) {
    
    private val projectDao = database.projectDao()
    private val api = RetrofitClient.api
    
    /**
     * Get all projects as Flow
     * Reads from local database (single source of truth)
     */
    fun getAllProjects(): Flow<List<ProjectEntity>> {
        return projectDao.getAllProjects()
    }
    
    /**
     * Get project by ID
     * Reads from local database
     */
    suspend fun getProjectById(id: String): ProjectEntity? {
        return projectDao.getProjectById(id)
    }
    
    /**
     * Get project by name
     * Reads from local database
     */
    suspend fun getProjectByName(name: String): ProjectEntity? {
        return projectDao.getProjectByName(name)
    }
    
    /**
     * Insert or update a project
     * Writes to local database
     */
    suspend fun insertProject(project: ProjectEntity) {
        projectDao.insertProject(project)
    }
    
    /**
     * Delete a project
     * Writes to local database
     */
    suspend fun deleteProject(project: ProjectEntity) {
        projectDao.deleteProject(project)
    }
    
    /**
     * Delete project by ID
     * Writes to local database
     */
    suspend fun deleteProjectById(projectId: String) {
        projectDao.deleteProjectById(projectId)
    }
    
    /**
     * Sync project to cloud
     * Reads from database, then makes network request and optionally updates local DB
     */
    suspend fun syncProjectToCloud(project: ProjectEntity): Result<Unit> {
        return try {
            // Read current state from database (ensure data is fresh)
            val currentProject = projectDao.getProjectById(project.id)
            
            if (currentProject != null) {
                // Make network request to sync
                val request = AssetMetadataRequest(
                    projectId = currentProject.id,
                    projectName = currentProject.name,
                    totalAssets = currentProject.totalAssets,
                    totalSize = currentProject.totalSize,
                    lastScanned = currentProject.lastScanned,
                    syncStatus = "synced"
                )
                
                val response = api.uploadAssetMetadata(request)
                
                if (response.id > 0) {
                    // Update local database with synced status
                    val syncedProject = currentProject.copy()
                    projectDao.insertProject(syncedProject)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Sync failed: invalid response"))
                }
            } else {
                Result.failure(Exception("Project not found in database"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Fetch project templates from cloud
     * Makes network request and maps response to ProjectEntity format
     * Used for initializing new projects with template data
     */
    suspend fun fetchProjectTemplates(): Result<List<ProjectEntity>> {
        return try {
            val posts = api.getPosts()
            
            // Map API response to ProjectEntity format
            // In real scenario, this would be specific project template data
            val templates = posts.take(10).mapIndexed { index, post ->
                ProjectEntity(
                    id = "template_${post.id}",
                    name = "Template: ${post.title.take(30)}",
                    path = "/templates/${post.id}",
                    totalAssets = (post.id * 10),
                    totalSize = (post.id * 1024L * 1024),
                    lastScanned = System.currentTimeMillis()
                )
            }
            
            Result.success(templates)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
