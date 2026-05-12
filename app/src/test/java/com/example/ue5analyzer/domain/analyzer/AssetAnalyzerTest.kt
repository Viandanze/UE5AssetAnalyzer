package com.example.ue5analyzer.domain.analyzer

import com.example.ue5analyzer.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * AssetAnalyzer 单元测试
 */
class AssetAnalyzerTest {

    private val analyzer = AssetAnalyzer()

    // ========== calculateHealthScore 测试 ==========

    @Test
    fun `calculateHealthScore emptyList returnsFullScore`() {
        val result = analyzer.calculateHealthScore(emptyList())
        assertEquals(100, result)
    }

    @Test
    fun `calculateHealthScore allHealthyAssets returnsFullScore`() {
        val assets = listOf(
            createAsset("asset1", isOrphan = false, refCount = 5),
            createAsset("asset2", isOrphan = false, refCount = 3),
            createAsset("asset3", isOrphan = false, refCount = 7)
        )
        
        val result = analyzer.calculateHealthScore(assets)
        assertEquals(100, result)
    }

    @Test
    fun `calculateHealthScore allOrphanAssets returnsLowScore`() {
        val assets = listOf(
            createAsset("orphan1", isOrphan = true, refCount = 0),
            createAsset("orphan2", isOrphan = true, refCount = 0),
            createAsset("orphan3", isOrphan = true, refCount = 0)
        )
        
        val result = analyzer.calculateHealthScore(assets)
        // 孤立率100% -> orphanScore=0, 有引用数-> refScore>0
        assertTrue(result < 50)
    }

    @Test
    fun `calculateHealthScore partialOrphanAssets returnsMediumScore`() {
        val assets = listOf(
            createAsset("healthy1", isOrphan = false, refCount = 5),
            createAsset("healthy2", isOrphan = false, refCount = 3),
            createAsset("orphan1", isOrphan = true, refCount = 0)
        )
        
        val result = analyzer.calculateHealthScore(assets)
        // 1/3 孤立 -> orphanScore ≈ 33.3, 加上引用分
        assertTrue(result in 50..80)
    }

    @Test
    fun `calculateHealthScore mixedScenarioWithHighReferences returnsHigherScore`() {
        val assets = listOf(
            createAsset("orphan1", isOrphan = true, refCount = 0),
            createAsset("healthy1", isOrphan = false, refCount = 10),
            createAsset("healthy2", isOrphan = false, refCount = 8)
        )
        
        val result = analyzer.calculateHealthScore(assets)
        // 1/3 孤立 + 高引用 -> 应得中高分
        assertTrue(result >= 50)
        assertTrue(result < 100)
    }

    @Test
    fun `calculateHealthScore singleHealthyAsset returnsFullScore`() {
        val assets = listOf(
            createAsset("singleAsset", isOrphan = false, refCount = 5)
        )
        
        val result = analyzer.calculateHealthScore(assets)
        assertEquals(100, result)
    }

    @Test
    fun `calculateHealthScore singleOrphanAsset returnsZeroOrphanScore`() {
        val assets = listOf(
            createAsset("orphanAsset", isOrphan = true, refCount = 0)
        )
        
        val result = analyzer.calculateHealthScore(assets)
        // 100%孤立 -> orphanScore=0, refScore=0
        assertEquals(0, result)
    }

    // ========== 孤立资源识别逻辑测试 ==========

    @Test
    fun `generateReport identifiesOrphanAssets correctly`() {
        val assets = listOf(
            createAsset("level1", type = AssetType.LEVEL, isOrphan = false, refCount = 0),
            createAsset("orphan1", isOrphan = true, refCount = 0),
            createAsset("orphan2", isOrphan = true, refCount = 0),
            createAsset("healthy1", isOrphan = false, refCount = 5)
        )
        
        val report = analyzer.generateReport("/path", "TestProject", assets)
        
        assertEquals(2, report.orphanCount)
        assertEquals(2, report.orphanAssets.size)
        assertTrue(report.orphanAssets.all { it.isOrphan })
    }

    @Test
    fun `generateReport emptyProject returnsZeroOrphans`() {
        val report = analyzer.generateReport("/path", "EmptyProject", emptyList())
        
        assertEquals(0, report.orphanCount)
        assertTrue(report.orphanAssets.isEmpty())
    }

    @Test
    fun `generateReport calculatesHealthScore inReport`() {
        val assets = listOf(
            createAsset("healthy1", isOrphan = false, refCount = 5),
            createAsset("healthy2", isOrphan = false, refCount = 3)
        )
        
        val report = analyzer.generateReport("/path", "TestProject", assets)
        
        assertEquals(90, report.healthScore)
    }

    @Test
    fun `generateReport calculatesTotalSize correctly`() {
        val assets = listOf(
            createAsset("small", size = 1024),
            createAsset("medium", size = 10240),
            createAsset("large", size = 102400)
        )
        
        val report = analyzer.generateReport("/path", "TestProject", assets)
        
        assertEquals(113664L, report.totalSize)
    }

    // ========== 辅助方法 ==========

    private fun createAsset(
        name: String,
        type: AssetType = AssetType.BLUEPRINT,
        isOrphan: Boolean = false,
        refCount: Int = 0,
        size: Long = 1024
    ): UEAsset {
        val references = (1..refCount).map { "ref_$it" }
        return UEAsset(
            id = name,
            name = name,
            path = "/Game/$name",
            type = type,
            size = size,
            dependencies = emptyList(),
            references = references,
            isOrphan = isOrphan,
            orphanRiskLevel = when {
                refCount >= 2 -> OrphanRiskLevel.NONE
                refCount == 1 -> OrphanRiskLevel.LOW
                else -> OrphanRiskLevel.HIGH
            }
        )
    }
}
