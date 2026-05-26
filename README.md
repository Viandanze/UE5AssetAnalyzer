# UE5 Asset Analyzer

An Android tool for scanning and analyzing Unreal Engine 5 project assets. Parses `.uasset` files,
detects orphaned resources, maps dependency chains, and calculates project health scores.

Built because UE5 projects get messy fast — orphaned assets, circular dependencies, bloated content
directories — manual cleanup is impractical. This automates it.

## Requirements

- **Minimum Android version**: 8.0 (API 26)
- **Target Android version**: 14 (API 34)
- A UE5 project directory to scan

## Download

- **APK**: [app-debug.apk](apk/app-debug.apk)
  or [GitHub Release](https://github.com/Viandanze/UE5AssetAnalyzer/releases/tag/v1.0-release)

## Features

- **Project Scanning**: Scan UE5 project directories, parse `.uasset` file headers for metadata
- **Asset Classification**: 16 asset types (Blueprint, Static Mesh, Material, Texture, Sound, Level,
  etc.)
- **Orphan Detection**: Flag zero-reference and single-reference assets with risk levels (High /
  Medium / Low)
- **Dependency Analysis**: Who depends on whom, how deep the chain goes
- **Circular Dependency Detection**: Find mutually referencing loops
- **Project Health Score**: Composite metric from orphan rate, circular deps, reference depth, and
  more
- **3D Model Preview**: Import and preview `.obj` files with Three.js (WebGL), supports orbit
  controls and wireframe toggle
- **Scan History & Compare**: Persisted locally via Room, compare two scans to track
  added/removed/modified assets over time
- **Batch Selection & Export**: Multi-select assets and export as Markdown
- **Scan Settings**: Configure ignored directories, extensions, and file size limits
- **Theme Support**: System / Light / Dark mode, persisted preference
- **Report Generation**: Markdown reports with health scores, asset breakdown, and recommendations

## Screenshots

| Scan Screen                              | Asset Detail                                | 3D Preview                            | Scan History                                 |
|------------------------------------------|---------------------------------------------|---------------------------------------|----------------------------------------------|
| ![Scan Screen](docs/screenshot_scan.png) | ![Asset Detail](docs/screenshot_detail.png) | ![3D Preview](docs/screenshot_3d.png) | ![Scan History](docs/screenshot_history.png) |

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Architecture**: MVVM (data / domain / UI layers)
- **Database**: Room
- **Async**: Coroutines + Flow
- **Navigation**: Navigation Compose
- **Config**: DataStore
- **3D Preview**: Three.js + WebView (OBJLoader via JavascriptInterface bridge)
- **Network**: Ktor + Retrofit (for future cloud sync)

## Project Structure

```
app/src/main/kotlin/com/example/ue5analyzer/
├── data/
│   ├── database/        # Room database, entities, DAOs
│   ├── filter/          # Asset filtering logic
│   ├── manager/         # ScanConfigManager, ThemePreferencesManager
│   ├── parser/          # .uasset binary parser, UE project scanner
│   ├── repository/      # Asset & project repositories
│   └── selection/       # Batch selection state management
├── domain/
│   ├── analyzer/        # Dependency analysis, health scoring, orphan detection
│   └── report/          # Markdown & PDF report generation
├── model/               # Data classes, enums, ScanConfig
└── ui/
    ├── components/      # Reusable chart components (pie, bar, ring progress)
    ├── navigation/      # Navigation route definitions
    ├── screens/         # Screen composables (Scan, Detail, History, OBJ Preview, etc.)
    ├── theme/           # Material3 theme configuration
    └── viewmodel/       # ViewModel + StateFlow
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

The app uses Android's Storage Access Framework (SAF) to access project directories. Permissions are
automatically managed — previous folder permissions are released when scanning a new folder.

## Demo Video

[Demo Video (2 min)](https://www.bilibili.com/) <!-- Replace with actual link -->

## License

MIT
