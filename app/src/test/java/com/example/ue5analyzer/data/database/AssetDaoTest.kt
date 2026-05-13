package com.example.ue5analyzer.data.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO Unit Tests
 * Tests Room DAO CRUD operations using in-memory database
 */
@RunWith(AndroidJUnit4::class)
class AssetDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var assetDao: AssetDao
    private lateinit var projectDao: ProjectDao

    @Before
    fun setup() {
        // Use in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        
        assetDao = database.assetDao()
        projectDao = database.projectDao()
    }

    @Test
    fun testInsertAndGetAssets() = runBlocking {
        // Arrange: Create test project first
        val project = ProjectEntity(
            id = "test-project-1",
            name = "Test Project",
            path = "/test/path",
            totalAssets = 2,
            totalSize = 1024L,
            lastScanned = System.currentTimeMillis()
        )
        projectDao.insertProject(project)

        // Arrange: Create test assets
        val assets = listOf(
            AssetEntity(
                id = "asset-1",
                name = "Material.uasset",
                path = "/Content/Materials/Material.uasset",
                type = "Material",
                size = 512L,
                dependencies = "[]",
                references = "[]",
                isOrphan = false,
                orphanRiskLevel = "None",
                lastModified = System.currentTimeMillis(),
                projectId = "test-project-1"
            ),
            AssetEntity(
                id = "asset-2",
                name = "OrphanTexture.uasset",
                path = "/Content/Textures/OrphanTexture.uasset",
                type = "Texture",
                size = 512L,
                dependencies = "[]",
                references = "[]",
                isOrphan = true,
                orphanRiskLevel = "High",
                lastModified = System.currentTimeMillis(),
                projectId = "test-project-1"
            )
        )

        // Act: Insert assets
        assetDao.insertAssets(assets)

        // Assert: Get assets and verify
        val retrievedAssets = assetDao.getAssetsByProject("test-project-1").first()
        assertEquals(2, retrievedAssets.size)
        assertEquals("Material.uasset", retrievedAssets[0].name)
        assertEquals("OrphanTexture.uasset", retrievedAssets[1].name)
    }

    @Test
    fun testGetOrphanAssets() = runBlocking {
        // Arrange: Create test project
        val project = ProjectEntity(
            id = "test-project-2",
            name = "Orphan Test Project",
            path = "/test/path2",
            totalAssets = 3,
            totalSize = 2048L,
            lastScanned = System.currentTimeMillis()
        )
        projectDao.insertProject(project)

        // Arrange: Create assets with different orphan status
        val assets = listOf(
            AssetEntity(
                id = "asset-3",
                name = "Used.uasset",
                path = "/Content/Used.uasset",
                type = "StaticMesh",
                size = 256L,
                dependencies = "[]",
                references = "[]",
                isOrphan = false,
                orphanRiskLevel = "None",
                lastModified = System.currentTimeMillis(),
                projectId = "test-project-2"
            ),
            AssetEntity(
                id = "asset-4",
                name = "Orphan1.uasset",
                path = "/Content/Orphan1.uasset",
                type = "Blueprint",
                size = 256L,
                dependencies = "[]",
                references = "[]",
                isOrphan = true,
                orphanRiskLevel = "Medium",
                lastModified = System.currentTimeMillis(),
                projectId = "test-project-2"
            ),
            AssetEntity(
                id = "asset-5",
                name = "Orphan2.uasset",
                path = "/Content/Orphan2.uasset",
                type = "Material",
                size = 256L,
                dependencies = "[]",
                references = "[]",
                isOrphan = true,
                orphanRiskLevel = "High",
                lastModified = System.currentTimeMillis(),
                projectId = "test-project-2"
            )
        )
        assetDao.insertAssets(assets)

        // Act: Get orphan assets
        val orphanAssets = assetDao.getOrphanAssets("test-project-2")

        // Assert: Verify only orphan assets are returned
        assertEquals(2, orphanAssets.size)
        assertTrue(orphanAssets.all { it.isOrphan })
        assertTrue(orphanAssets.any { it.orphanRiskLevel == "Medium" })
        assertTrue(orphanAssets.any { it.orphanRiskLevel == "High" })
    }

    @Test
    fun testDeleteAssetsByProject() = runBlocking {
        // Arrange: Create test project
        val project = ProjectEntity(
            id = "test-project-3",
            name = "Delete Test Project",
            path = "/test/path3",
            totalAssets = 2,
            totalSize = 1024L,
            lastScanned = System.currentTimeMillis()
        )
        projectDao.insertProject(project)

        // Arrange: Create test assets
        val assets = listOf(
            AssetEntity(
                id = "asset-6",
                name = "Asset1.uasset",
                path = "/Content/Asset1.uasset",
                type = "Texture",
                size = 512L,
                dependencies = "[]",
                references = "[]",
                isOrphan = false,
                orphanRiskLevel = "None",
                lastModified = System.currentTimeMillis(),
                projectId = "test-project-3"
            ),
            AssetEntity(
                id = "asset-7",
                name = "Asset2.uasset",
                path = "/Content/Asset2.uasset",
                type = "Material",
                size = 512L,
                dependencies = "[]",
                references = "[]",
                isOrphan = false,
                orphanRiskLevel = "None",
                lastModified = System.currentTimeMillis(),
                projectId = "test-project-3"
            )
        )
        assetDao.insertAssets(assets)

        // Act: Delete assets by project
        assetDao.deleteAssetsByProject("test-project-3")

        // Assert: Verify assets are deleted
        val remainingAssets = assetDao.getAssetsByProject("test-project-3").first()
        assertTrue(remainingAssets.isEmpty())
    }
}
