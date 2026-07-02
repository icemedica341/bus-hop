# BusHop Perfect Development Plan

**Recursive specification-driven framework**: Seed Ambition → Full Interrogation → Macro Plan → Micro Plans → Dependency Matrix → Standards Registry.

*Plan version: 1.0 · Scope: v0.1.0 through v1.0.x*

---

## Layer 1 — Seed Ambition

### The initial thought

> *"I want to build a lightweight Singapore bus timing app for Android — real-time arrivals, no ads, no accounts, no tracking. I want it to be fast, private, and look good (Material 3)."*

### Constraints (implicit from the start)
- **Android only** — no iOS, no desktop
- **No API key** — must consume a public/proxied bus arrival API
- **No accounts** — zero sign-in, zero telemetry, zero analytics
- **Singapore-only** — LTA DataMall data via Arrivelah proxy
- **Low APK size** — lightweight, no unnecessary dependencies
- **Open source** — MIT license, public GitHub

### The ambition diagram

```
Singapore Bus App
├── Fast & offline-resilient arrival data
│   ├── Cached responses with TTL + stale-while-revalidate
│   ├── Auto-refresh with configurable interval
│   └── Pull-to-refresh for manual demand
├── Search 5,201 stops instantly
│   ├── TokenTrie prefix O(k) lookup
│   ├── Levenshtein fuzzy fallback
│   └── Weighted ranking (name > code > substring)
├── Organize stops visually
│   ├── Pin/unpin (persist across restarts)
│   ├── Drag-to-reorder with haptic + animation
│   ├── Drag-to-delete with undo snackbar
│   └── Pin individual bus services within a stop
├── Clean, modern UI
│   ├── Material 3 + Dynamic Color
│   ├── Light / Dark / System themes
│   ├── Blue + Contrast Blue color schemes
│   └── Edge-to-edge, translucent top bar
└── Privacy-first
    ├── Location opt-in only
    ├── All data stored locally (DataStore)
    └── No tracking SDKs, no crash reporting
```

---

## Layer 2 — Full Interrogation

*These 11 questions **should have been asked and answered before line 1 of code was written**. Each prevented rework when answered late.*

| # | Question | Answer (the correct one from Day 1) | Why it matters |
|---|----------|--------------------------------------|----------------|
| 1 | **API source — official LTA DataMall or proxied?** | Use **Arrivelah proxy** (`arrivelah2.busrouter.sg`) — keyless, no rate limits, no registration. The official LTA DataMall v1 requires an API key and has per-key quotas that break open-source distribution. (Note: you tried both — Arrivelah → LTA → Arrivelah — wasted effort proving what research would have settled.) | **First-order decision.** Wrong choice = API key management, account system, or blocked users. The LTA detour cost multiple commits. |
| 2 | **Architecture — single module or multi-module?** | **3-module Clean Architecture from Day 1** (`:domain` pure Kotlin, `:data` Android library, `:app` Compose UI). The monolithic app → module split in v0.5.0 caused test rewrites, ProGuard reconfiguration, and dependency refactoring. | **Structural.** Starting monolithic means redoing the entire build system mid-project. Multi-module from Day 1 costs ~30 min extra setup and saves days of refactoring. |
| 3 | **DI framework — Hilt/Koin or manual?** | **Manual constructor injection** through ViewModel factories. This is a single-dev app with 3 modules — Hilt's annotation processing adds 2+ min to every build and obscures dependency wiring. Manual DI is explicit, build-time free, and trivially testable. | **Build speed + clarity.** 30+ gradle invocations per day × 2 min overhead = ~1 hour/day lost with Hilt. Manual DI is the correct choice for solo projects. |
| 4 | **State management — LiveData, StateFlow, or RX?** | **StateFlow + Flow** (Kotlin Coroutines). LiveData is Android-lifecycle-coupled (hard to unit test). RX adds 3K+ methods to the DEX count. StateFlow + `stateIn(scope)` is lifecycle-safe, testable without Android dependencies, and fully coroutine-integrated. | **Testability + performance.** StateFlow + `distinctUntilChanged()` prevents redundant recomposition and works without Android instrumentation. |
| 5 | **Search engine — SQLite FTS, Trie, or linear scan?** | **Inverted index + TokenTrie (prefix) + Levenshtein (fuzzy)** — built entirely in-memory at startup. The initial linear `O(n)` scan worked but showed latency on 5,201 stops. The inverted index (`Map<token, List<stopCode>>`) makes search `O(k)` where k = query tokens. | **UX critical.** Search is the second-most-used interaction after viewing arrivals. Sub-100ms response is table stakes. The inverted index pays for itself on first use. |
| 6 | **Persistence — Room, DataStore, or SharedPreferences?** | **DataStore Preferences** for user settings + pin state. Room is overkill (no relational data, no migrations needed). SharedPreferences has known ANR risks on main-thread reads. DataStore gives async coroutine-based reads with built-in flow observation. | **ANR prevention.** The FeatureFlags ANR in v1.0.4 was caused by SharedPreferences synchronous reads on main thread — exactly the class of bug DataStore eliminates. |
| 7 | **Theme strategy — Dynamic Color vs fixed palette?** | **Classic Blue (fixed) + Dynamic Color (opt-in) + Contrast Blue (accessibility).** Dynamic Color alone is inconsistent (tested — white-on-yellow readability failures). A fixed primary palette guarantees consistent contrast; Dynamic Color is offered as an alternative for users who prefer it. | **Accessibility.** Dynamic Color looked great in demos but failed in real-world wallpapers. Having a fixed fallback is non-negotiable. |
| 8 | **Auto-refresh strategy — polling, push, or pull?** | **Polling with configurable interval (30s / 1m / 2m / 5m / Off) + stale-while-revalidate.** Push (Firebase FCM) requires a server. Server-sent events are over-engineered for 5-minute bus arrival intervals. Configurable polling with cooldown mutex prevents rate-limit abuse while letting users choose their freshness/reliability trade-off. | **Battery + API limits.** Hard-coded 30s polling would drain battery. Cooldown mutex (v0.3.0) prevents rapid refreshes. Configurable interval lets users optimize for their usage pattern. |
| 9 | **Feature rollout — flag system or direct commits?** | **Feature flags backed by SharedPreferences (later DataStore) from Day 1.** In-progress features ship behind flags (default off) with a debug menu toggle (long-press version label). Enables gradual rollout, instant kill-switch, and no-branch development for experimental features. | **Risk management.** Flags let you commit half-finished features without breaking the release. The `NEW_BUS_TIMELINE` and `NEARBY_STOPS_V2` flags exist precisely for this. |
| 10 | **Testing pyramid — what to test at each layer?** | **Domain: pure unit tests (mock-free, 28+8+6+7=49). Data: integration-ish (API parsing, search index, retry — 45+6+6=57). App: ViewModel state tests (47). Architecture: layer-boundary tests (8).** Total ~161 tests. Integration/E2E (screenshot, UI smoke) are minimal because the UI is thin — most logic lives in ViewModel/domain. | **Coverage efficiency.** 161 tests caught regressions across 41 releases. Over-testing the UI (Compose screenshot tests) would add fragility without catching real bugs. |
| 11 | **Release pipeline — manual or automated?** | **Automated with release script + CI.** `scripts/release.sh` increments version, updates CHANGELOG, builds release APK, creates git tag. CI runs `release.yml` to publish GitHub Release with APK + auto-extracted CHANGELOG section. Users download via Obtainium pointing at GitHub Releases. | **Consistency.** Manual releases skip steps (verify, sign, tag, publish). A script guarantees every release is identical in process. 41 releases without a broken publish proves the automation works. |

---

## Layer 3 — Macro Plan (8 Phases)

Each phase has: **WHY** (why this phase exists and what problem it solves), **WHAT** (what it produces), **ACCEPTANCE** (how to know it's done), **STANDARDS** (which authoritative standards apply).

### Phase 0: Discovery & Foundation Research
**WHY**: BusHop cannot be built without understanding the API landscape, Android target requirements, and Singapore bus data structure. The LTA DataMall → Arrivelah API detour (discovered mid-project) cost multiple commits. This phase prevents that.
**WHAT**: API research, Android SDK requirements, data model discovery
**ACCEPTANCE**: Full API contract documented, target SDK chosen, stop data ~5,201 entries catalogued
**TIME**: 1-2 hours (research only, no code)
**INPUT**: Singapore bus data spec, Arrivelah API docs, Material 3 guidelines
**OUTPUT**: API contract document, data model spec, theme architecture decision

### Phase 1: Scaffold & Architecture
**WHY**: The entire project's structural integrity depends on getting the module layout, dependency direction, and build system right from Day 1. Multi-module separation was done in v0.5.0 — retrofitting cost 3 commits and test rewrites.
**WHAT**: 3-module Gradle project, version catalog, DI strategy, baseline architecture tests
**ACCEPTANCE**: `./gradlew test` passes, `./gradlew assembleDebug` produces valid APK, ArchitectureTest.kt has 8 rules that verify layer separation
**TIME**: 0.5-1 day
**OUTPUT**: settings.gradle.kts, build.gradle.kts per module, libs.versions.toml, ArchitectureTest.kt

### Phase 2: Core Domain
**WHY**: The domain layer (pure Kotlin, zero framework deps) is the heart of the app — it models bus stops, services, arrivals, and the use cases that transform API data into UI-ready state. Everything else (UI, networking) is detail.
**WHAT**: Domain models, use cases, repository interfaces, BusStopIndex (search), RefreshCoordinator, AutoRefreshController
**ACCEPTANCE**: All domain tests pass (49 tests), zero Android/network imports in `:domain`, all models are immutable data classes
**TIME**: 2-3 days
**OUTPUT**: domain/ module complete with models, use cases, repository interfaces, search engine

### Phase 3: Data Layer
**WHY**: The data layer bridges the domain's interfaces to real implementations — LTA DataMall API calls via Retrofit, stop persistence via DataStore, stop search index initialization. This is where network errors, caching, and data transformation live.
**WHAT**: Retrofit API client, NetworkResult sealed class, DataStore persistence, BusStopIndex implementation, UpdateChecker, RetryUtil
**ACCEPTANCE**: All data tests pass (57 tests), API calls parsed into domain models, cache TTL enforced, retry with backoff works
**TIME**: 2-3 days
**OUTPUT**: data/ module complete with API client, persistence, search implementation

### Phase 4: App Shell
**WHY**: The app module wires domain + data into a running Android application — ViewModels expose state to the UI, the DI root (composition) connects everything, feature flags provide rollout control.
**WHAT**: MainViewModel (stop list state), SearchViewModel, SettingsViewModel, ThemeManager, DI factory, FeatureFlag system
**ACCEPTANCE**: All app tests pass (47 tests), app launches on emulator, stops load from API, search works
**TIME**: 3-4 days
**OUTPUT**: app/ module complete, working APK with core functionality

### Phase 5: UI Polish
**WHY**: The app works but doesn't feel good. This phase adds Material 3 theming, animations, drag interaction, translucency, pill design, edge-to-edge, splash screen — the difference between a prototype and a production app.
**WHAT**: Theme system (Light/Dark/Blue/Contrast), drag-to-reorder/delete, pill cards, translucent top bar, pull-to-refresh, splash screen, auto-refresh UI, API health banner, offline indicator
**ACCEPTANCE**: All visual interactions feel smooth, theme switching works, drag reorder passes gesture testing, APK size < 2MB release
**TIME**: 5-7 days
**OUTPUT**: Polished app complete with all visual and interaction features

### Phase 6: CI & Infrastructure
**WHY**: Without automated CI, every change risks regressions and every release is manual. This phase bakes in build/test/lint automation, dependency scanning, secret scanning, coverage gates, CHANGELOG enforcement, and automated badge updates.
**WHAT**: GitHub Actions workflow (build.yml), gitleaks, dependabot, JaCoCo coverage gate, spotless + ktlint, APK verification, commit signing enforcement, release script
**ACCEPTANCE**: CI passes on every push, dependabot creates weekly PRs, JaCoCo gate enforces 60%, release.sh creates APK + tag in one command
**TIME**: 1-2 days
**OUTPUT**: .github/workflows/build.yml, .github/dependabot.yml, scripts/release.sh, scripts/check.sh

### Phase 7: Production & Polish
**WHY**: The final phase handles edge cases discovered in real use, performance optimization, accessibility hardening, and the release pipeline for distribution via GitHub Releases + Obtainium.
**WHAT**: ANR fixes (DataStore migration), security audit (ProGuard, cert pinning, URL validation), performance audit (distinctUntilChanged, lambda hoisting, search optimization), accessibility audit (WCAG contrast), in-app update, release v1.0.x
**ACCEPTANCE**: 161 tests pass, APK integrity verified, no ANR reports, security audit clean, release published
**TIME**: 2-3 days
**OUTPUT**: v1.0.x release, CHANGELOG complete, all badges current

---

## Phase Dependency Matrix

```
Phase 0 (Research)
   ↓
Phase 1 (Scaffold) ───────────────────────────────┐
   ↓                                                │
Phase 2 (Core Domain) ─────────────────────┐        │
   ↓                                         │        │
Phase 3 (Data Layer) ───────────────┐        │        │
   ↓                                   │        │        │
Phase 4 (App Shell) ───────────────────┼────────┼────────┤
   ↓                                   │        │        │
Phase 5 (UI Polish)                   │        │        │
   ↓                                   │        │        │
Phase 6 (CI & Infrastructure) ◄───────┼────────┼────────┘
   ↓                                   │        │
Phase 7 (Production & Polish) ◄────────┘        │
                                                │
  Parallel can start:                            │
  Phase 6 (CI) can start after Phase 1 ────────┘
  (Once the project compiles, CI can be tested)

Key parallelization:
- Phase 6 (CI) can begin as soon as Phase 1 produces a compiling `./gradlew assembleDebug` — it tests the container, not the code.
- Phase 7's security audit can run in parallel with Phase 5 once the API client is stable.
```

---

## Layer 4 — Micro Plans

*Each micro plan is a zero-qualms step tree for one macro phase. Every step has a why, what, and acceptance.*

### Phase 0 — Discovery & Foundation Research (Micro)

```
Step 0.1: API Research
  WHY: The API is the data source — wrong choice means mid-project migration
  WHAT: Research LTA DataMall v1, Arrivelah, and any other Singapore bus API
  ACCEPTANCE: Written comparison of all APIs with auth requirements, rate limits, data format
  DECISION: Arrivelah proxy (keyless, no quotas, open-source friendly)

Step 0.2: Data Model Discovery
  WHY: Must understand the API response shape before writing domain models
  WHAT: Fetch sample API responses, document all fields (BusStopCode, ServiceNo, EstimatedArrival, Load, Feature, etc.)
  ACCEPTANCE: Complete data dictionary with types and examples from LTA DataMall spec

Step 0.3: Android SDK & Target Research
  WHY: Target SDK choice affects available APIs, Play Store requirements, and minSdk backward compatibility
  WHAT: Determine minSdk (26 = Android 8.0 for 98% coverage), targetSdk (latest stable)
  ACCEPTANCE: Documented SDK choices with rationale

Step 0.4: Stop Data Inventory
  WHY: The full Singapore bus stop dataset (~5,201 stops) must be catalogued for search
  WHAT: Download/aggregate stop list from LTA DataMall, understand data structure
  ACCEPTANCE: Complete stop dataset with BusStopCode, RoadName, Description, Latitude, Longitude

Step 0.5: Theme & Design Research
  WHY: Material 3 theming requires understanding color schemes, Dynamic Color, accessibility
  WHAT: Research Material 3 color scheme generation, Dynamic Color behavior, WCAG contrast requirements
  ACCEPTANCE: Theme architecture decision documented (Classic Blue + Dynamic Color + Contrast Blue)
```

### Phase 1 — Scaffold & Architecture (Micro)

```
Step 1.1: Create Gradle Project
  WHY: Standard Android project structure with version catalog
  WHAT: settings.gradle.kts with 3 modules, build.gradle.kts per module, libs.versions.toml
  ACCEPTANCE: ./gradlew projects shows :app, :domain, :data

Step 1.2: Configure Version Catalog
  WHY: Single-point dependency management prevents version drift
  WHAT: libs.versions.toml with Compose BOM, Retrofit, OkHttp, Coroutines, Testing, Gradle plugins
  ACCEPTANCE: All module build.gradle.kts reference libs.versions.toml accessors

Step 1.3: Manual DI Framework
  WHY: Explicit dependency injection without annotation processing overhead
  WHAT: Factory interfaces/classes for ViewModel creation, ApiClient singleton pattern
  ACCEPTANCE: MainActivity creates ViewModel via factory with explicit dependency wiring

Step 1.4: Write Baseline ArchitectureTest
  WHY: Prevents dependency direction violations before they happen
  WHAT: 8 ArchUnit-style rules: domain has zero Android imports, data doesn't reference Compose, app is only Activity module
  ACCEPTANCE: ./gradlew test passes ArchitectureTest.kt

Step 1.5: Configure EditorConfig + Formatter
  WHY: Consistent code style from Day 1 prevents whitespace noise in diffs
  WHAT: .editorconfig with LF, UTF-8, 4-space indent; spotless + ktlint configured
  ACCEPTANCE: ./gradlew spotlessCheck passes

Step 1.6: Configure Detekt Static Analysis
  WHY: Catch code quality issues at build time
  WHAT: detekt.yml with style, complexity, and potential-bug rule sets, baseline file for known issues
  ACCEPTANCE: ./gradlew detekt passes with baseline
```

### Phase 2 — Core Domain (Micro)

```
Step 2.1: Domain Models
  WHY: Pure data structures that represent bus stop, service, and arrival concepts
  WHAT: BusStop, BusService, BusArrival data classes, BusStopUiState for UI presentation
  ACCEPTANCE: All models are data classes, zero annotations, zero Android imports

Step 2.2: NetworkResult Sealed Class
  WHY: Typed API response states eliminate null-check branches everywhere
  WHAT: sealed class NetworkResult<T> { Success, Error, Loading }
  ACCEPTANCE: Pattern-matched exhaustively at every call site

Step 2.3: Repository Interfaces
  WHY: Abstraction boundary between domain and data layers
  WHAT: BusRepository interface (30+ methods), BusArrivalDataSource, UpdateChecker
  ACCEPTANCE: Domain depends only on interfaces, not implementations

Step 2.4: BusStopIndex (Search Engine)
  WHY: Instant search over 5,201 stops requires efficient indexing
  WHAT: Inverted index (Map<token, List<stopCode>>), TokenTrie prefix lookup, Levenshtein fuzzy fallback
  ACCEPTANCE: 45 tests covering exact match, prefix, fuzzy, abbreviation, sorting, findNearby

Step 2.5: Use Cases
  WHY: Business logic that transforms API data into UI-ready state
  WHAT: sortServices (by ETA + pin status), applyPinning, toggleCollapsed, filterServices
  ACCEPTANCE: 28 tests for sort orders, pin interactions, collapse behavior

Step 2.6: RefreshCoordinator & AutoRefreshController
  WHY: Coordinate refresh cooldowns and auto-refresh lifecycle
  WHAT: StopRefreshCoordinator with per-stop independent cooldowns, AutoRefreshController with start/stop/restart
  ACCEPTANCE: 6 + 7 tests for cooldown mutual exclusion, concurrent batching, lifecycle states
```

### Phase 3 — Data Layer (Micro)

```
Step 3.1: Retrofit API Client
  WHY: HTTP client for LTA DataMall / Arrivelah API calls
  WHAT: OkHttpClient singleton with timeouts, interceptors, logging; Retrofit instance with Gson converter
  ACCEPTANCE: ApiClientTest verifies client creation, configuration, and timeouts

Step 3.2: API Service Interface
  WHY: Retrofit service interface mapping API endpoints to Kotlin functions
  WHAT: BusArrivalApi interface with @GET methods for bus arrival by stop code
  ACCEPTANCE: Interface methods match Arrivelah API contract

Step 3.3: DTOs + Mappers
  WHY: API JSON shapes differ from domain models — must map at the boundary
  WHAT: API response DTOs (ApiBusArrivalResponse, ApiService, ApiBusStop), mapper functions to domain models
  ACCEPTANCE: All DTO fields mapped; unmapped fields explicitly named and documented

Step 3.4: DataStore Persistence
  WHY: User preferences and pinned stops survive app restart
  WHAT: DataStore<Preferences> with typed keys for theme, pinned stops, auto-refresh interval, feature flags
  ACCEPTANCE: DataStore read/write tested in isolation

Step 3.5: Repository Implementation
  WHY: Bridge between API/DataStore and domain repository interfaces
  WHAT: BusRepositoryImpl implementing BusRepository, with cache TTL (30s default), staleness tracking, stale-while-revalidate
  ACCEPTANCE: 0 data-layer tests reference domain models directly; all pass through repository interface

Step 3.6: RetryUtil
  WHY: Transient API failures must not crash the app
  WHAT: Retry with exponential backoff (1s, 2s, 4s, max 3), jitter, CancellationException propagation
  ACCEPTANCE: 6 tests for backoff timing, cancellation, max retries, jitter distribution

Step 3.7: UpdateChecker
  WHY: Users should know when a new version is available
  WHAT: GitHub Releases API parser, semantic version comparison, download URL construction
  ACCEPTANCE: 6 tests for API parsing, version comparison, error handling, download guard
```

### Phase 4 — App Shell (Micro)

```
Step 4.1: MainViewModel
  WHY: Central state holder for stop list, arrivals, pinning, refresh, errors
  WHAT: StateFlow<BusStopListUiState> with add/remove/move/pin/collapse/refresh/sort actions
  ACCEPTANCE: 47 tests covering every user interaction

Step 4.2: FeatureFlag System
  WHY: Gradual rollout and instant kill-switch without branch management
  WHAT: FeatureFlag enum with default values, debug menu toggle (long-press version label), SharedPreferences/DataStore backing
  ACCEPTANCE: FeatureFlag entries auto-discovered in debug dialog, flags default off

Step 4.3: ThemeManager
  WHY: Persistent theme selection across restarts
  WHAT: ThemeMode (Light/Dark/System), ColorScheme (Classic Blue/Dynamic/Contrast Blue), persisted in DataStore
  ACCEPTANCE: Theme switches without app restart, all 6 combinations render correctly

Step 4.4: UpdateManager
  WHY: In-app update download and install flow
  WHAT: GitHub Release check, DownloadManager integration, FileProvider content URI, install intent
  ACCEPTANCE: Download starts, progress notification shows, APK installs on tap

Step 4.5: DI Composition Root
  WHY: Wire all dependencies at the app level
  WHAT: MainActivity creates ViewModel via factory, provides ApiClient singleton, configures theme
  ACCEPTANCE: Application launches without DI-related crashes

Step 4.6: Navigation Shell
  WHY: Bottom navigation between stops list and settings
  WHAT: NavigationBar with Stops and Settings screens, NavHost with routes
  ACCEPTANCE: Both screens render, navigation works, state survives tab switch
```

### Phase 5 — UI Polish (Micro)

```
Step 5.1: Theme Implementation
  WHY: Apply Material 3 color schemes across all composables
  WHAT: MaterialTheme with Classic Blue palette, Dynamic Color integration, Contrast Blue accessibility variant
  ACCEPTANCE: All 6 theme combos (Light/Dark × Blue/Dynamic/Contrast) render without contrast issues

Step 5.2: BusStopCard Design
  WHY: The primary visual unit — needs to convey arrival info at a glance
  WHAT: Pill card design with header (operator badge + bus type icon), body (service list with ETA/load/WAB), collapse/expand
  ACCEPTANCE: Card passes visual QA on 320dp-480dp widths, all data renders without truncation

Step 5.3: Drag-to-Reorder & Delete
  WHY: Primary interaction for organizing stops — must feel fluid
  WHAT: detectDragGesturesAfterLongPress, animated item placement, delete zone with undo snackbar, haptic feedback
  ACCEPTANCE: Free-follow drag, items swap mid-gesture, delete zone threshold works, undo restores

Step 5.4: Search UI
  WHY: Second most-used interaction — must be instant and accurate
  WHAT: Search bar, results list with weighted ranking, fuzzy match indicators, random hint on open
  ACCEPTANCE: Search renders results in <16ms (O(k) lookup), fuzzy matches shown after exact results, hint shows on dialog open

Step 5.5: Pull-to-Refresh
  WHY: Standard Android refresh gesture
  WHAT: pullRefresh modifier, custom indicator composable, foreground spinner
  ACCEPTANCE: Pull down triggers refresh, spinner shows on top of content, refreshing state disables concurrent pulls

Step 5.6: API Health Banner
  WHY: Inform users when the data source is degraded
  WHAT: Banner monitoring sliding window of last 10 API calls, shows Operational/Degraded/Down states, auto-dismiss on recovery
  ACCEPTANCE: Banner transitions correct on consecutive failures, hides when API recovers

Step 5.7: Edge-to-Edge + Translucent Top Bar
  WHY: Modern Android look with content behind system bars
  WHAT: WindowInsets handling, Scaffold with top bar scroll behavior, alpha transition transparent→opaque
  ACCEPTANCE: Content renders behind transparent top bar, no double-padding, status bar icons visible on all backgrounds

Step 5.8: Splash Screen
  WHY: Branded cold-start experience
  WHAT: core-splashscreen library with branded icon, theme-aware background
  ACCEPTANCE: Splash displays on app cold start, transitions smoothly to main content

Step 5.9: Sort by Earliest Arrival
  WHY: Users want the soonest bus at the top
  WHAT: Toggle sort mode between manual order and earliest-arrival-first, persisted to DataStore
  ACCEPTANCE: Sort toggles correctly, order recalculates on data refresh, persists across restarts

Step 5.10: Offline Indicator
  WHY: Users should know when data is stale due to no connectivity
  WHAT: Cloud-off icon per stop, "No internet connection" label, separate visual state from API error
  ACCEPTANCE: Offline state renders distinctly from error/loading, clears automatically on reconnection
```

### Phase 6 — CI & Infrastructure (Micro)

```
Step 6.1: Build CI Workflow
  WHY: Every push must be verified
  WHAT: .github/workflows/build.yml with test, lint, detekt, spotlessCheck, assembleDebug, badge auto-update
  ACCEPTANCE: Workflow completes on push to main, all jobs pass

Step 6.2: Gitleaks Secret Scan
  WHY: No API keys or credentials in git history
  WHAT: Parallel CI job running gitleaks-action on every push/PR
  ACCEPTANCE: CI blocks on secrets found, passes on clean repo

Step 6.3: Dependabot Configuration
  WHY: Automated dependency vulnerability notifications
  WHAT: .github/dependabot.yml with weekly checks for Gradle + GitHub Actions
  ACCEPTANCE: Dependabot opens PRs for available updates

Step 6.4: JaCoCo Coverage Gate
  WHY: Code coverage must not regress without notice
  WHAT: JaCoCo 0.8.12 with 60% instruction coverage threshold, jacocoTestCoverageVerification in CI
  ACCEPTANCE: Build fails if coverage drops below 60%

Step 6.5: APK Integrity Verification
  WHY: Prevent shipping a corrupt APK
  WHAT: Custom Gradle task verifies APK file size, signing block, manifest integrity on every assemble
  ACCEPTANCE: Task fails on malformed APK, passes on valid build

Step 6.6: Release Script
  WHY: One-command releases prevent manual step omissions
  WHAT: scripts/release.sh increments version, updates CHANGELOG, builds release APK, creates git tag
  ACCEPTANCE: Script completes full release cycle without errors

Step 6.7: Commit Signing Enforcement
  WHY: Verifiable authorship
  WHAT: git config commit.gpgsign=true, tag.gpgSign=true with SSH key
  ACCEPTANCE: git log --format="%h %G?" shows G on every commit

Step 6.8: CHANGELOG Validation
  WHY: CHANGELOG must never be empty or forgotten
  WHAT: CI checks CHANGELOG.md is non-empty on PRs, warns if not modified
  ACCEPTANCE: Empty CHANGELOG fails CI, missing update warns
```

### Phase 7 — Production & Polish (Micro)

```
Step 7.1: ANR Prevention Audit
  WHY: ANRs cause app termination and negative reviews
  WHAT: Audit all main-thread operations — migrate SharedPreferences reads to DataStore, ensure IO operations on Dispatchers.IO
  ACCEPTANCE: No main-thread I/O in any code path reachable from UI

Step 7.2: Security Audit
  WHY: APK must not expose users to data interception or manipulation
  WHAT: Cert pinning (OkHttp CertificatePinner), URL validation (allowlist), GsonBuilder hardening, ProGuard rule review, manifest permission audit
  ACCEPTANCE: Security checklists pass, ProGuard rules verified, no over-permissive manifest entries

Step 7.3: ProGuard/R8 Optimization
  WHY: Minimize APK size for download and distribution
  WHAT: R8 minification, shrinkResources, ProGuard keep rules for ViewModel/Gson/enums
  ACCEPTANCE: Release APK < 2MB, all reflection-based serialization works

Step 7.4: Performance Optimization
  WHY: Smooth scrolling and instant interactions
  WHAT: structuralDistinctUntilChanged on all StateFlow collectors, stable lambda hoisting, search match token pre-allocation, color constant extraction
  ACCEPTANCE: Scroll through 20 stops at full speed without jank, search responds in <50ms

Step 7.5: Accessibility Audit
  WHY: App must be usable by visually impaired users
  WHAT: Contrast ratio check (WCAG AA 4.5:1 for text), contentDescription on all icons, focus order verification
  ACCEPTANCE: Contrast Blue scheme achieves WCAG AA, TalkBack navigates main flow without obstructions

Step 7.6: Feature Flag Migration (SharedPreferences → DataStore)
  WHY: Prevent ANR from main-thread SharedPreferences reads in feature flag checks
  WHAT: Migrate FeatureFlag backing from SharedPreferences to DataStore, async reads via Flow
  ACCEPTANCE: FeatureFlag reads never block main thread, all flag checks return within 1ms

Step 7.7: Release v1.0.0
  WHY: First stable milestone
  WHAT: run scripts/release.sh, verify APK, publish GitHub Release with auto-extracted CHANGELOG
  ACCEPTANCE: Release published, APK downloadable, CHANGELOG v1.0.0 section matches release content

Step 7.8: Post-release Monitoring
  WHY: Real-world usage may reveal edge cases
  WHAT: Monitor GitHub Issues, review Obtainium distribution analytics, address crash reports
  ACCEPTANCE: v1.0.1+ hotfixes as needed, CHANGELOG updated per release
```

---

## Layer 5 — Dependency Matrix

| Task | Depends On | Blocking | Parallel With |
|------|-----------|----------|---------------|
| Phase 0 (Research) | Nothing | Phase 1-7 | Everything |
| Phase 1 (Scaffold) | Phase 0 | Phase 2-4, 6 | Nothing |
| Phase 2 (Core Domain) | Phase 1 | Phase 3 | Nothing |
| Phase 3 (Data Layer) | Phase 1, 2 | Phase 4 | Nothing |
| Phase 4 (App Shell) | Phase 1-3 | Phase 5 | Nothing |
| Phase 5 (UI Polish) | Phase 4 | Nothing | Phase 6, 7 |
| Phase 6 (CI) | Phase 1 | Nothing | Phase 2-5, 7 |
| Phase 7 (Production) | Phase 3, 4 | Nothing | Phase 5, 6 |

### Critical path (longest chain)
```
Phase 0 → Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5
(Research → Scaffold → Domain → Data → App Shell → UI Polish)
= ~14-20 days total
```

Phase 6 and 7 can run in parallel with Phase 5 on the critical path, but Phase 6 needs Phase 1 (compiling project for CI to test) and Phase 7 needs Phase 3-4 (API client + app shell for security audit).

---

## Layer 6 — Standards Registry

### Applied Standards (Authoritative References)

| Standard | Type | Phases | Source | How Applied |
|----------|------|--------|--------|-------------|
| **Clean Architecture** | Architecture | 1-4 | Robert C. Martin | 3 modules with one-way dependency: app → domain + data → domain |
| **MVVM** | Architecture | 4-5 | Android Architecture Components | ViewModel + StateFlow, UI observes state, user actions call ViewModel methods |
| **Material 3** | Design | 5 | Google Material Design | MaterialTheme, color schemes, NavigationBar, pullRefresh, edge-to-edge |
| **WCAG 2.2 AA** | Accessibility | 5, 7 | W3C | 4.5:1 contrast ratio, contentDescription, focus order |
| **Semantic Versioning** | Release | 6-7 | semver.org | MAJOR.MINOR.PATCH for breaking features/features/bugfixes |
| **Keep a Changelog** | Documentation | 6-7 | keepachangelog.com | CHANGELOG.md format with Added/Changed/Fixed/Infrastructure/Security sections |
| **Type-safe Design** | Code | 2 | Kotlin idioms | NetworkResult<T> sealed class, immutable data classes, exhaustive `when` |
| **Parse-Don't-Validate** | Code | 3 | Alexis King (FP) | JSON parsed at API boundary into typed domain models, never raw JSON past boundary |
| **Stale-While-Revalidate** | Data | 3 | HTTP caching (RFC 5861) | Cache TTL 30s, stale data served immediately, background refresh in parallel |
| **Exponential Backoff** | Data | 3 | AWS / Google SRE | 1s, 2s, 4s, max 3 retries with jitter for transient API failures |
| **Cooldown Token Bucket** | Data | 3 | Rate limiting pattern | Mutex-based cooldown preventing API calls within COOLDOWN_MS window |
| **Feature Flags** | Dev | 4 | Martin Fowler (continuous delivery) | FeatureFlag enum with default-off, debug menu toggle, DataStore backing |
| **Conventional Commits** | Git | 6 | conventionalcommits.org | feat/fix/chore/docs prefix with scope, enforced on PRs |

### Standards NOT Applied (Deliberate Omissions)

| Standard | Why Skipped | Alternative |
|----------|-------------|-------------|
| **Hilt/Dagger DI** | Annotation processing adds 2+ min per build. Solo dev project. | Manual constructor injection |
| **Room Database** | No relational data, no migrations needed. Preference data only. | DataStore Preferences |
| **Compose Screenshot Tests** | UI is thin (most logic in ViewModel). Flaky across API levels. | ViewModel state tests (47) |
| **Firebase Crashlytics** | Privacy-first — no tracking SDKs. | No crash reporting (deliberate) |
| **AGP version catalog sync** | Single-app, single-developer. No multi-app version drift risk. | Direct version pinning |
| **Renovate** | Dependabot is sufficient for 3-module Gradle project. | Dependabot |

---

## Verification Chain

Every phase's acceptance criteria must be verifiable by the executing agent without human judgment:

| Phase | Auto-Verification |
|-------|-------------------|
| 0 | `docs/` exist with API spec, data model, theme decision |
| 1 | `./gradlew test && ./gradlew assembleDebug` passes |
| 2 | `:domain` has zero Android imports (grep check), all domain tests pass |
| 3 | `:data` tests pass, API returns parsed data, cache TTL enforced (verified with log output) |
| 4 | App launches on emulator, stops load from API, search returns results, tests pass |
| 5 | All visual interactions verified, APK < 2MB release, no visual regressions |
| 6 | CI workflow runs on push, all jobs green, release script creates valid output |
| 7 | Security audit checklist signed off, ANR-free, release published, CHANGELOG current |

Each micro step must leave a verifiable artifact (passing test, green CI, file on disk, screenshot). If a step cannot be verified without human eyes, it is not acceptance-complete.

---

*This plan assumes the agent executing it has access to: Android Studio / CLI build tools, Arrivelah API documentation, Material 3 design guidelines, and the full Singapore bus stop dataset. The plan is decision-complete — zero judgment calls remain for the executor beyond the choices documented in Layers 1-2.*
