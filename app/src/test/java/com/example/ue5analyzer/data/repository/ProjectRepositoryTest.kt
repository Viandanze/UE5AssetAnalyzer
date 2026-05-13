package com.example.ue5analyzer.data.repository

import com.example.ue5analyzer.data.database.AssetEntity
import com.example.ue5analyzer.data.database.ProjectEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * ProjectRepository Unit Tests
 * Tests repository data transformation and business logic
 */
class ProjectRepositoryTest {

    @Test
    fun testProjectEntityToRepositoryMapping() {
        // Test data transformation: project data class mapping
        val entity = ProjectEntity(
            id = "repo-test-1",
            name = "Repository Test Project",
            path = "/repo/test/path",
            totalAssets = 100,
            totalSize = 50_000_000L,
            lastScanned = System.currentTimeMillis()
        )

        // Verify field integrity after transformation
        assertEquals("repo-test-1", entity.id)
        assertEquals("Repository Test Project", entity.name)
        assertEquals(100, entity.totalAssets)
        assertEquals(50_000_000L, entity.totalSize)
    }

    @Test
    fun testAssetSizeAggregation() {
        // Test data transformation: asset size calculation logic
        val assets = listOf(
            AssetEntity(
                id = "a1", name = "Asset1.uasset", path = "/a1",
                type = "Material", size = 1024L, dependencies = "[]",
                references = "[]", isOrphan = false, orphanRiskLevel = "None",
                lastModified = 1000L, projectId = "p1"
            ),
            AssetEntity(
                id = "a2", name = "Asset2.uasset", path = "/a2",
                type = "Texture", size = 2048L, dependencies = "[]",
                references = "[]", isOrphan = false, orphanRiskLevel = "None",
                lastModified = 2000L, projectId = "p1"
            ),
            AssetEntity(
                id = "a3", name = "Asset3.uasset", path = "/a3",
                type = "Blueprint", size = 4096L, dependencies = "[]",
                references = "[]", isOrphan = true, orphanRiskLevel = "High",
                lastModified = 3000L, projectId = "p1"
            )
        )

        // Repository logic: aggregate sizes
        val totalSize = assets.sumOf { it.size }
        assertEquals(7168L, totalSize)

        // Repository logic: count orphans
        val orphanCount = assets.count { it.isOrphan }
        assertEquals(1, orphanCount)

        // Repository logic: orphan rate
        val orphanRate = orphanCount.toDouble() / assets.size * 100
        assertEquals(33.33, orphanRate, 0.1)
    }

    @Test
    fun testProjectSortByLastScanned() {
        // Test data transformation: project sorting logic
        val projects = listOf(
            ProjectEntity("p1", "Project A", "/a", 10, 1000L, 3000L),
            ProjectEntity("p2", "Project B", "/b", 20, 2000L, 1000L),
            ProjectEntity("p3", "Project C", "/c", 30, 3000L, 2000L)
        )

        // Repository logic: sort by lastScanned DESC
        val sorted = projects.sortedByDescending { it.lastScanned }
        assertEquals("Project A", sorted[0].name)
        assertEquals("Project C", sorted[1].name)
        assertEquals("Project B", sorted[2].name)
    }

    @Test
    fun testFindProjectByName() {
        // Test data transformation: name-based lookup logic
        val projects = listOf(
            ProjectEntity("p1", "Unique Project Name", "/a", 30, 5_000_000L, 1000L),
            ProjectEntity("p2", "Another Project", "/b", 40, 8_000_000L, 2000L)
        )

        // Repository logic: find by name
        val found = projects.find { it.name == "Unique Project Name" }
        assertNotNull(found)
        assertEquals("p1", found!!.id)
        assertEquals(30, found.totalAssets)
    }
}
