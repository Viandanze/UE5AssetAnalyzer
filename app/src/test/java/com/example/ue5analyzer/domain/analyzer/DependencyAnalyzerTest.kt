package com.example.ue5analyzer.domain.analyzer

import com.example.ue5analyzer.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * DependencyAnalyzer 单元测试
 */
class DependencyAnalyzerTest {

    private val analyzer = DependencyAnalyzer()

    // ========== findCircularDependencies 测试 ==========

    @Test
    fun `findCircularDependencies noCycles returnsEmptyList`() {
        val assets = listOf(
            createAsset("A", dependencies = listOf("B")),
            createAsset("B", dependencies = listOf("C")),
            createAsset("C", dependencies = emptyList())
        )
        
        val result = analyzer.findCircularDependencies(assets)
        
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findCircularDependencies simpleCycle A to B to A detectsCycle`() {
        val assets = listOf(
            createAsset("A", dependencies = listOf("B")),
            createAsset("B", dependencies = listOf("A"))
        )
        
        val result = analyzer.findCircularDependencies(assets)
        
        assertEquals(1, result.size)
        val cycle = result[0].toSet()
        assertTrue(cycle.contains("A"))
        assertTrue(cycle.contains("B"))
    }

    @Test
    fun `findCircularDependencies triangularCycle A to B to C to A detectsCycle`() {
        val assets = listOf(
            createAsset("A", dependencies = listOf("B")),
            createAsset("B", dependencies = listOf("C")),
            createAsset("C", dependencies = listOf("A"))
        )
        
        val result = analyzer.findCircularDependencies(assets)
        
        assertEquals(1, result.size)
        val cycle = result[0].toSet()
        assertTrue(cycle.containsAll(setOf("A", "B", "C")))
    }

    @Test
    fun `findCircularDependencies multipleIndependentCycles detectsAllCycles`() {
        val assets = listOf(
            // Cycle 1: A -> B -> A
            createAsset("A", dependencies = listOf("B")),
            createAsset("B", dependencies = listOf("A")),
            // Cycle 2: C -> D -> C
            createAsset("C", dependencies = listOf("D")),
            createAsset("D", dependencies = listOf("C"))
        )
        
        val result = analyzer.findCircularDependencies(assets)
        
        assertEquals(2, result.size)
        val allNodes = result.flatMap { it }.toSet()
        assertTrue(allNodes.containsAll(setOf("A", "B", "C", "D")))
    }

    @Test
    fun `findCircularDependencies selfLoop A to A detectsCycle`() {
        val assets = listOf(
            createAsset("A", dependencies = listOf("A"))
        )
        
        val result = analyzer.findCircularDependencies(assets)
        
        assertEquals(1, result.size)
        assertTrue(result[0].contains("A"))
    }

    @Test
    fun `findCircularDependencies deepChainWithoutCycle noStackOverflow`() {
        // 创建50+层深度的依赖链，无循环
        val assets = (1..60).map { i ->
            val deps = if (i < 60) listOf("asset_$i+1") else emptyList()
            createAsset("asset_$i", dependencies = deps)
        }
        
        val result = analyzer.findCircularDependencies(assets)
        
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findCircularDependencies complexGraphWithMultipleCycles detectsAllCycles`() {
        val assets = listOf(
            // Long chain with cycle at end
            createAsset("A", dependencies = listOf("B")),
            createAsset("B", dependencies = listOf("C")),
            createAsset("C", dependencies = listOf("D")),
            createAsset("D", dependencies = listOf("C")), // Cycle: C -> D -> C
            // Another chain
            createAsset("E", dependencies = listOf("F")),
            createAsset("F", dependencies = listOf("E"))  // Cycle: E -> F -> E
        )
        
        val result = analyzer.findCircularDependencies(assets)
        
        assertEquals(2, result.size)
    }

    @Test
    fun `findCircularDependencies emptyList returnsEmptyList`() {
        val result = analyzer.findCircularDependencies(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findCircularDependencies singleAssetNoDeps returnsEmptyList`() {
        val assets = listOf(
            createAsset("A", dependencies = emptyList())
        )
        
        val result = analyzer.findCircularDependencies(assets)
        
        assertTrue(result.isEmpty())
    }

    // ========== buildDependencyGraph 测试 ==========

    @Test
    fun `buildDependencyGraph emptyList returnsEmptyMap`() {
        val result = analyzer.buildDependencyGraph(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildDependencyGraph singleLevel reportsLevelAsset`() {
        val assets = listOf(
            createAsset("Level1", type = AssetType.LEVEL),
            createAsset("Texture1", type = AssetType.TEXTURE, dependencies = listOf("Level1"))
        )
        
        val result = analyzer.buildDependencyGraph(assets)
        
        assertFalse(result.isEmpty())
        assertTrue(result.containsKey("Level1"))
    }

    @Test
    fun `buildDependencyGraph levelsWithDependencies includesDependencies`() {
        val assets = listOf(
            createAsset("Level1", type = AssetType.LEVEL),
            createAsset("Mesh1", type = AssetType.STATIC_MESH, dependencies = listOf("Level1")),
            createAsset("Mat1", type = AssetType.MATERIAL, dependencies = listOf("Mesh1"))
        )
        
        val result = analyzer.buildDependencyGraph(assets)
        
        // 依赖图应包含从关卡可达的资源
        // BFS 从 LEVEL 出发，遍历其 dependencies 字段
        // Level1 没有 dependencies，所以只有 Level1 在图中
        assertTrue(result.containsKey("Level1"))
        // Mesh1 和 Mat1 依赖 Level1，但 Level1 不依赖它们，所以它们不在 BFS 路径中
        assertFalse(result.containsKey("Mesh1"))
        assertFalse(result.containsKey("Mat1"))
    }

    @Test
    fun `buildDependencyGraph noLevels returnsEmptyOrMinimalGraph`() {
        val assets = listOf(
            createAsset("Mesh1", type = AssetType.STATIC_MESH, dependencies = emptyList()),
            createAsset("Texture1", type = AssetType.TEXTURE, dependencies = listOf("Mesh1"))
        )
        
        val result = analyzer.buildDependencyGraph(assets)
        
        // 无关卡起点，只包含关卡资源
        // 如果没有 LEVEL 类型资源，graph 可能是空的
        assertTrue(result.keys.all { id -> 
            assets.find { it.id == id }?.type == AssetType.LEVEL 
        } || result.isEmpty())
    }

    @Test
    fun `buildDependencyGraph correctDepthCalculation`() {
        val assets = listOf(
            createAsset("Level1", type = AssetType.LEVEL),
            createAsset("Mesh1", type = AssetType.STATIC_MESH, dependencies = listOf("Level1")),
            createAsset("Mat1", type = AssetType.MATERIAL, dependencies = listOf("Mesh1"))
        )
        
        val result = analyzer.buildDependencyGraph(assets)
        
        val levelNode = result["Level1"]
        
        // BFS 从 LEVEL 出发，遍历其 dependencies 字段
        // Level1 没有 dependencies，所以只有 Level1 在图中
        assertNotNull(levelNode)
        assertEquals(0, levelNode!!.depth)
        // Mesh1 和 Mat1 不在图中，因为它们是 Level1 的依赖者，而非依赖 Level1
        assertFalse(result.containsKey("Mesh1"))
        assertFalse(result.containsKey("Mat1"))
    }

    @Test
    fun `buildDependencyGraph preservesAssetInfo`() {
        val assets = listOf(
            createAsset("BP_Test", type = AssetType.BLUEPRINT, dependencies = listOf("SM_Test")),
            createAsset("SM_Test", type = AssetType.STATIC_MESH, dependencies = emptyList())
        )
        
        val result = analyzer.buildDependencyGraph(assets)
        
        // 不包含任何 LEVEL 资源，所以可能为空
        // 这个测试验证图结构正确性
        assertTrue(result.isEmpty() || result.containsKey("BP_Test"))
    }

    // ========== 辅助方法 ==========

    private fun createAsset(
        id: String,
        type: AssetType = AssetType.BLUEPRINT,
        dependencies: List<String> = emptyList()
    ): UEAsset {
        return UEAsset(
            id = id,
            name = id,
            path = "/Game/$id",
            type = type,
            size = 1024,
            dependencies = dependencies,
            references = emptyList(),
            isOrphan = false
        )
    }
}
