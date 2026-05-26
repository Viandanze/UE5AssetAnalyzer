# UE5 Asset Analyzer

An Android application for scanning and analyzing Unreal Engine 5 project assets. Parses `.uasset` binary file headers, detects orphaned resources, maps dependency chains, generates health scores, previews 3D models, and exports PDF reports.

## Requirements

- **Minimum Android version**: 8.0 (API 26)
- **Target Android version**: 14 (API 34)
- A UE5 project directory to scan

## Download

- **APK**: [GitHub Release](https://github.com/Viandanze/UE5AssetAnalyzer/releases/tag/v1.0-release)

## Features

- **Project Scanning**: Scan UE5 project directories via SAF, parse `.uasset` file headers for metadata (supports 3 header variants)
- **Asset Classification**: 16+ asset types recognized — Blueprint, Static Mesh, Material, Texture, Sound, Level, and more
- **Orphan Detection**: Flag zero-reference and single-reference assets with risk levels (High / Medium / Low), with protected-type awareness
- **Dependency Analysis**: Full dependency chain mapping — who depends on whom, how deep it goes
- **Circular Dependency Detection**: Find mutually referencing loops automatically
- **Project Health Score**: Composite 100-point metric from 5 dimensions — orphan rate, circular deps, reference depth, asset distribution, and redundancy
- **3D Model Preview**: Import and preview `.obj` files with Three.js (WebGL) via WebView + JavascriptInterface bridge, supports orbit controls, zoom, and wireframe toggle
- **Scan History & Compare**: Persisted locally via Room database, compare two scans to track added/removed/modified assets with diff highlighting
- **PDF Report Export**: Generate and export A4-formatted PDF analysis reports (native Android PdfDocument API, no third-party dependencies)
- **Markdown Report**: Generate Markdown-formatted analysis reports with health scores and recommendations
- **Batch Selection & Export**: Multi-select assets and export as Markdown
- **Scan Settings**: Configure ignored directories, extensions, and file size limits
- **Theme Support**: System / Light / Dark mode, persisted via DataStore

## Screenshots

| Scan Screen                              | Asset Detail                                | 3D Preview                            | Scan History                                 |
|------------------------------------------|---------------------------------------------|---------------------------------------|----------------------------------------------|
| ![Scan Screen](docs/screenshot_scan.png) | ![Asset Detail](docs/screenshot_detail.png) | ![3D Preview](docs/screenshot_3d.png) | ![Scan History](docs/screenshot_history.png) |

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Architecture**: MVVM + Clean Architecture (data / domain / UI layers)
- **Database**: Room with migration support
- **Async**: Coroutines + Flow
- **Navigation**: Navigation Compose
- **Config**: DataStore
- **3D Preview**: Three.js + WebView (OBJLoader via `addJavascriptInterface` bridge)
- **PDF Export**: Native Android PdfDocument API
- **Network**: Retrofit (for future cloud sync)
- **Security**: Network security config, WebView cross-origin disabled, ProGuard code obfuscation

## Project Structure

```
app/src/main/
├── assets/
│   ├── js/                        # Three.js, OrbitControls, OBJLoader (local, no CDN)
│   └── obj_viewer.html            # 3D preview page
├── kotlin/com/example/ue5analyzer/
│   ├── MainActivity.kt            # Entry point
│   ├── data/
│   │   ├── database/              # Room database, ScanHistoryEntity, DAOs
│   │   ├── filter/                # Asset filtering logic
│   │   ├── manager/               # ScanConfigManager, ThemePreferencesManager
│   │   ├── parser/                # .uasset binary parser, UE project scanner
│   │   ├── repository/            # Asset & project repositories
│   │   └── selection/             # Batch selection state management
│   ├── domain/
│   │   ├── analyzer/              # AdvancedAssetAnalyzer, dependency analysis, orphan detection
│   │   └── report/                # PdfExporter, ReportGenerator
│   ├── model/                     # Data classes, enums, ScanConfig
│   ├── ui/
│   │   ├── components/            # Charts, DependencyGraph
│   │   ├── navigation/            # Navigation route definitions
│   │   ├── screens/               # Scan, Detail, History, OBJ Preview, Report, Stats, ProjectList
│   │   ├── theme/                 # Material3 theme configuration
│   │   └── viewmodel/             # MainViewModel + StateFlow
│   └── util/                      # FormatUtils
└── res/                           # Drawable, mipmap, values, xml configs
```

## Getting Started

### Build & Run

1. Clone the repository
   ```bash
   git clone https://github.com/Viandanze/UE5AssetAnalyzer.git
   ```
2. Open in Android Studio
3. Sync Gradle and run on device/emulator
4. Tap **Select Project** and choose a UE5 project folder

### Using the 3D Preview

1. Export your UE5 asset as `.obj` format
2. Push it to your device: `adb push model.obj /sdcard/Download/`
3. In the app, open **⋮ → 3D Model Preview**
4. Tap the **+** button and select your `.obj` file

## Configuration

### Scan Settings

Accessible from the **⋮** menu on the scan screen:

- **Ignored Directories**: Skip folders like `Intermediate`, `Saved`, `DerivedDataCache`
- **Ignored Extensions**: Skip file types like `uproject`, `png`, `jpg`

### SAF Permissions

The app uses Android's Storage Access Framework (SAF) to access project directories. Permissions are automatically managed — previous folder permissions are released when scanning a new folder.

## Demo Video

[Demo Video (2 min)](https://www.bilibili.com/) <!-- Replace with actual link -->

## License

MIT
