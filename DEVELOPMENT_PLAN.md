# UE5 Asset Analyzer - 3天冲刺计划

## 项目目标

开发一个 Android 应用，扫描 UE5 项目，分析资源依赖关系，生成优化报告。

---

## Day 1：核心解析层（今天）

### 目标
完成项目扫描和资源解析的核心逻辑

### 任务清单

#### 上午（4小时）
- [ ] 配置开发环境
  - Android Studio Hedgehog 或更新版本
  - Kotlin 1.9+ / Compose 1.5+
  - Git 初始化

- [ ] 验证项目框架
  - 导入项目到 Android Studio
  - 解决依赖问题
  - 确保项目能编译运行

- [ ] 实现 UEProjectParser
  - 文件扫描逻辑（SAF）
  - 目录遍历
  - uasset 文件识别

#### 下午（4小时）
- [ ] 实现 uasset 解析
  - 解析二进制文件头
  - 提取 Import Table（依赖关系）
  - 处理不同版本的 UE 资源格式

- [ ] 数据模型完善
  - AssetType 枚举扩展
  - UEAsset 数据类
  - 数据库实体映射

- [ ] 单元测试
  - 测试文件扫描
  - 测试资源解析

#### 晚上（2小时）
- [ ] 调试与优化
  - 修复发现的 bug
  - 性能优化（大项目扫描）

---

## Day 2：业务逻辑层

### 目标
完成依赖分析和报告生成

### 任务清单

#### 上午（4小时）
- [ ] 实现 DependencyAnalyzer
  - 构建依赖图
  - 检测循环依赖
  - 计算依赖深度

- [ ] 实现孤立资源检测
  - BFS/DFS 遍历依赖图
  - 标记未引用资源
  - 特殊处理关卡资源

- [ ] 数据库集成
  - Room 数据库配置
  - DAO 实现
  - 数据持久化

#### 下午（4小时）
- [ ] 实现 ReportGenerator
  - Markdown 报告生成
  - 统计数据计算
  - 优化建议生成

- [ ] 实现 AssetAnalyzer
  - 健康度评分算法
  - 资源分类统计
  - 问题检测规则

- [ ] ViewModel 实现
  - MainViewModel
  - StateFlow 状态管理
  - 协程集成

#### 晚上（2小时）
- [ ] 数据流测试
  - 扫描 → 分析 → 存储 → 报告
  - 完整流程验证

---

## Day 3：UI层 + 整合

### 目标
完成 UI 界面和最终整合

### 任务清单

#### 上午（4小时）
- [ ] Compose UI 实现
  - MainScreen 主界面
  - 项目列表
  - 资源列表
  - 详情页面

- [ ] 搜索与筛选
  - 搜索栏
  - 类型筛选
  - 排序功能

- [ ] 可视化图表
  - 资源类型饼图
  - 大小分布柱状图
  - 依赖深度图

#### 下午（4小时）
- [ ] 报告导出
  - 导出 Markdown 文件
  - 分享功能
  - PDF 导出（可选）

- [ ] 设置页面
  - 扫描选项配置
  - 缓存管理
  - 关于页面

- [ ] UI 优化
  - 动画效果
  - 主题定制
  - 深色模式

#### 晚上（2小时）
- [ ] 最终测试
  - 真机测试
  - 大型项目测试
  - 边界情况处理

- [ ] 文档完善
  - README 更新
  - 使用说明
  - API 文档（可选）

---

## 核心技术点

### 1. uasset 文件解析

```kotlin
// UE5 uasset 文件结构
// Header (Magic: 0x9E2A83C1)
// Name Table
// Import Table (依赖的资源)
// Export Table (资源对象)
```

关键：
- 读取 Import Table 获取依赖关系
- 解析 Name Table 获取资源名称

### 2. SAF（Storage Access Framework）

```kotlin
// 使用 SAF 访问外部存储
val picker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocumentTree()
) { uri ->
    // 处理选择的目录
}
```

### 3. 依赖图算法

```kotlin
// BFS 构建依赖图
fun buildDependencyGraph(assets: List<UEAsset>): Map<String, DependencyNode> {
    // 从关卡资源开始
    // 遍历依赖链
    // 计算深度
}
```

---

## 预期成果

### 功能
- ✅ 扫描 UE5 项目目录
- ✅ 解析 uasset 文件
- ✅ 构建依赖关系图
- ✅ 检测孤立资源
- ✅ 生成分析报告
- ✅ 导出 Markdown

### 产出
- Android APK
- 源代码仓库
- 技术文档
- 演示视频（可选）

---

## 开发环境配置

### 必需软件
- Android Studio Hedgehog (2023.1.1) 或更新
- JDK 17
- Android SDK 34

### 可选工具
- Git
- Scrcpy（真机投屏调试）

### 测试设备
- Android 手机（API 26+）
- 或 Android 模拟器

---

## 开始开发

```bash
# 1. 打开 Android Studio
# 2. File → Open → 选择 UE5AssetAnalyzer 目录
# 3. 等待 Gradle Sync
# 4. 运行项目
```

---

现在开始 Day 1 的任务吧！🚀
