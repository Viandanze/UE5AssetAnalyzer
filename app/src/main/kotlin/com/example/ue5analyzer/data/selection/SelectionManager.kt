package com.example.ue5analyzer.data.selection

/**
 * Selection Manager
 * Responsible for asset multi-selection logic
 */
class SelectionManager {
    
    private val selectedIds = mutableSetOf<String>()
    
    /**
     * Get Selected ID List
     */
    fun getSelectedIds(): List<String> = selectedIds.toList()
    
    /**
     * Check if Selected
     */
    fun isSelected(id: String): Boolean = selectedIds.contains(id)
    
    /**
     * Toggle Selection State
     */
    fun toggle(id: String) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
    }
    
    /**
     * Select All
     */
    fun selectAll(ids: List<String>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
    }
    
    /**
     * Clear All Selection
     */
    fun clear() {
        selectedIds.clear()
    }
    
    /**
     * Selection Count
     */
    fun count(): Int = selectedIds.size
    
    /**
     * Is Empty
     */
    fun isEmpty(): Boolean = selectedIds.isEmpty()
}
