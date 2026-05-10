# UE5 Asset Analyzer

一款面向 UE5 开发者的 Android 资源分析工具，帮助快速扫描虚幻引擎项目，识别孤立资源、分析依赖关系、评估项目健康度。

## 主要功能

- 📁 扫描 UE5 项目 .uasset 文件
- 🏷️ 资源类型分类与统计
- ⚠️ 孤立资源检测与风险评估
- 🔗 依赖关系图分析
- 🔄 循环依赖检测
- 📊 项目健康度评分
- 📋 历史扫描记录管理

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose, Material3
- **架构**: MVVM + Clean Architecture
- **数据库**: Room
- **异步**: Coroutines / Flow
- **导航**: Navigation Compose

## 项目结构

```
UE5AssetAnalyzer/
├── app/                    # 应用模块
│   ├── src/main/
│   │   ├── java/com/ue5analyzer/
│   │   │   ├── data/       # 数据层
│   │   │   ├── domain/    # 业务逻辑层
│   │   │   └── ui/        # 界面层
│   │   └── res/           # 资源文件
│   └── build.gradle.kts
├── build/                  # Gradle 构建配置
├── gradle/                 # Gradle 包装器
└── settings.gradle.kts
```

## 开发环境

- Android Studio Hedgehog 或更高
- Android Gradle Plugin 8.2+
- Kotlin 1.9+
- Target SDK 34
- Min SDK 26
