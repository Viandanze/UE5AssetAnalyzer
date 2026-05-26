# Architecture

UE5 Asset Analyzer uses the **MVVM (Model-View-ViewModel)** architecture pattern with clear
separation between data, domain, and UI layers.

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                     UI Layer                        │
│                                                     │
│  Composable Screens ← StateFlow ← ViewModel        │
│  (ScanScreen, AssetDetail, HistoryScreen, etc.)     │
└──────────────────────────┬──────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│                   Domain Layer                      │
│                                                     │
│  AdvancedAssetAnalyzer  - Health scoring, metrics   │
│  OrphanDetector         - Orphan identification     │
│  ReportGenerator        - Markdown report creation  │
│  PdfExporter            - PDF export                │
│  AssetFilter            - Filter & sort logic       │
└──────────────────────────┬──────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│                    Data Layer                       │
│                                                     │
│  UEProjectParser    - .uasset binary parsing        │
│  UassetParser       - Low-level binary reader       │
│  ProjectRepository  - Project CRUD                  │
│  AssetRepository    - Asset CRUD                    │
│  ScanHistoryDao     - Scan history persistence      │
│  ScanConfigManager  - Scan config (DataStore)       │
│  ThemePrefManager   - Theme preference (DataStore)  │
└─────────────────────────────────────────────────────┘
```

## Data Flow

```
User selects directory (SAF)
        │
        ▼
ScanScreen → MainViewModel.scanProject(uri)
        │
        ▼
UEProjectParser.scanProject() ──→ ScanResult
        │                              │
        │                    ┌─────────┼──────────┐
        ▼                    ▼         ▼          ▼
  Room DB persist     applyFilters()  generateReport()  saveScanHistory()
        │                    │              │                │
        ▼                    ▼              ▼                ▼
  ProjectEntity       filteredAssets   AnalysisReport   ScanHistoryEntity
  AssetEntity         (StateFlow)      (StateFlow)      (Room)
        │
        ▼
  UI auto-refreshes via StateFlow collection
```

## Key Classes & Responsibilities

### ViewModel

| Class           | Responsibility                                                                                                                                                                           |
|-----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `MainViewModel` | Central state holder. Manages scan lifecycle, filters, sort, selection, history, and comparison. Exposes `StateFlow` for UI observation. Uses `Mutex` for thread-safe filter operations. |

### Data Layer

| Class                     | Responsibility                                                                                    |
|---------------------------|---------------------------------------------------------------------------------------------------|
| `UEProjectParser`         | Scans UE5 project directories via SAF, orchestrates `.uasset` parsing with progress callbacks     |
| `UassetParser`            | Low-level binary parser for `.uasset` file format — reads import/export tables, extracts metadata |
| `ProjectRepository`       | CRUD operations for project entities via Room DAO                                                 |
| `AssetRepository`         | CRUD operations for asset entities via Room DAO                                                   |
| `ScanConfigManager`       | Reads/writes scan configuration (ignored dirs/extensions) via DataStore                           |
| `ThemePreferencesManager` | Persists theme mode (System/Light/Dark) via DataStore                                             |

### Domain Layer

| Class                   | Responsibility                                                                                       |
|-------------------------|------------------------------------------------------------------------------------------------------|
| `AdvancedAssetAnalyzer` | Computes project health score, generates analysis reports with recommendations                       |
| `OrphanDetector`        | Identifies orphaned assets based on reference count, type, and risk heuristics                       |
| `ReportGenerator`       | Creates Markdown reports with health metrics, asset breakdown, and optimization suggestions          |
| `AssetFilter`           | Pure function-based filtering and sorting: search query, type, orphan status, risk level, sort order |

### UI Layer

| Screen              | Responsibility                                                              |
|---------------------|-----------------------------------------------------------------------------|
| `ScanScreen`        | Main screen: project picker, asset list, filters, sort, overflow menu       |
| `AssetDetailScreen` | Asset details: dependency graph, references, metadata                       |
| `HistoryScreen`     | Scan history list with A/B selection for comparison                         |
| `ObjPreviewScreen`  | 3D OBJ model viewer using Three.js via WebView + JavascriptInterface bridge |
| `ReportScreen`      | Rendered Markdown health report                                             |
| `StatsScreen`       | Project statistics with charts                                              |
| `ProjectListScreen` | Saved projects list                                                         |

### Model

| Class             | Responsibility                                                                            |
|-------------------|-------------------------------------------------------------------------------------------|
| `UEAsset`         | Core asset model with id, name, path, type, size, dependencies, references, orphan status |
| `ScanConfig`      | Configuration for scan behavior (ignored directories, extensions, size limits)            |
| `AssetType`       | Enum of 16 UE5 asset types                                                                |
| `OrphanRiskLevel` | Enum: HIGH / MEDIUM / LOW / NONE                                                          |

## Database (Room)

- **AppDatabase** (version 2): Provides DAOs for projects, assets, and scan history
- **Migration**: v1→v2 adds `scan_history` table
- Uses `fallbackToDestructiveMigration()` as safety net

## 3D Preview Architecture

```
ObjPreviewScreen (Compose)
    │
    ├── ObjDataBridge (named class, @JavascriptInterface)
    │       └── setData() / getObjData() — thread-safe OBJ text passing
    │
    ├── WebView
    │       ├── Loads obj_viewer.html from assets
    │       ├── addJavascriptInterface(bridge, "AndroidBridge")
    │       └── evaluateJavascript("loadObjFromBridge()")
    │
    └── obj_viewer.html (Three.js)
            ├── Scene / Camera / Renderer / OrbitControls
            ├── OBJLoader.parse() — parses OBJ text into 3D mesh
            ├── Auto-center & scale based on bounding box
            └── Wireframe toggle
```

**Why this approach**: The `addJavascriptInterface` bridge avoids the CORS restrictions of `file://`
URLs and the string length limits of `evaluateJavascript` with large OBJ files. Data flows through
JNI directly, no HTTP or base64 encoding needed.

## Concurrency Model

- **Coroutines**: All async operations (scanning, DB queries, filtering) run in `viewModelScope`
- **StateFlow**: UI state, filtered assets, scan progress, etc. are exposed as `StateFlow` for
  reactive observation
- **Mutex**: `applyFilters()` uses `Mutex.withLock` to prevent concurrent filter operations
- **Scan cancellation**: `scanJob?.cancel()` for user-initiated cancellation,
  `CancellationException` handled separately
