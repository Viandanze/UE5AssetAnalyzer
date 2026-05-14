# UE5 Asset Analyzer

An Android tool for scanning and analyzing Unreal Engine 5 project assets. Parses `.uasset` files, detects orphaned resources, maps dependency chains, and calculates project health scores.

Built because UE5 projects get messy fast — orphaned assets, circular dependencies, bloated content directories — manual cleanup is impractical. This automates it.

## Features

- Scan UE5 project directories, parse `.uasset` file headers for metadata
- Classify assets by type (Blueprint, Static Mesh, Material, Texture, Sound, etc. — 16 types)
- Orphan detection: flag zero-reference and single-reference assets by risk level
- Dependency graph: who depends on whom, how deep the chain goes
- Circular dependency detection: find mutually referencing loops
- Project health score: composite metric from orphan rate, circular deps, and more
- Scan history: persisted locally via Room, survives app restarts
- Custom scan rules: configure ignored directories/extensions, file size limits
- Dark mode: system / light / dark, persisted preference
- Markdown report export with enhanced rendering (code blocks, tables, nested lists)

## Tech Stack

Kotlin / Jetpack Compose / Material3 / Room / Coroutines + Flow / Navigation Compose / DataStore / Ktor

MVVM architecture — data, domain, and UI layers separated.

## Project Structure

```
app/src/main/kotlin/com/example/ue5analyzer/
├── data/
│   ├── database/        # Room database, ProjectEntity persistence
│   ├── filter/          # Asset filtering logic
│   ├── manager/         # ScanConfigManager, ThemePreferencesManager
│   ├── parser/          # .uasset binary parser
│   └── selection/       # Batch selection state management
├── domain/
│   ├── analyzer/        # Dependency analysis, health scoring
│   └── report/          # Markdown report generation
├── model/               # Data classes and enums
└── ui/
    ├── components/      # Reusable chart components (pie, bar, ring progress)
    ├── navigation/      # Navigation route definitions
    ├── screens/         # Screen composables
    ├── theme/           # Material3 theme
    └── viewmodel/       # ViewModel + StateFlow
```

## Development

- Android Studio Hedgehog+
- AGP 8.2, Kotlin 1.9, Compose BOM 2023.10.01
- Target SDK 34, Min SDK 26

## License

MIT
