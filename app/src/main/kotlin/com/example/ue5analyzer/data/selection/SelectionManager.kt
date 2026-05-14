package com.example.ue5analyzer.data.selection

/**
 * 选择管理器
 * 负责资产的多选逻辑
 */
class SelectionManager {
    
    private val selectedIds = mutableSetOf<String>()
    
    /**
     * 获取已选择的ID列表
     */
    fun getSelectedIds(): List<String> = selectedIds.toList()
    
    /**
     * 检查是否已选择
     */
    fun isSelected(id: String): Boolean = selectedIds.contains(id)
    
    /**
     * 切换选择状态
     */
    fun toggle(id: String) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
    }
    
    /**
     * 全选
     */
    fun selectAll(ids: List<String>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
    }
    
    /**
     * 清空所有选择
     */
    fun clear() {
        selectedIds.clear()
    }
    
    /**
     * 选择数量
     */
    fun count(): Int = selectedIds.size
    
    /**
     * 是否为空
     */
    fun isEmpty(): Boolean = selectedIds.isEmpty()
}
