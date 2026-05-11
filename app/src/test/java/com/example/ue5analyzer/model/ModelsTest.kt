package com.example.ue5analyzer.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Models 单元测试
 */
class ModelsTest {

    // ========== AssetType 枚举测试 ==========

    @Test
    fun `AssetType_fromName_blueprintPrefix_returnsBlueprint() {
        val result = AssetType.fromName("BP_MyCharacter")
        assertEquals(AssetType.BLUEPRINT, result)
    }

    @Test
    fun `AssetType_fromName_staticMeshPrefix_returnsStaticMesh() {
        val result = AssetType.fromName("SM_Cube")
        assertEquals(AssetType.STATIC_MESH, result)
    }

    @Test
    fun `AssetType_fromName_skeletalMeshPrefix_returnsSkeletalMesh() {
        val result = AssetType.fromName("SK_Character")
        assertEquals(AssetType.SKELETAL_MESH, result)
    }

    @Test
    fun `AssetType_fromName_materialPrefix_returnsMaterial() {
        val result = AssetType.fromName("M_BaseMaterial")
        assertEquals(AssetType.MATERIAL, result)
    }

    @Test
    fun `AssetType_fromName_materialInstancePrefix_returnsMaterialInstance() {
        val result = AssetType.fromName("MI_InstancedMaterial")
        assertEquals(AssetType.MATERIAL_INSTANCE, result)
    }

    @Test
    fun `AssetType_fromName_texturePrefix_returnsTexture() {
        val result = AssetType.fromName("T_Diffuse")
        assertEquals(AssetType.TEXTURE, result)
    }

    @Test
    fun `AssetType_fromName_soundPrefix_returnsSound() {
        val result = AssetType.fromName("S_BackgroundMusic")
        assertEquals(AssetType.SOUND, result)
    }

    @Test
    fun `AssetType_fromName_particleSystemPrefix_returnsParticleSystem() {
        val result = AssetType.fromName("P_FireEffect")
        assertEquals(AssetType.PARTICLE_SYSTEM, result)
    }

    @Test
    fun `AssetType_fromName_animationPrefix_returnsAnimation() {
        val result = AssetType.fromName("A_RunAnimation")
        assertEquals(AssetType.ANIMATION, result)
    }

    @Test
    fun `AssetType_fromName_levelPrefix_returnsLevel() {
        val result = AssetType.fromName("LVL_MainMenu")
        assertEquals(AssetType.LEVEL, result)
    }

    @Test
    fun `AssetType_fromName_widgetPrefix_returnsWidget() {
        val result = AssetType.fromName("WBP_MainHUD")
        assertEquals(AssetType.WIDGET, result)
    }

    @Test
    fun `AssetType_fromName_enumPrefix_returnsEnum() {
        val result = AssetType.fromName("E_ItemType")
        assertEquals(AssetType.ENUM, result)
    }

    @Test
    fun `AssetType_fromName_structPrefix_returnsStruct() {
        val result = AssetType.fromName("F_PlayerData")
        assertEquals(AssetType.STRUCT, result)
    }

    @Test
    fun `AssetType_fromName_interfacePrefix_returnsInterface() {
        val result = AssetType.fromName("I_Damageable")
        assertEquals(AssetType.INTERFACE, result)
    }

    @Test
    fun `AssetType_fromName_dataTablePrefix_returnsDataTable() {
        val result = AssetType.fromName("DT_WeaponStats")
        assertEquals(AssetType.DATA_TABLE, result)
    }

    @Test
    fun `AssetType_fromName_curvePrefix_returnsCurve() {
        val result = AssetType.fromName("CR_HealthCurve")
        assertEquals(AssetType.CURVE, result)
    }

    @Test
    fun `AssetType_fromName_unknownPrefix_returnsUnknown() {
        val result = AssetType.fromName("RandomAsset")
        assertEquals(AssetType.UNKNOWN, result)
    }

    @Test
    fun `AssetType_fromName_emptyName_returnsUnknown() {
        val result = AssetType.fromName("")
        assertEquals(AssetType.UNKNOWN, result)
    }

    @Test
    fun `AssetType_fromName_partialMatch_doesNotMatch() {
        // 名称包含 BP_ 但不是以 BP_ 开头
        val result = AssetType.fromName("MyAsset_BP_Test")
        assertEquals(AssetType.UNKNOWN, result)
    }

    @Test
    fun `AssetType_fromName_caseSensitive() {
        // UE 资源命名是大小写敏感的
        val result = AssetType.fromName("bp_MyAsset")
        assertEquals(AssetType.UNKNOWN, result)
    }

    // ========== AssetType displayName 测试 ==========

    @Test
    fun `AssetType_displayName_blueprint_returnsChinese() {
        assertEquals("蓝图", AssetType.BLUEPRINT.displayName)
    }

    @Test
    fun `AssetType_displayName_level_returnsChinese() {
        assertEquals("关卡", AssetType.LEVEL.displayName)
    }

    // ========== OrphanRiskLevel 枚举测试 ==========

    @Test
    fun `OrphanRiskLevel_noReferences_returnsHigh() {
        val asset = UEAsset(
            id = "orphan",
            name = "orphan",
            path = "/Game/orphan",
            type = AssetType.BLUEPRINT,
            size = 1024,
            references = emptyList(),
            orphanRiskLevel = OrphanRiskLevel.HIGH
        )
        
        assertEquals(OrphanRiskLevel.HIGH, asset.orphanRiskLevel)
        assertEquals(0, asset.references.size)
    }

    @Test
    fun `OrphanRiskLevel_oneReference_returnsLow() {
        val asset = UEAsset(
            id = "lowRisk",
            name = "lowRisk",
            path = "/Game/lowRisk",
            type = AssetType.BLUEPRINT,
            size = 1024,
            references = listOf("ref1"),
            orphanRiskLevel = OrphanRiskLevel.LOW
        )
        
        assertEquals(OrphanRiskLevel.LOW, asset.orphanRiskLevel)
        assertEquals(1, asset.references.size)
    }

    @Test
    fun `OrphanRiskLevel_twoReferences_returnsNone() {
        val asset = UEAsset(
            id = "safe",
            name = "safe",
            path = "/Game/safe",
            type = AssetType.BLUEPRINT,
            size = 1024,
            references = listOf("ref1", "ref2"),
            orphanRiskLevel = OrphanRiskLevel.NONE
        )
        
        assertEquals(OrphanRiskLevel.NONE, asset.orphanRiskLevel)
        assertEquals(2, asset.references.size)
    }

    @Test
    fun `OrphanRiskLevel_manyReferences_returnsNone() {
        val asset = UEAsset(
            id = "popular",
            name = "popular",
            path = "/Game/popular",
            type = AssetType.MATERIAL,
            size = 1024,
            references = listOf("ref1", "ref2", "ref3", "ref4", "ref5"),
            orphanRiskLevel = OrphanRiskLevel.NONE
        )
        
        assertEquals(OrphanRiskLevel.NONE, asset.orphanRiskLevel)
    }

    // ========== UEAsset 数据类测试 ==========

    @Test
    fun `UEAsset_defaultValues_areCorrect() {
        val asset = UEAsset(
            id = "test",
            name = "Test",
            path = "/Game/Test",
            type = AssetType.BLUEPRINT,
            size = 1024
        )
        
        assertTrue(asset.dependencies.isEmpty())
        assertTrue(asset.references.isEmpty())
        assertFalse(asset.isOrphan)
        assertEquals(OrphanRiskLevel.NONE, asset.orphanRiskLevel)
        assertTrue(asset.lastModified > 0)
    }

    @Test
    fun `UEAsset_equality_basedOnId() {
        val asset1 = UEAsset(
            id = "sameId",
            name = "Name1",
            path = "/Game/Path1",
            type = AssetType.BLUEPRINT,
            size = 100
        )
        
        val asset2 = UEAsset(
            id = "sameId",
            name = "Name2",
            path = "/Game/Path2",
            type = AssetType.STATIC_MESH,
            size = 200
        )
        
        // 相同 ID 应该相等（数据类默认比较所有属性）
        assertEquals(asset1, asset2)
    }

    @Test
    fun `UEAsset_copy_modifiesCorrectly() {
        val original = UEAsset(
            id = "original",
            name = "Original",
            path = "/Game/Original",
            type = AssetType.BLUEPRINT,
            size = 1024,
            isOrphan = false
        )
        
        val copied = original.copy(isOrphan = true, orphanRiskLevel = OrphanRiskLevel.HIGH)
        
        assertEquals("original", copied.id)
        assertTrue(copied.isOrphan)
        assertEquals(OrphanRiskLevel.HIGH, copied.orphanRiskLevel)
    }

    // ========== DependencyNode 数据类测试 ==========

    @Test
    fun `DependencyNode_defaultDepth_isZero() {
        val node = DependencyNode(
            assetId = "test",
            assetName = "Test",
            assetType = AssetType.BLUEPRINT,
            dependencies = emptyList()
        )
        
        assertEquals(0, node.depth)
    }

    @Test
    fun `DependencyNode_withDepth_storesCorrectly() {
        val node = DependencyNode(
            assetId = "test",
            assetName = "Test",
            assetType = AssetType.BLUEPRINT,
            dependencies = emptyList(),
            depth = 5
        )
        
        assertEquals(5, node.depth)
    }

    // ========== AnalysisReport 数据类测试 ==========

    @Test
    fun `AnalysisReport_defaultGeneratedAt_isCurrentTime() {
        val before = System.currentTimeMillis()
        val report = AnalysisReport(
            projectPath = "/path",
            projectName = "Project",
            totalAssets = 10,
            totalSize = 10240,
            orphanCount = 2,
            assetsByType = emptyMap(),
            largestAssets = emptyList(),
            mostReferenced = emptyList(),
            orphanAssets = emptyList()
        )
        val after = System.currentTimeMillis()
        
        assertTrue(report.generatedAt in before..after)
    }

    @Test
    fun `AnalysisReport_defaultHealthScore_isZero() {
        val report = AnalysisReport(
            projectPath = "/path",
            projectName = "Project",
            totalAssets = 10,
            totalSize = 10240,
            orphanCount = 0,
            assetsByType = emptyMap(),
            largestAssets = emptyList(),
            mostReferenced = emptyList(),
            orphanAssets = emptyList()
        )
        
        assertEquals(0, report.healthScore)
    }

    @Test
    fun `AnalysisReport_defaultCircularDependencies_isEmpty() {
        val report = AnalysisReport(
            projectPath = "/path",
            projectName = "Project",
            totalAssets = 10,
            totalSize = 10240,
            orphanCount = 0,
            assetsByType = emptyMap(),
            largestAssets = emptyList(),
            mostReferenced = emptyList(),
            orphanAssets = emptyList()
        )
        
        assertTrue(report.circularDependencies.isEmpty())
    }

    // ========== ScanResult 数据类测试 ==========

    @Test
    fun `ScanResult_defaultScanTime_isCurrentTime() {
        val before = System.currentTimeMillis()
        val result = ScanResult(
            projectPath = "/path",
            projectName = "Project",
            totalAssets = 10,
            totalSize = 10240,
            assetsByType = emptyMap(),
            allAssets = emptyList(),
            orphanAssets = emptyList()
        )
        val after = System.currentTimeMillis()
        
        assertTrue(result.scanTime in before..after)
    }
}
