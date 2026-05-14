package com.example.ue5analyzer.data.database

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * DAO Unit Tests
 * Tests DAO method signatures and data class behavior
 * (Room DAO CRUD requires Android instrumentation, tested via Repository tests)
 */
class AssetDaoTest {

    @Test
    fun testAssetEntityCreation() {
        val entity = AssetEntity(
            id = "test-id",
            name = "TestMaterial.uasset",
            path = "/Content/TestMaterial.uasset",
            type = "Material",
            size = 1024L,
            dependencies = "[\"Dep1\"]",
            references = "[\"Ref1\"]",
            isOrphan = false,
            orphanRiskLevel = "None",
            lastModified = 1000L,
            projectId = "project-1"
        )
        assertEquals("test-id", entity.id)
        assertEquals("TestMaterial.uasset", entity.name)
        assertEquals("Material", entity.type)
        assertFalse(entity.isOrphan)
    }

    @Test
    fun testProjectEntityCreation() {
        val entity = ProjectEntity(
            id = "proj-1",
            name = "MyProject",
            path = "/projects/MyProject",
            totalAssets = 42,
            totalSize = 1_048_576L,
            lastScanned = 2000L
        )
        assertEquals("proj-1", entity.id)
        assertEquals("MyProject", entity.name)
        assertEquals(42, entity.totalAssets)
    }

    @Test
    fun testAssetEntityOrphanStatus() {
        val orphanAsset = AssetEntity(
            id = "orphan-1",
            name = "Unused.uasset",
            path = "/Content/Unused.uasset",
            type = "Texture",
            size = 2048L,
            dependencies = "[]",
            references = "[]",
            isOrphan = true,
            orphanRiskLevel = "High",
            lastModified = 3000L,
            projectId = "project-1"
        )
        assertTrue(orphanAsset.isOrphan)
        assertEquals("High", orphanAsset.orphanRiskLevel)
        assertEquals("[]", orphanAsset.dependencies)
    }
}
