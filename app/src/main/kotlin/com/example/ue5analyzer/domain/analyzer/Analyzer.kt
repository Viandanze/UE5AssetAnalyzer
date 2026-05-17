package com.example.ue5analyzer.domain.analyzer

import com.example.ue5analyzer.model.*
import java.util.*

/**
 * Dependency graph analyzer
 */

// Magic number constants
private const val HEALTH_REF_AVERAGE = 5f  // Average reference count used in health score calculation

class DependencyAnalyzer {
    
    /**
     * Build Dependency Graph
     */
    fun buildDependencyGraph(assets: List<UEAsset>): Map<String, DependencyNode> {
        val graph = mutableMapOf<String, DependencyNode>()
        val assetMap = assets.associateBy { it.id }
        
        // BFS for calculating depth
        val visited = mutableSetOf<String>()
        val queue = LinkedList<Pair<String, Int>>()
        
        // Start from level assets
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
     * Find Circular Dependencies (deduplicated) - Use iterative DFS to avoid stack overflow
     */
    fun findCircularDependencies(assets: List<UEAsset>): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val assetMap = assets.associateBy { it.id }
        val visited = mutableSetOf<String>()
        
        // Traverse all nodes as starting points
        assets.forEach { startAsset ->
            if (startAsset.id in visited) return@forEach
            
            // Iterative DFS using explicit stack
            val stack = ArrayDeque<Pair<String, List<String>>>()
            stack.push(startAsset.id to emptyList())
            
            while (stack.isNotEmpty()) {
                val (currentId, path) = stack.pop()
                
                // If current node is already in current path, it means a cycle exists
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
                
                // Push dependencies to stack (in reverse to maintain original order)
                for (i in asset.dependencies.indices.reversed()) {
                    stack.push(asset.dependencies[i] to newPath)
                }
            }
        }
        
        // Deduplicate: Remove duplicate cycles by sorted node set
        val seen = mutableSetOf<String>()
        val uniqueCycles = mutableListOf<List<String>>()
        
        for (cycle in cycles) {
            // Get unique nodes in cycle (remove last duplicate), use sorted as key
            val key = cycle.dropLast(1).sorted().joinToString(",")
            if (key !in seen) {
                seen.add(key)
                uniqueCycles.add(cycle)
            }
        }
        
        return uniqueCycles
    }
    
    /**
     * Sort by references
     */
    fun sortByReferences(assets: List<UEAsset>): List<UEAsset> {
        return assets.sortedByDescending { it.references.size }
    }
    
    /**
     * Sort by size
     */
    fun sortBySize(assets: List<UEAsset>): List<UEAsset> {
        return assets.sortedByDescending { it.size }
    }
}

/**
 * Asset Analyzer
 */
class AssetAnalyzer {
    
    private val dependencyAnalyzer = DependencyAnalyzer()
    
    /**
     * Generate Analysis Report
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
        
        // Calculate health score
        val healthScore = calculateHealthScore(assets)
        
        // Calculate circular dependencies
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
     * Find Circular Dependencies (returns asset names) - Use iterative DFS to avoid stack overflow
     */
    private fun findCircularDependenciesNames(assets: List<UEAsset>): List<List<String>> {
        val assetMap = assets.associateBy { it.id }
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        
        // Traverse all nodes as starting points
        assets.forEach { startAsset ->
            if (startAsset.id in visited) return@forEach
            
            // Iterative DFS using explicit stack
            val stack = ArrayDeque<Pair<String, List<String>>>()
            stack.push(startAsset.id to emptyList())
            
            while (stack.isNotEmpty()) {
                val (currentId, path) = stack.pop()
                
                // If current node is already in current path, it means a cycle exists
                if (currentId in path) {
                    val cycleStart = path.indexOf(currentId)
                    if (cycleStart >= 0) {
                        // Convert IDs to names
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
                
                // Push dependencies to stack (in reverse to maintain original order)
                for (i in asset.dependencies.indices.reversed()) {
                    stack.push(asset.dependencies[i] to newPath)
                }
            }
        }
        
        // Deduplicate
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
     * Calculate Asset Health Score
     */
    fun calculateHealthScore(assets: List<UEAsset>): Int {
        if (assets.isEmpty()) return 100
        
        val orphanRatio = assets.count { it.isOrphan }.toFloat() / assets.size
        val avgReferences = assets.sumOf { it.references.size }.toFloat() / assets.size
        
        // Fewer orphan assets and more average references = higher score
        val orphanScore = (1 - orphanRatio) * 50
        val refScore = minOf(avgReferences / HEALTH_REF_AVERAGE, 1f) * 50
        
        return (orphanScore + refScore).toInt()
    }
}
