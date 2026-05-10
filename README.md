# UE5 Asset Analyzer

UE5 项目资源分析工具，Android 端。扫描 .uasset 文件，找孤立资源，理依赖关系，算项目健康度。

做这个的起因很简单——UE5 项目做大之后，资源管理就是一坨，哪些没人用、哪些互相绕、哪些纯占空间，手动排查根本不现实。所以写了个工具把这事儿自动化了。

## 功能

- 扫描 UE5 项目目录下的 .uasset 文件，解析文件头提取元信息
- 按类型分类资源（蓝图、静态网格、材质、贴图、音效等 16 种）
- 孤立资源检测：零引用的、单引用的，按风险等级标出来
- 依赖关系图分析：谁依赖谁，引用链有多深
- 循环依赖检测：找出互相引用的死循环
- 项目健康度评分：综合孤立率、循环依赖等算一个分数
- 历史扫描记录：Room 本地存储，下次打开还能看

## 技术栈

Kotlin / Jetpack Compose / Material3 / Room / Coroutines + Flow / Navigation Compose

架构走的 MVVM，数据层、业务层、UI 层分开。

## 项目结构

```
app/src/main/kotlin/com/example/ue5analyzer/
├── data/
│   ├── database/        # Room 数据库，ProjectEntity 持久化
│   └── parser/          # .uasset 二进制解析器
├── domain/
│   ├── analyzer/        # 依赖分析、健康度计算
│   └── report/          # Markdown 报告生成
├── model/               # 数据类定义
└── ui/
    ├── components/      # 通用图表组件（饼图、柱状图、环形进度条）
    ├── navigation/      # 导航路由定义
    ├── screens/         # 各页面 Composable
    ├── theme/           # Material3 主题
    └── viewmodel/       # ViewModel + StateFlow
```

## 开发环境

- Android Studio Hedgehog+
- AGP 8.2, Kotlin 1.9, Compose BOM 2023.10.01
- Target SDK 34, Min SDK 26

## License

MIT
