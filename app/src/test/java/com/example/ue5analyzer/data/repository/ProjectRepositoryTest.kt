package com.example.ue5analyzer.data.repository

import com.example.ue5analyzer.data.database.AppDatabase
import com.example.ue5analyzer.data.database.AssetEntity
import com.example.ue5analyzer.data.database.ProjectEntity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ProjectRepository Unit Tests
 * Tests repository data transformation and business logic
 */
class ProjectRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var projectRepository: ProjectRepository
    private lateinit var assetRepository: AssetRepository

    @Before
    fun setup() {
        // Use in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        
        projectRepository = ProjectRepository(database)
        assetRepository = AssetRepository(database)
    }

    @Test
    fun testInsertAndGetProject() = runBlocking {
        // Arrange: Create test project
        val project = ProjectEntity(
            id = "repo-test-1",
            name = "Repository Test Project",
            path = "/repo/test/path",
            totalAssets = 100,
            totalSize = 50_000_000L,
            lastScanned = System.currentTimeMillis()
        )

        // Act: Insert and retrieve
        projectRepository.insertProject(project)
        val retrieved = projectRepository.getProjectById("repo-test-1")

        // Assert: Verify data integrity
        assertNotNull(retrieved)
        assertEquals("Repository Test Project", retrieved!!.name)
        assertEquals(100, retrieved.totalAssets)
        assertEquals(50_000_000L, retrieved.totalSize)
    }

    @Test
    fun testGetAllProjects() = runBlocking {
        // Arrange: Insert multiple projects
        val projects = listOf(
            ProjectEntity(
                id = "multi-1",
                name = "Project A",
                path = "/path/a",
                totalAssets = 50,
                totalSize = 10_000_000L,
                lastScanned = System.currentTimeMillis()
            ),
            ProjectEntity(
                id = "multi-2",
                name = "Project B",
                path = "/path/b",
                totalAssets = 75,
                totalSize = 15_000_000L,
                lastScanned = System.currentTimeMillis() - 1000
            ),
            ProjectEntity(
                id = "multi-3",
                name = "Project C",
                path = "/path/c",
                totalAssets = 200,
                totalSize = 100_000_000L,
                lastScanned = System.currentTimeMillis() - 2000
            )
        )
        
        projects.forEach { projectRepository.insertProject(it) }

        // Act: Get all projects
        val allProjects = projectRepository.getAllProjects().first()

        // Assert: Verify all projects are retrieved
        assertEquals(3, allProjects.size)
        // Verify sorted by lastScanned DESC
        assertEquals("Project C", allProjects[0].name)
        assertEquals("Project B", allProjects[1].name)
        assertEquals("Project A", allProjects[2].name)
    }

    @Test
    fun testDeleteProject() = runBlocking {
        // Arrange: Insert project with assets
        val project = ProjectEntity(
            id = "delete-test",
            name = "Delete Test",
            path = "/delete/test",
            totalAssets = 10,
            totalSize = 1_000_000L,
            lastScanned = System.currentTimeMillis()
        )
        projectRepository.insertProject(project)
        
        // Add some assets
        val assets = listOf(
            AssetEntity(
                id = "del-asset-1",
                name = "Asset1.uasset",
                path = "/Content/Asset1.uasset",
                type = "Material",
                size = 512L,
                dependencies = "[]",
                references = "[]",
                isOrphan = false,
                orphanRiskLevel = "None",
                lastModified = System.currentTimeMillis(),
                projectId = "delete-test"
            )
        )
        assetRepository.insertAssets(assets)

        // Act: Delete project
        projectRepository.deleteProjectById("delete-test")

        // Assert: Verify project and assets are deleted
        val retrieved = projectRepository.getProjectById("delete-test")
        assertNull(retrieved)
        
        val remainingAssets = assetRepository.getAssetsByProject("delete-test").first()
        assertTrue(remainingAssets.isEmpty())
    }

    @Test
    fun testGetProjectByName() = runBlocking {
        // Arrange: Insert projects with different names
        val project1 = ProjectEntity(
            id = "name-test-1",
            name = "Unique Project Name",
            path = "/unique/path",
            totalAssets = 30,
            totalSize = 5_000_000L,
            lastScanned = System.currentTimeMillis()
        )
        val project2 = ProjectEntity(
            id = "name-test-2",
            name = "Another Project",
            path = "/another/path",
            totalAssets = 40,
            totalSize = 8_000_000L,
            lastScanned = System.currentTimeMillis()
        )
        
        projectRepository.insertProject(project1)
        projectRepository.insertProject(project2)

        // Act: Find by name
        val found = projectRepository.getProjectByName("Unique Project Name")

        // Assert: Verify correct project is returned
        assertNotNull(found)
        assertEquals("name-test-1", found!!.id)
        assertEquals(30, found.totalAssets)
    }
}
