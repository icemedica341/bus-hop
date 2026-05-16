<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png" alt="icemedica341" width="96" height="96">
  <h1>icemedica341</h1>
  <p><strong>Lightweight Singapore bus timing app</strong></p>
  <p>Material 3 Compose UI with real-time arrivals, pinning, and smart search.</p>
  <!-- Badges -->
  <p>
    <img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF?logo=kotlin&logoColor=white">
    <img src="https://img.shields.io/badge/Compose-BOM%202024-4285F4?logo=jetpackcompose&logoColor=white">
    <img src="https://img.shields.io/badge/minSdk-24-34A853">
    <img src="https://img.shields.io/badge/targetSdk-34-34A853">
    <img src="https://img.shields.io/badge/license-MIT-yellow">
    <img src="https://img.shields.io/badge/build-115%20tasks%20%F0%9F%94%8A-34A853">
  </p>
</div>

---

## Features

| | Feature | Detail |
|---|---------|--------|
| 🚌 | **Real-time arrivals** | Shows next 3 buses per service with minutes-to-arrival |
| 🏷️ | **Operator badges** | SBS, SMRT, TTS, Go-Ahead colour-coded |
| 🚍 | **Bus type icons** | Single Decker, Double Decker, Bendy |
| 💺 | **Load indicator** | Seats Available / Standing Available / Limited Standing |
| ♿ | **Wheelchair info** | Wheelchair Accessible Bus (WAB) indicator |
| 📌 | **Pin stops & services** | Pin stops to the top; pin specific bus services within a stop |
| 🔍 | **Smart search** | Type-tolerant tokenized search with Levenshtein fuzzy matching |
| 📍 | **Nearby stops** | Optional location-based nearby stop finder (opt-in via Settings) |
| 🌙 | **Theme support** | Light, Dark, System-following — persisted across restarts |
| 🔄 | **Auto-refresh** | Configurable interval (30s / 1m / 2m / 5m / Off) — pauses in background |
| ↘️ | **Pull to refresh** | Swipe down to refresh all stops |
| 🖱️ | **Drag to reorder** | Long-press and drag bus stops to reorder them |
| 🔒 | **Privacy first** | Location is opt-in only. No accounts, no analytics, no telemetry |
| 📱 | **Material 3** | Modern Compose UI with animations, pull-to-refresh, edge-to-edge |

## Download

> **Latest release:** [v0.7.9](https://github.com/icemedica341/icemedica341/releases/latest) — `bus-hop.apk` (17 MB)

Or build from source (see below).

## Architecture

```
┌─────────────────────────────────────────────┐
│                  App Module                  │
│  ┌─────────┐ ┌──────────┐ ┌──────────────┐  │
│  │  UI     │ │ ViewModel│ │  Components   │  │
│  │(Compose)│◄┤ (State)  │◄┤ (Theme/Dialogs)│  │
│  └─────────┘ └──────────┘ └──────────────┘  │
├─────────────────────────────────────────────┤
│               Data Module                    │
│  ┌──────────┐ ┌──────────┐ ┌─────────────┐  │
│  │  API     │ │  Local   │ │ Repository   │  │
│  │(Retrofit)│ │(DataStore)│ │  Impl       │  │
│  └──────────┘ └──────────┘ └─────────────┘  │
├─────────────────────────────────────────────┤
│              Domain Module                   │
│  ┌──────────┐ ┌──────────┐ ┌─────────────┐  │
│  │  Models  │ │ UseCases │ │ Repository   │  │
│  │          │ │          │ │  Interface   │  │
│  └──────────┘ └──────────┘ └─────────────┘  │
└─────────────────────────────────────────────┘
```

- **domain/** — Pure Kotlin (zero framework deps). Models, use cases, repository interface.
- **data/** — Android module. Retrofit API calls, DataStore persistence, repository implementation.
- **app/** — Android module. Jetpack Compose UI, ViewModels, theme, components.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 1.9 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture (3 modules) |
| Networking | Retrofit 2 + OkHttp |
| Serialization | Gson (data layer only) |
| Persistence | DataStore Preferences |
| Async | Kotlin Coroutines + Flow |
| DI | Manual constructor injection |
| Testing | JUnit 4, MockK, Coroutines Test |
| Minification | R8 + ProGuard (release builds) |
| Target | Android 14 (SDK 34), min SDK 24 |

## Build from Source

### Prerequisites

- **JDK 17** (OpenJDK)
- **Android SDK 34** with build tools 34.0.0
- Set `ANDROID_HOME` to your SDK path

### Commands

```bash
# Debug build + tests + APK verification
./gradlew clean test checkAndRenameDebugApk

# Release build
./gradlew assembleRelease

# APK output at:
# app/build/outputs/apk/debug/bus-hop.apk
```

## Automated Checks

| Check | When | Where |
|-------|------|-------|
| APK integrity | Every `./gradlew assembleDebug` | `app/build.gradle.kts` — `checkAndRenameDebugApk` |
| Lint + Tests + APK | Every `git push` | `.github/workflows/ci.yml` |
| Full local check | `bash scripts/check.sh` | Runs lint → tests → APK verify |

## API

BusHop uses the [Arrivelah](https://github.com/cheeaun/arrivelah) API (`arrivelah2.busrouter.sg`), which proxies LTA DataMall's BusArrivalv2 endpoint. No API key required.

The app also includes a data source for the official LTA DataMall API (API key required).

## Privacy

| Data | Collected? |
|------|-----------|
| Location | 🔘 — opt-in via Settings, never sent off-device |
| Personal info | ❌ — no accounts, no sign-in |
| Analytics | ❌ — no tracking SDKs |
| Crash reports | ❌ — not integrated |
| Saved stops | 🔒 — stored locally in DataStore |
| API calls | 🔒 — direct to BusRouter / LTA, no intermediary |

## License

MIT License — see [LICENSE](LICENSE).
