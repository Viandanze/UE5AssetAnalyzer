package com.example.ue5analyzer.domain.analyzer

import com.example.ue5analyzer.model.*
import java.util.*

/**
 * 依赖图分析器
 */

// 魔法数字常量
private const val HEALTH_REF_AVERAGE = 5f  // 健康度计算中引用数参考平均值

class DependencyAnalyzer {
    
    /**
     * 构建依赖图
     */
    fun buildDependencyGraph(assets: List<UEAsset>): Map<String, DependencyNode> {
        val graph = mutableMapOf<String, DependencyNode>()
        val assetMap = assets.associateBy { it.id }
        
        // BFS 计算深度
        val visited = mutableSetOf<String>()
        val queue = LinkedList<Pair<String, Int>>()
        
        // 从关卡资源开始
        assets.filter { it.type == AssetType.LEVEL }.forEach {
            queue.offer(it.id to 0)
        }
        
        while (queue.isNotEmpty()) {
            val (assetId, depth) = queue.poll()
            if (assetId in visited) continue
            visited.add(assetId)
            
            val asset = assetMap[assetId] ?: continue
            graph[assetId] = DependencyNode(
                assetId = asset.id,
                assetName = asset.name,
                assetType = asset.type,
                dependencies = asset.dependencies,
                depth = depth
            )
            
            asset.dependencies.forEach { depId ->
                if (depId !in visited) {
                    queue.offer(depId to depth + 1)
                }
            }
        }
        
        return graph
    }
    
    /**
     * 查找循环依赖（去重版本）- 使用迭代DFS避免栈溢出
     */
    fun findCircularDependencies(assets: List<UEAsset>): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val assetMap = assets.associateBy { it.id }
        val visited = mutableSetOf<String>()
        
        // 遍历所有节点作为起点
        assets.forEach { startAsset ->
            if (startAsset.id in visited) return@forEach
            
            // 迭代DFS使用显式栈
            val stack = ArrayDeque<Pair<String, List<String>>>()
            stack.push(startAsset.id to emptyList())
            
            while (stack.isNotEmpty()) {
                val (currentId, path) = stack.pop()
                
                // 如果当前节点已在当前路径中，说明存在环
                if (currentId in path) {
                    val cycleStart = path.indexOf(currentId)
                    if (cycleStart >= 0) {
                        cycles.add(path.subList(cycleStart, path.size) + currentId)
                    }
                    continue
                }
                
                if (currentId in visited) continue
                visited.add(currentId)
                
                val asset = assetMap[currentId] ?: continue
                val newPath = path + currentId
                
                // 将依赖项压入栈（逆序以保持原始顺序）
                for (i in asset.dependencies.indices.reversed()) {
                    stack.push(asset.dependencies[i] to newPath)
                }
            }
        }
        
        // 去重：将环按排序后的节点集合去重
        val seen = mutableSetOf<String>()
        val uniqueCycles = mutableListOf<List<String>>()
        
        for (cycle in cycles) {
            // 取环中不重复的节点（去掉最后一个重复节点），排序后作为key
            val key = cycle.dropLast(1).sorted().joinToString(",")
            if (key !in seen) {
                seen.add(key)
                uniqueCycles.add(cycle)
            }
        }
        
        return uniqueCycles
    }
    
    /**
     * 按引用数排序
     */
    fun sortByReferences(assets: List<UEAsset>): List<UEAsset> {
        return assets.sortedByDescending { it.references.size }
    }
    
    /**
     * 按大小排序
     */
    fun sortBySize(assets: List<UEAsset>): List<UEAsset> {
        return assets.sortedByDescending { it.size }
    }
}

/**
 * 资源分析器
 */
class AssetAnalyzer {
    
    private val dependencyAnalyzer = DependencyAnalyzer()
    
    /**
     * 生成分析报告
     */
    fun generateReport(
        projectPath: String,
        projectName: String,
        assets: List<UEAsset>
    ): AnalysisReport {
        val assetsByType = assets.groupingBy { it.type }.eachCount()
        val orphanAssets = assets.filter { it.isOrphan }
        val largestAssets = dependencyAnalyzer.sortBySize(assets).take(10)
        val mostReferenced = dependencyAnalyzer.sortByReferences(assets).take(10)
        
        // 计算健康度
        val healthScore = calculateHealthScore(assets)
        
        // 计算循环依赖
        val circularDependencies = findCircularDependenciesNames(assets)
        
        return AnalysisReport(
            projectPath = projectPath,
            projectName = projectName,
            totalAssets = assets.size,
            totalSize = assets.sumOf { it.size },
            orphanCount = orphanAssets.size,
            assetsByType = assetsByType,
            largestAssets = largestAssets,
            mostReferenced = mostReferenced,
            orphanAssets = orphanAssets,
            healthScore = healthScore,
            circularDependencies = circularDependencies
        )
    }
    
    /**
     * 查找循环依赖（返回资产名称列表）- 使用迭代DFS避免栈溢出
     */
    private fun findCircularDependenciesNames(assets: List<UEAsset>): List<List<String>> {
        val assetMap = assets.associateBy { it.id }
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        
        // 遍历所有节点作为起点
        assets.forEach { startAsset ->
            if (startAsset.id in visited) return@forEach
            
            // 迭代DFS使用显式栈
            val stack = ArrayDeque<Pair<String, List<String>>>()
            stack.push(startAsset.id to emptyList())
            
            while (stack.isNotEmpty()) {
                val (currentId, path) = stack.pop()
                
                // 如果当前节点已在当前路径中，说明存在环
                if (currentId in path) {
                    val cycleStart = path.indexOf(currentId)
                    if (cycleStart >= 0) {
                        // 将ID转换为名称
                        val cycleIds = path.subList(cycleStart, path.size) + currentId
                        val cycleNames = cycleIds.mapNotNull { assetMap[it]?.name }
                        if (cycleNames.isNotEmpty()) {
                            cycles.add(cycleNames)
                        }
                    }
                    continue
                }
                
                if (currentId in visited) continue
                visited.add(currentId)
                
                val asset = assetMap[currentId] ?: continue
                val newPath = path + currentId
                
                // 将依赖项压入栈（逆序以保持原始顺序）
                for (i in asset.dependencies.indices.reversed()) {
                    stack.push(asset.dependencies[i] to newPath)
                }
            }
        }
        
        // 去重
        val seen = mutableSetOf<String>()
        val uniqueCycles = mutableListOf<List<String>>()
        
        for (cycle in cycles) {
            val key = cycle.sorted().joinToString(",")
            if (key !in seen) {
                seen.add(key)
                uniqueCycles.add(cycle)
            }
        }
        
        return uniqueCycles
    }
    
    /**
     * 计算资源健康度得分
     */
    fun calculateHealthScore(assets: List<UEAsset>): Int {
        if (assets.isEmpty()) return 100
        
        val orphanRatio = assets.count { it.isOrphan }.toFloat() / assets.size
        val avgReferences = assets.sumOf { it.references.size }.toFloat() / assets.size
        
        // 孤立资源越少，平均引用越多，得分越高
        val orphanScore = (1 - orphanRatio) * 50
        val refScore = minOf(avgReferences / HEALTH_REF_AVERAGE, 1f) * 50
        
        return (orphanScore + refScore).toInt()
    }
}
