# ARCHITECTURE.md: Bus-Hop System Architecture

## Overview

BusHop is a lightweight Android bus arrival viewer for Singapore and Taipei. It follows MVVM + Clean Architecture with three Gradle modules enforcing strict layer separation.

**Status**: Archived per [ADR-004](adr/ADR-004-archival.md). This document reflects the final architecture at archival.

## C4 Context Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        BusHop App                               │
│                   (Android, Kotlin 2.4.0)                       │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    UI Layer (app/)                        │  │
│  │  Jetpack Compose + Material 3                            │  │
│  │                                                           │  │
│  │  ┌─────────────┐ ┌──────────────┐ ┌───────────────────┐  │  │
│  │  │ MainScreen   │ │ SearchDialog │ │ SettingsScreen    │  │  │
│  │  └──────┬──────┘ └──────┬───────┘ └─────────┬─────────┘  │  │
│  │         │               │                    │             │  │
│  │         └───────────────┴────────────────────┘             │  │
│  │                         │                                  │  │
│  │                    ┌────┴────┐                             │  │
│  │                    │ViewModel│                             │  │
│  │                    └────┬────┘                             │  │
│  └─────────────────────────┼─────────────────────────────────┘  │
│                            │                                    │
│  ┌─────────────────────────┼─────────────────────────────────┐  │
│  │              Domain Layer (domain/)                       │  │
│  │  Pure Kotlin — zero Android/framework dependencies        │  │
│  │                                                           │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌─────────────────┐   │  │
│  │  │ BusStopUseCase│ │ RefreshCoord │ │ AutoRefreshCtrl │   │  │
│  │  └──────┬───────┘ └──────┬───────┘ └────────┬────────┘   │  │
│  │         │                │                   │             │  │
│  │         └────────────────┴───────────────────┘             │  │
│  │                         │                                  │  │
│  │              ┌──────────┴──────────┐                       │  │
│  │              │  Repository (iface) │                       │  │
│  │              └──────────┬──────────┘                       │  │
│  └─────────────────────────┼─────────────────────────────────┘  │
│                            │                                    │
│  ┌─────────────────────────┼─────────────────────────────────┐  │
│  │               Data Layer (data/)                          │  │
│  │  Android library — Retrofit, DataStore, BusStopIndex      │  │
│  │                                                           │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌─────────────────┐   │  │
│  │  │ RetrofitAPI  │ │ DataStore    │ │ BusStopIndex    │   │  │
│  │  └──────┬───────┘ └──────────────┘ └────────┬────────┘   │  │
│  │         │                                    │             │  │
│  └─────────┼────────────────────────────────────┼─────────────┘  │
│            │                                    │                 │
└────────────┼────────────────────────────────────┼─────────────────┘
             │                                    │
             ▼                                    ▼
    ┌─────────────────┐                 ┌─────────────────┐
    │   Arrivelah API │                 │   Local Storage │
    │ (external HTTP) │                 │   (DataStore)   │
    │                 │                 │                 │
    │ arrivelah2.     │                 │ Pinned stops    │
    │ busrouter.sg    │                 │ Sort order      │
    └─────────────────┘                 │ Theme prefs     │
                                        └─────────────────┘
```

## Module Map

| Module | Type | Package | Responsibility |
|--------|------|---------|---------------|
| **app/** | Android Application | `com.bushop.ui.*` | Jetpack Compose UI, ViewModels, feature flags, theme, components |
| **domain/** | Kotlin JVM Library | `com.bushop.domain.*` | Pure Kotlin models, use cases, repository interfaces |
| **data/** | Android Library | `com.bushop.data.*` | Retrofit API calls, DataStore persistence, BusStopIndex + TokenTrie |

### Dependency Direction

```
app/ → domain/
app/ → data/
data/ → domain/
domain/ → (nothing — pure Kotlin)
```

Domain has zero framework dependencies. Data depends only on domain interfaces. App depends on both.

## Key Components

### UI Layer (app/)

- **MainViewModel**: Central state management for stop list, arrivals, refresh, pinning, drag-to-reorder
- **ThemeManager**: Light/Dark/System theme with Blue and Contrast Blue colour schemes
- **UpdateChecker**: In-app GitHub Releases update checker
- **FeatureFlag**: Runtime toggleable flags (NEW_BUS_TIMELINE, NEARBY_STOPS_V2, PINNED_REORDER)

### Domain Layer (domain/)

- **BusStopUseCase**: Sorting, pinning, collapse logic
- **RefreshCoordinator**: Cooldown management, concurrent batch refresh
- **AutoRefreshController**: Timer-based auto-refresh lifecycle
- **Models**: BusStop, Arrivals, DisplayArrival (immutable data classes)

### Data Layer (data/)

- **BusStopIndex**: Inverted index + TokenTrie for O(k) prefix search + Levenshtein fuzzy matching across 5,201 stops
- **RetrofitAPI**: Arrivelah HTTP client (arrivelah2.busrouter.sg)
- **DataStore**: Pinned stops, sort order, theme preferences, feature flag overrides
- **RetryUtil**: Exponential backoff retry for API calls

## Fitness Functions

These are enforced by `scripts/check-local.sh` and architecture tests:

| Fitness Function | Enforcement | Threshold |
|-----------------|-------------|-----------|
| File size | `check-local.sh` (§5 Code Fitness) | ≤ 300 LOC per `.kt` file |
| Code style | `./gradlew ktlintCheck` | Pass/fail |
| Static analysis | `./gradlew detekt` | Pass/fail |
| Unit tests | `./gradlew testDebugUnitTest` | All 161 pass |
| Feature anchors | `check-local.sh` (§6) | ≥ 8 F-### anchors in docs/FEATURES.md |
| Requirements coverage | `check-local.sh` (§6) | ≥ 15 FRs in SPECIFICATION.md |
| Layer separation | `ArchitectureTest.kt` | domain/ has no Android imports |
| Module dependencies | `ArchitectureTest.kt` | No circular deps, domain is pure |

## Local vs GitHub Quality Gates

| Gate | Local (`check-local.sh`) | GitHub CI |
|------|-------------------------|-----------|
| ktlint | ✅ via `./gradlew ktlintCheck` | ❌ Removed (ADR-004) |
| detekt | ✅ via `./gradlew detekt` | ❌ Removed (ADR-004) |
| Unit tests | ✅ via `./gradlew testDebugUnitTest` | ❌ Removed (ADR-004) |
| Gitleaks | ✅ via `gitleaks detect` | ❌ Removed (ADR-004) |
| File LOC | ✅ max file ≤ 300 LOC | ❌ Removed (ADR-004) |
| Feature anchors | ✅ ≥ 8 F-###, ≥ 15 FRs | ❌ Removed (ADR-004) |
| Docs check | ✅ README/CHANGELOG/FEATURES/SPEC/TECH_DEBT/ADR-004 | ❌ Removed (ADR-004) |

All CI/CD was intentionally removed per [ADR-004](adr/ADR-004-archival.md). The project operates in maintenance mode with local quality gates only.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.4.0 |
| UI | Jetpack Compose (BOM 2026.05.01) + Material 3 |
| Architecture | MVVM + Clean Architecture (3 modules) |
| Networking | Retrofit 3 + OkHttp 5 |
| Serialization | Gson (data layer only) |
| Persistence | DataStore Preferences |
| Async | Kotlin Coroutines 1.11 + Flow |
| DI | Manual constructor injection through ViewModel Factory |
| Search | Inverted index + TokenTrie (prefix) + Levenshtein (fuzzy) |
| Testing | JUnit 4, MockK, Coroutines Test |
| Minification | R8 + ProGuard (release builds) |
| Gradle | 9.4.1, AGP 9.1.0 |

## References

- [FEATURES.md](FEATURES.md) — Feature inventory with F-### anchors
- [SPECIFICATION.md](../SPECIFICATION.md) — Functional + non-functional requirements
- [TECH_DEBT_AUDIT.md](../TECH_DEBT_AUDIT.md) — Technical debt status
- [ADR-004](adr/ADR-004-archival.md) — Archival decision record
- [scripts/check-local.sh](../scripts/check-local.sh) — Local quality gates
