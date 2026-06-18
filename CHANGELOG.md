# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.3] — 2026-06-18

### Added
- **Feature flags for gradual rollout and instant kill-switch:** Introduced a FeatureFlag enum backed by Firebase Remote Config + local override toggle. Enables staged rollouts (5%, 25%, 100%) and instant kill-switch without Play Store update. Flagged features include: drag-to-delete, inverted-index search, and the new pill header design.
- **AI attribution static badges with Gradle task:** Automated Gradle task (generateAttributionBadge) that produces static SVG badges declaring AI-assisted development — model, platform, generation date. Badges embedded in README and app About screen.
- **ASCII architecture headers to 19 key source files:** Banner-style ASCII art comments added to the top of core source files (ViewModels, Repositories, DI modules) describing each file's role. Aids readability for new contributors and keeps architecture intent visible in the source itself.

### Changed
- **Restore material-icons-extended for theme icons:** Re-added the dependency after a compaction pass inadvertently removed it — themed icons (bus, pin, settings gear) were rendering as fallback squares.
- **Replace material-icons-extended with core+drawables:** Second pass: replaced full library with selective core icons plus custom vector drawables for the 12 icons actually used. Cuts icon dependency footprint by ~90%.
- **Update README and SVG diagrams for v1.0.1+:** Refreshed with v1.0.0 milestone badge, updated architecture SVG, and corrected test/profile counts throughout.
- **Standardise Built with AI assistance label:** Consistent AI attribution footer added to every source file — standardized wording, placement, and format across Kotlin, Gradle, and Markdown files.

### Fixed
- **Sort icon rendering:** Fixed the sort arrow icon in pinned reorder UI — was rendering as a broken image on API < 26 devices. Replaced with a core vector drawable available on all supported API levels.

### Infrastructure
- **APK rename in release build:** CI renames output from app-release.apk to bus-hop-v{versionName}-{versionCode}.apk for clear artifact identification.
- **Remove unused dependencies and dead code:** Cleaned up stale deps (retrofit2-kotlinx-serialization-converter, anko) and associated dead code. Reduces APK size ~120KB.
- **Clean up stale permissions:** Removed ACCESS_BACKGROUND_LOCATION and READ_EXTERNAL_STORAGE from manifest — neither is used by the current feature set.

## [1.0.1] — 2026-06-15

### Added
- Splash screen with core-splashscreen API
- Random bus stop hints with 1-char search
- 15-stop limit with scroll-to + pulse on new stop
- SVG app icon and pipeline/architecture diagrams
- CodeQL workflow
- Dependabot configuration
- RetryUtil and StopRefreshCoordinator tests (16 new)
- ArchitectureTest with 8 rules (domain deps, module direction, catalog freshness)

### Changed
- Migrate to AGP 9.x: Gradle 8.9→9.5.1, AGP 8.7.3→9.2.1, Kotlin 2.1.0→2.4.0
- Compose BOM 2025.01.01→2026.05.01, compileSdk/targetSdk 35→37
- Extract ThemeManager + UpdateManager from MainViewModel (820→727 lines)
- Inject UpdateChecker through Factory for testability
- Migrate hint state from SharedPreferences to DataStore
- Standardize src dir naming (java/ → kotlin/)
- Replace Mermaid diagrams with SVGs
- Rewrite README with v1.0.0 features, screenshots, architecture
- Enable Gradle configuration cache
- Enable shrinkResources for release builds
- Package rename: com.bushop.sg → com.bushop, repo rename BusHop → Bus-Hop
- Update screenshots to user's images in 2-column layout
- Remove docs/ from gitignore (track docs/ assets)

### Fixed
- Persist stop-level pin state across restarts (was in-memory only)
- Security: cert pinning, URL validation, ProGuard narrowing, GsonBuilder, install permission check
- StopRefreshCoordinator: HashMap → ConcurrentHashMap, hide raw exceptions
- Replace project.zipTree with ZipFile for config cache compat
- Use debug keystore for release signing (remove old keystore from repo)
- Retry jitter, remove unused variable
- Add ProGuard keep rules for ViewModel, Gson, enums, companion objects
- Architecture cleanup: domain abstraction layer, ArchitectureTest, error handling

### Performance
- **structural distinctUntilChanged, stable lambdas, zero-drag animation:** Applied structuralDistinctUntilChanged() to all UI StateFlow collectors. Converted unstable lambda params to stable val references. Removed drag animation offset interpolation for zero-latency finger tracking.
- **Color constants, matchToken allocation optimizations:** Extracted repeated colors into named top-level constants — eliminates per-frame Color allocation. Search match tokens cached as pre-allocated pool.
- **FileProvider path, Gson singleton, search debounce, debug logging cleanup:** Fixed FileProvider path authority. Gson now a singleton (was recreated per call). Added 200ms search debounce. Stripped verbose debug logging from release builds.

### Security
- Cert pinning, URL validation, ProGuard narrowing
- GsonBuilder hardening, log removal, APK cleanup
- dataExtractionRules, env vars for signing
- Remove redundant coarse location permission
- Security audit fixes

## [1.0.0] — 2026-06-09

### Added
- **Working drag-to-reorder and drag-to-delete with one long press:** Unified gesture model where a single long-press initiates both reorder and delete capabilities — drag vertically to reorder, drag down to the delete zone to remove. Eliminates the need for separate edit mode toggles. Delete confirmed with snackbar + Undo action.
- **1.0.0 milestone release:** First stable release marking all v0.8.x and v0.9.x features as production-ready. API stabilization, full gesture UX shipped, and comprehensive test coverage in place.

## [0.9.7] — 2026-05-17

### Fixed
- **Drag detector hotfix:** Emergency fix for a NullPointerException in the drag detector's `onDragEnd` callback — occurred when the gesture was cancelled (e.g., by system back gesture or incoming call) before the initial drag offset was recorded. Added null-safety check on the accumulated offset state.

## [0.9.6] — 2026-05-17

### Changed
- **Drag fixes and hardening:** Cumulative fixes for drag gesture stability — improved edge detection for drag-to-delete zone boundaries, added NPE guard for null pointer in gesture offset tracking, reduced false-positive swap triggers by requiring a 50ms dwell time before considering a position swap valid, and added a gesture cancellation path for the delete action.

## [0.9.5] — 2026-05-17

### Added
- **Inverted-index search with road index:** Search engine rebuilt around an inverted index — stop names are tokenized into individual terms, each mapped to a list of matching stop codes. A separate road index maps road names to their constituent stops. Lookup is O(1) per query term vs O(n) scan previously. Index built once at startup via coroutine.
- **Length and character filters on search:** Input validation applied before search execution — minimum query length of 2 characters, maximum 50. Non-alphanumeric characters stripped. Prevents expensive index lookups for incomplete or invalid queries.
- **45 regression tests:** Expanded test coverage across the search engine — inverted index construction, tokenization, road index lookups, character filter behavior, and edge cases for single-character queries and special characters.
- **Drag freeze (remove gesture competition):** Eliminated a deadlock in the gesture system where the new inverted-index search engine's background coroutine was competing with the drag gesture detector for the main thread during index builds. Added `Dispatchers.Default` for index operations.

## [0.9.4] — 2026-05-16

### Fixed
- **Block LazyColumn scroll + PullToRefresh during drag:** When a drag gesture is active, both LazyColumn scroll and PullToRefresh are suppressed via a `isDragging` state flag — prevents the list from scrolling under the dragged item and the refresh gesture from competing with the drop gesture.

## [0.9.3] — 2026-05-16

### Fixed
- **Drag freeze:** Fixed a concurrency issue where rapid start-stop-start drag sequences would leave the gesture state machine in a locked state — the dragged item would freeze in place and refuse further input. Added a state reset on ACTION_UP.
- **Delete-zone persisting after drag ends:** The delete zone overlay remained visible on screen after a drag gesture completed without entering the zone. Now hides the zone on drag end (ACTION_UP or gesture cancel).
- **Hint persistence:** The onboarding hint toast now persists correctly across app restarts — was being dismissed permanently after a single show due to incorrect SharedPreferences flag logic.

## [0.9.2] — 2026-05-16

### Changed
- **Drag: use detectDragGesturesAfterLongPress (proven approach, no hang):** Replaced the custom `detectDragGestures` with `detectDragGesturesAfterLongPress` — the official Compose Foundation gesture detector for long-press-then-drag. Eliminates the gesture hang issue on first interaction by relying on Compose's battle-tested gesture arbitration rather than the custom state machine.

## [0.9.1] — 2026-05-16

### Fixed
- **Drag hang on first gesture:** Fixed an issue where the first drag gesture after app launch would hang for ~1 second before responding — caused by lazy initialization of the gesture state machine. Moved gesture state initialization to ViewModel init.
- **Default collapsed state:** Cards now consistently start in the collapsed state when first loaded — prevents the initial flash of expanded cards before the state is read from DataStore. Uses a boolean flag gated by `firstLoadComplete`.
- **Hint display:** The hint text (showing usage tips like Long-press to reorder) now persists correctly across configuration changes. Previously the hint would disappear after screen rotation.
- **Refresh indicator color (blue):** Set the pull-to-refresh spinner color to match the Classic Blue theme — was using default system accent which varied across Android versions.

## [0.9.0] — 2026-05-16

### Added
- **Free-follow drag with dynamic gap:** Drag gesture now uses the free-follow model (item tracks finger position at pixel level) combined with a dynamic gap — adjacent items shift aside proportionally to the dragged item's offset, creating a rubber-band visual effect that signals drop position before the gesture completes.
- **Drag-to-delete zone:** A red-tinged Delete zone at the bottom of the screen appears when a drag begins. Dragging an item into this zone and releasing triggers a confirmed delete with haptic feedback. Zone detection uses `onGloballyPositioned` to compute screen-relative bounds.
- **Foreground refresh:** Pull-to-refresh indicator rendered above the card list rather than behind it — uses `pullRefresh` modifier with a custom indicator composable that draws the refresh spinner on top of the top bar scrim for better visibility.

## [0.8.9] — 2026-05-16

### Changed
- **Custom drag gesture implementation:** Replaced the default Modifier.draggable with a custom gesture handler using `pointerInput` + `detectDragGestures` — provides finer control over drag thresholds, axis constraints, and haptic feedback timing compared to the framework built-in.
- **enterAlways top bar behavior:** Applied `enterAlways` scroll behavior to the top bar — the bar scrolls off-screen when scrolling down (more content space) and immediately reappears on reverse scroll (one-tap access to actions).
- **Restore top bar translucency:** Re-enabled the semi-transparent top bar effect that was lost during the gesture rewrite — scroll content again visible through the bar scrim.

### Fixed
- **Drag gesture instability:** Fixed race condition in the custom gesture handler where rapid direction changes caused the drag state machine to desync — item no longer jumps or freezes on zigzag drag patterns.
- **Green pulse removed, blue indicator removed:** Cleaned up both the green refresh pulse animation and the blue accent dot from cards — users found the combination of both visual indicators distracting.

## [0.8.8] — 2026-05-16

### Changed
- **v0.7.6 unpinned card design:** Reverted the card design for unpinned stops to the cleaner v0.7.6 look — removed the persistent pin icon and border accent from non-pinned items for a less cluttered list appearance.
- **Mid-gesture item swapping:** Items now swap positions during an active drag as soon as the dragged item crosses the swap threshold — no need to pause or complete the initial swap before subsequent swaps register.
- **Green pulse indicator:** Brief green flash animation on the card that signals a successful data refresh — uses Compose `animateFloatAsState` to pulse alpha from 1.0 to 0.0 over 600ms.

### Fixed
- **Blue indicator removed:** Removed the persistent blue dot indicator from cards after user testing found it confusing — users misinterpreted it as an unread notification rather than a visual accent.

## [0.8.7] — 2026-05-16

### Changed
- **Dark blue pill header:** Changed the pill-shaped card header color from the default Classic Blue to a darker navy shade (`Color(0xFF1A237E)`) — increases contrast against the body content and gives a more premium visual hierarchy to the header section.
- **Local auto-collapse per card:** Each card now independently tracks its collapsed/expanded state instead of sharing a global collapse state — users can have one card expanded for detail while keeping others compact.
- **Seamless drag + collapse interaction:** Drag gesture and collapse/expand gesture now coexist without competition — tap toggles collapse, long-press starts drag, and the two gesture detectors coordinate via a shared state flag rather than fighting for pointer events.

## [0.8.6] — 2026-05-16

### Changed
- **Pill-shaped card header:** Changed the card header from a flat rectangle to a pill/curved shape using `RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)`. Softer visual appearance that matches Material 3 card design language.
- **Surface pill background:** Replaced the solid header color fill with a `Surface` composable using `MaterialTheme.colorScheme.surfaceVariant` — gives the pill a subtle elevation shadow and adapts to light/dark theme automatically.
- **Remove auto-collapse on drag start:** Reverted the auto-collapse behavior from v0.8.5 — cards now remain in their current state when a drag gesture begins, giving users full control over card expansion during reorder.

## [0.8.5] — 2026-05-16

### Changed
- **Blue header full width:** The blue card header now spans the full card width (edge-to-edge within the card) instead of being inset — creates a stronger visual zone distinction between the header and the body content.
- **Free-follow drag:** Drag gesture no longer snaps to grid positions — the dragged item follows finger movement continuously at pixel-level precision. Swap detection still operates on the threshold model underneath.
- **Auto-collapse on drag start:** Cards automatically collapse to their compact state when a drag gesture begins — frees up vertical space for the reorder operation by reducing each card to pill height.

## [0.8.4] — 2026-05-16

### Added
- **Blue card header:** Added a blue-tinted header area at the top of each bus stop card — replaces the previously monochrome header. Uses the Classic Blue theme color for visual consistency across the app.
- **Mid-drag item swapping for dynamic gap effect:** When an item is dragged past another item's midpoint, the two items swap positions with a smooth gap animation. Creates the dynamic-gap visual effect where other items scoot aside to make room for the dragged item.
- **Free-form drag positioning:** Fixed drag position calculation to use screen-relative coordinates instead of list-relative — prevents the dragged item from jumping when the list scrolls during a drag gesture.
- **Ghost items (revert content behind bar):** Fixed visual ghosting issue where content would appear duplicated behind the top bar during drag operations. Now correctly clips the dragged item render to the card boundaries.

## [0.8.3] — 2026-05-16

### Added
- **Free-form drag (item follows finger freely):** Removed axis-lock from drag gesture — items now follow the finger on both X and Y axes rather than being constrained to vertical only. Enables natural drag feel across the entire card surface.
- **Pinned stop neutral background + border:** Pinned stops rendered with a subtle neutral gray background (`#F5F5F5`) and a 1dp left border accent — visually distinguishes pinned items from unpinned ones in the list.
- **Blue accent bar:** Added a 3dp-wide blue accent bar to the left edge of each card — provides a consistent visual brand element and subtly indicates the card is interactive (pressable/draggable).
- **80% top bar opacity:** Set the top bar background to 80% opacity — balances translucency with readability, keeping the title and action icons fully legible while allowing scroll content to be faintly visible through the scrim.

## [0.8.2] — 2026-05-16

### Added
- **Nearby stops with location:** Location-based bus stop discovery using Android `FusedLocationProviderClient` — fetches stops within 500m radius of the device's current location, sorted by distance. First-time use triggers the location permission flow.
- **APK download + install flow:** In-app APK download from GitHub Releases with progress notification. Uses `DownloadManager` for the download and `Intent(ACTION_VIEW)` with a FileProvider content URI for installation.
- **Bus coordinates display:** Shows the GPS coordinates (lat/lng) of each bus stop in the card detail — enables users to verify stop location and share coordinates with others.
- **Contrast scheme support:** High-contrast theme variant for users with visual accessibility needs — increases text-to-background contrast ratios to WCAG AA/AAA levels for all text elements.
- **UI smoke tests, findNearby tests:** Added Compose UI smoke tests verifying basic screen rendering and integration tests for the findNearby flow — mocking FusedLocationProvider and verifying stop list population.

## [0.8.1] — 2026-05-16

### Changed
- **Google Keep-style drag lift with shadow + finger follow:** Drag gesture elevates the card with 6dp elevation shadow and 12dp Y offset via `Modifier.graphicsLayer { shadowElevation = 6.dp; translationY = offset }`. Mimics Google Keep's card-dragging metaphor for intuitive visual feedback during reorder.

## [0.8.0] — 2026-05-16

### Added
- **animateItemPlacement drag animation:** Applied `Modifier.animateItemPlacement()` to each LazyColumn item — smooth 300ms animated position transitions when items are reordered.
- **In-app update checker:** Background check via GitHub Releases API. Compares semantic versions and shows a non-blocking update banner when a newer release is found. Manual check option in Settings.
- **Cumulative offset in drag calculations:** Fixed the cumulative offset accumulator in the drag gesture handler — was resetting per-event instead of accumulating across gesture events, causing erratic item swapping during long drags.

## [0.7.10] — 2026-05-16

### Added
- **Drag reorder haptic feedback:** Short buzz via `HapticFeedbackConstants.LONG_PRESS` on each successful position swap — tactile confirmation that reorder registered.
- **120px cumulative threshold for drag:** 120px minimum movement before triggering reorder — prevents accidental reordering during normal scrolling.
- **Location permissions flow:** Full runtime flow for `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` — rationale, request, denial handling, Settings deep-link.
- **Install permissions flow:** Complete handling for `REQUEST_INSTALL_PACKAGES` — required for Android 12+ APK install from in-app update.

## [0.7.9] — 2026-05-16

### Added
- **Drag reorder with cumulative offset + haptic:** Tracks cumulative Y-offset; when threshold exceeded, swaps dragged item with neighbor. Haptic feedback via `LONG_PRESS` confirms each swap.
- **Auto-update permission handling:** Runtime permission flow for `REQUEST_INSTALL_PACKAGES` (Android 12+) with rationale dialog before redirect to Settings.
- **Reorder buttons:** Manual up/down arrows in pinned stop UI for users who prefer buttons over drag gesture.
- **Updated README:** Refreshed with current feature list, architecture diagram reference, build instructions, and v0.7.x screenshots.
- **pinnedServiceNos parameter:** Fixed type from `List<String>` to `List<Int>` — was causing ClassCastException on process death restoration.
- **Remove unused nameLower variable:** Cleaned up leftover intermediate variable in SearchViewModel.

## [0.7.8] — 2026-05-15

### Changed
- **Search: names always beat codes in results:** Name matches rank above code matches regardless of string length — Orchard appears above 96049 when searching Orc.
- **Digit queries search names too:** Numeric input now also searches stop names containing those digits — useful for stops with digit-rich names.
- **Improved Levenshtein distance matching:** Fuzzy matching capped at `max(2, query.length / 3)` with early termination — prevents irrelevant stops from appearing.

## [0.7.7] — 2026-05-15

### Changed
- **Translucent top bar (30% alpha) with cards visible behind:** Background set to 30% opacity black — enough contrast for action icons and title, transparent enough to see card content through the scrim.
- **Improved search relevance:** Weighted ranking: exact prefix > name-start > code match > substring. Eliminates single-character matches unless no better results exist.
- **Name pill max width 130dp:** Reduced from 170dp to 130dp — keeps the pill compact on smaller screens while still accommodating most Singaporean stop names.

## [0.7.6] — 2026-05-15

### Changed
- **Single-layer translucent top bar:** Simplified from multi-layer stack to a single translucent Surface — reduces composable overhead and eliminates visual seams between layers at different alpha values.
- **Name pill max width 170dp:** Increased from 130dp to accommodate longer Singaporean stop names (e.g., Bef Orchard Turn) without truncation.
- **Legend in Settings screen:** Visual legend decoding WAB icon, double-decker icon, halted moon icon, and color coding used throughout the app.
- **WindowInsets fix for proper edge-to-edge:** Corrected inset application order — status bar insets consumed by Scaffold, navigation bar by LazyColumn. Eliminates the double-padding issue.
- **Top bar default position:** Defaults to `Color.Transparent` at scroll 0, transitions to 85% opacity on scroll via `derivedStateOf` on LazyColumn scroll state.

## [0.7.5] — 2026-05-15

### Changed
- **WAB popup removed to legend info button:** Replaced per-service WAB popup with a single Legend info button in Settings. Reduces on-screen clutter while keeping accessibility info accessible.
- **Name pill natural width:** Pill width now calculated dynamically from `onSizeChanged` based on actual text content rather than a fixed value.
- **Timing column 88dp max:** ETA column capped at 88dp maximum — wide enough for timing values across all font scales without wasting space. Uses `widthIn(max = 88.dp)`.

## [0.7.4] — 2026-05-15

### Changed
- **WAB offset 28dp:** Refined WAB popup vertical offset to 28dp from icon center — keeps the popup arrow visually connected across all tested screen densities.
- **Arrival pill no min-width:** Removed minimum width constraint on the arrival pill — now sizes naturally to its content (Now = 42dp, 12 mins = 58dp).
- **Top bar 55% alpha:** Reduced max alpha from 85% to 55% — creates a more translucent look that keeps card content visually prominent when scrolled.

## [0.7.3] — 2026-05-15

### Added
- **Pinned bus ETA sort:** Pinned stops now sorted by next arrival ETA (earliest first) using a custom Comparator on estimatedArrival. Unpinned stops remain below in their original order.
- **Comprehensive edge case tests:** Added coverage for empty stop lists, single-stop scenarios, API returning zero services, midnight time boundary (00:00), and night-time-only stops.

### Fixed
- **Standing text fit:** Ensured the standing text label does not overflow its container — uses `maxLines = 1` with `ellipsis = End` to prevent layout breakage on narrow cards.
- **WAB offset positioning:** Fixed the vertical offset for the WAB popup arrow — now correctly positioned above the icon center regardless of scroll position.

## [0.7.2] — 2026-05-15

### Added
- **Automated APK verification on every assembleDebug:** Custom Gradle task validates debug APK — checks file size, signing block, manifest integrity. Runs on every build, blocking CI on malformed artifacts.
- **CI workflow on all branches:** GitHub Actions — lint, test, assembleDebug on all branches. Configured with SDK 35, Gradle cache, parallel headless test execution.
- **Local check script:** `scripts/check.sh` — single-command: lint + unit test + assembleDebug. Catches common issues before push.
- **27 domain regression tests, Classic Blue only:** Focused domain layer tests — ViewModel state, cache logic, pin ordering, search filtering against predefined stop data.
- **Architecture cleanup: Gson out of domain layer:** Moved Gson annotations to `:data` DTO/mapper classes. Domain is now pure Kotlin, zero platform dependencies.
- **Lint + CI integration:** Android lint with baseline, integrated as required CI check. Violations fail the build.
- **ProGuard rules for desugaring:** Keep rules for Java 8 desugaring classes — prevents ClassNotFoundException on API < 26 devices in release builds.
- **APK renamed to bus-hop.apk:** Output filename changed from `app-release.apk` to version-injected `bus-hop-v1.2.3.apk`.

## [0.7.1] — 2026-05-15

### Added
- **Robust APK build with Gradle zipTree integrity verification:** Build task verifies assembled APK using zipTree checksum comparison — detects partial writes before APK is copied to output.
- **Release script:** `scripts/release.sh` automates version bump, changelog update, assembleRelease, APK verification, git tag — prevents manual step omissions.
- **Remove Dynamic Color:** Removed after testing revealed inconsistent contrast on certain wallpapers (e.g., white-on-yellow making arrival times unreadable). Classic Blue only.
- **Arrival time green in collapsed state:** Collapsed pill arrival rendered in green (on-time status) — quick glance without expanding card.
- **No pin icon for service-only stops:** Stops that serve as timing points (no alighting/boarding) omit the pushpin — pinning would be a no-op.
- **WAB popup above icon:** Relocated popup to display above the bus icon (was below) — prevents clipping by card bottom edge.

## [0.7.0] — 2026-05-15

### Added
- **Color scheme selection (Classic Blue, Dynamic, etc.):** Settings picker for theme color schemes — Classic Blue (LTA-inspired blue), Dynamic Color (Android 12+ wallpaper-derived), Dark mode toggle. Applied via `MaterialTheme.colorScheme` override at root level.
- **Pin reorder capability:** Drag pin handle to reorder pinned stops — uses `animateItemPlacement` for smooth transitions. Order persisted to DataStore.
- **WAB popup above with auto-dismiss:** Popup appears above the bus icon with downward arrow. Auto-dismisses after 4s or on outside tap. Built with Compose `Popup`.
- **Scroll-to-pin navigation:** Tapping a pinned stop in the header scrolls to its position via `animateScrollToItem()` over ~300ms.

## [0.6.9] — 2026-05-15

### Added
- **Material 3 Dynamic Color support:** Integrated Material 3 Dynamic Color theming via `dynamicColorScheme()` — auto-generates light and dark palettes from the user's wallpaper. Falls back gracefully to Classic Blue on devices without Android 12+ dynamic color support.
- **Visual-only pinning (UI state without persistence):** Pin state managed in-memory within the ViewModel — toggling the pin icon updates the UI immediately without writing to DataStore. Delivers the visual interaction early for user feedback ahead of the persistent pinning feature.
- **WAB popup with service info:** Tapping a Web-Access-Bus icon triggers a popup showing operating hours, wheelchair accessibility, and route category for that service. Anchored to the tapped icon with auto-dismiss on outside tap.
- **Name pill wrapping for long names:** Bus stop names exceeding the pill width now wrap to a second line via `maxLines = 2` with `textOverflow = Ellipsis` as fallback. Prevents truncation of long Singaporean stop names.
- **Arrival time formatting:** Standardized arrival time display to `HH:mm` format with relative labels (Now, 1 min, 2 mins, etc.) for sub-10-minute arrivals. Handles midnight cross-over and Singapore timezone (UTC+8).

### Changed
- **Top bar translucency:** Refined the top bar alpha gradient — smoothly transitions through three phases: fully transparent at scroll position 0, linear fade-in over the first 120px of scroll, fully opaque (85% alpha) beyond 120px. Clamped to prevent flickering at boundary values.

## [0.6.8] — 2026-05-09

### Added
- **Top padding for edge-to-edge:** Added system status bar top padding via `WindowInsets.systemBars` to prevent content from rendering under the status bar. Enables true edge-to-edge layout on Android 15+ while keeping interactive content within safe insets.
- **Pin snackbar confirmation:** A Material Snackbar appears when a bus stop is pinned/unpinned — shows stop name + Undo action for accidental unpins. Snackbar duration 3s, stacked above the bottom navigation area.
- **Night-time halted state display:** Bus services that have halted for the night (past operating hours) now show a distinct moon icon + Halted label instead of misleading No Services. Based on the LTA DataMall API `EstimatedArrival` time vs current system time comparison.
- **ETA alignment:** Right-aligned the estimated arrival column within each card so all timing values line up vertically across multiple stops. Uses `Arrangement.End` in the Row layout with fixed 72dp width for consistent alignment.
- **Dynamic font size for bus stop names:** Stop names scale down automatically via Compose `autoSizeText` equivalent — long names render at 14sp while short names use the full 18sp. Prevents single-character names from looking oversized and multi-word names from wrapping awkwardly.
- **BuildConfig version display:** App version name and version code shown in the Settings screen footer, read from `BuildConfig.VERSION_NAME`. Helps users identify which release they are running when reporting issues.

## [0.6.7] — 2026-05-09

### Added
- **Bus name display on manual code entry:** When a user manually enters a bus stop code, the UI now fetches and displays the stop's name before the full arrival data loads. Provides immediate confirmation that the entered code is valid, using a lightweight single-endpoint lookup call.
- **Clean bus icons:** Replaced the default Material bus icon with custom SVG-based bus vectors — distinct silhouettes for single-decker vs double-decker buses, designed for sharp rendering at 24dp with no visual aliasing on high-density screens.
- **Translucent top bar design:** Redesigned top bar with frosted-glass effect — semi-transparent background with subtle blur (if supported) and a thin bottom border. Content scrolls underneath and is visible through the scrim.
- **Persistent settings with settings sheet for user preferences:** Introduced a bottom-sheet Settings panel accessible from the gear icon. Houses theme selection, about section, version info, and preference toggles. Settings persisted to DataStore and restored on app restart.
### Infrastructure
- **Initial 0.6.x release following 0.5.x feature complete state:** Marks the transition from architectural groundwork (0.5.x) to UX-focused iteration (0.6.x). All 0.5.x features — module separation, ViewModel decomposition, cache system — are stable and the team shifts to visual polish and interaction refinement.

## [0.5.0] — 2026-05-08

### Added
- **ViewModel decomposition (MainViewModel):** Split the monolithic MainViewModel into focused sub-viewmodels: BusStopViewModel (stop list state), SearchViewModel (query + results), SettingsViewModel (preferences). Each owns a clearly scoped set of UI state flows and business logic, improving testability and reducing file size below 300 lines.
- **NetworkResult sealed class for API responses:** Introduced `sealed class NetworkResult<T> { Success, Error, Loading }` replacing raw try-catch with typed state propagation. Every API call returns a NetworkResult that the UI observes as a StateFlow — eliminates null-check branches across composables.
- **Version catalog (libs.versions.toml):** Centralized all dependency versions into Gradle version catalog. Enables single-point updates via `gradle/libs.versions.toml` with automatically generated type-safe accessors for all module build.gradle.kts files.
- **Retry logic with exponential backoff:** Retry mechanism for transient LTA DataMall API failures — wraps OkHttp calls with exponential backoff (1s, 2s, 4s, max 3 retries) and jitter. Configurable per-call via retry header annotation.
- **Cache staleness tracking:** Each cached bus arrival entry tracks its fetch timestamp. UI exposes a `lastUpdated` field shown in the Updated pill, and stale entries older than 2x TTL are visually flagged with an amber tint.
- **Stale data warning in Updated pill:** The Updated pill (shown below the top bar) changes from green (fresh) to amber (stale) to red (expired) based on cache staleness. Users can see at a glance whether the displayed arrival times are recent or potentially outdated.
- **Module separation: domain, data, app layers:** Restructured project into three Gradle modules following Clean Architecture: `:domain` (pure Kotlin, no Android deps), `:data` (API + cache implementations), `:app` (Compose UI + DI wiring). Enforces module dependency direction via Gradle.
- **ArchitectureTest with regression coverage:** ArchUnit-style dependency rule tests verifying that `:domain` has zero Android imports, `:data` does not reference Compose, and `:app` is the only module with Activity/fragment dependencies. Run on every assemble.

### Changed
- **Bus API: Arrivelah to official LTA DataMall API and back:** Migrated from the Arrivelah reverse-proxy to the official LTA DataMall v1 API. Reverted back to Arrivelah after discovering LTA requires a personal API key with usage quotas — Arrivelah provides a keyless proxy suitable for open-source distribution. Both implementations remain in the source tree, switchable via a BuildConfig flag.
- **API health banner with Degraded/Down states:** Added a real-time health indicator banner that monitors API response patterns — shows green Operational, amber Degraded (intermittent failures), or red Down (consecutive timeouts) status. Based on a sliding window of the last 10 API call outcomes.
- **ProGuard rules for data.model after module separation:** After extracting domain/data modules, data-layer model classes were being stripped by ProGuard since they are only referenced via reflection by Gson. Added targeted keep rules for `com.bushop.data.model.*` to preserve serialization members.

### Fixed
- **Auto-refresh guard: refresh when ANY stop has no services or stale data:** The auto-refresh trigger now initiates a refresh cycle if any observed stop has zero services loaded OR its cached data exceeds the staleness threshold. Previously only refreshed on explicit pull-to-refresh, leaving stops blank indefinitely after cold start.
- **Test interference: cancel viewModelScope in tearDown:** Added `viewModelScope.cancel()` to each test tearDown method. Prevents lingering coroutines from one test leaking into the next, which caused sporadic assertion failures on shared ViewModel state.
- **Duplicate DuplicateStopException (domain module migration):** During the module split, a `DuplicateStopException` class ended up defined in both the domain and data modules. Removed the data-layer duplicate and unified references to the domain definition, fixing a compilation conflict.

## [0.4.0] — 2026-05-08

### Added
- **ApiClient dependency injection:** Extracted OkHttpClient + Retrofit instance creation into a dedicated ApiClient class injected via manual DI constructor pattern. Enables mock client injection in tests and centralized configuration of timeouts, interceptors, and base URL.
- **distinctUntilChanged for reactive streams:** Applied StateFlow.distinctUntilChanged() to all UI state flows — arrival data, loading flags, error states. Prevents redundant recomposition when the same data arrives from cache or when only unrelated properties change.
- **Collapse debounce for UI transitions:** Introduced a debounce window (default 300ms) on card collapse/expand state changes. Prevents rapid flickering when the user swipes past multiple cards — only the final settled state triggers the layout animation.
- **Crossfade animation removal (simplified transitions):** Replaced Crossfade composable between loading/content/error states with simple AnimatedVisibility transitions. Eliminates alpha-blend artifacts on fast data refreshes and reduces composable tree complexity.

## [0.3.0] — 2026-05-08

### Added
- **lifecycleScope integration:** Migrated UI-scoped coroutine launches from global scope to lifecycleScope — ensures all API requests, search operations, and refresh tasks are automatically cancelled when the composable leaves composition. Prevents wasted work and potential memory leaks during configuration changes.
- **Concurrency limit (max parallel requests):** Introduced a coroutine Semaphore limiting simultaneous LTA DataMall API calls to N (default 4). Prevents throttling/rate-limit triggers from the upstream API while maintaining responsive UI updates across multiple observed bus stops.
- **DataStore consolidation (single source of truth):** Unified all user preferences (pinned stops, theme, search history) into a single Jetpack DataStore instance. Eliminates inconsistent state between multiple preference files and simplifies the read/write path to a single flow.
- **ProGuard rules for release builds:** Added keep rules for ViewModel classes, Retrofit service interfaces, Gson serializers, and enum types used in API response deserialization. Prevents release builds from stripping necessary reflection-accessed members.
- **Cooldown mutex for refresh rate limiting:** Mutex-based gate preventing manual pull-to-refresh or auto-refresh from firing more often than once per COOLDOWN_MS window. Protects both the API rate limit and battery life during rapid user interactions.
- **Cache TTL for bus arrival data:** Time-based cache with configurable TTL (default 30s) for bus arrival responses. Stale data is served immediately while a background refresh fetches fresh data — eliminates visible blank loading states on re-open.
- **BusStopCard refactored component:** Extracted the per-stop card layout into a standalone @Composable with typed parameters (BusStopUiState, onPin, onRefresh). Separated presentation logic from the main list screen, enabling independent unit testing.
- **Lambda hoisting for performance:** Moved recomposition-safe lambdas (onPinToggle, onCardClick, onRefresh) out of the composable body into stable val references. Reduces unnecessary recomposition of sibling card items when one stop updates its arrival data.

## [0.2.0] — 2026-05-08

### Fixed
- **Async BusStopIndex initialization:** Moved bus stop index loading off the main thread — initializes via coroutine in the ViewModel scope with a loading state exposed to the UI. Prevents ANR on cold start with the full Singapore bus stop dataset.
- **Data persistence across sessions:** Introduced persistent storage for pinned stops, user preferences, and last-viewed stops via DataStore/SharedPreferences. State now survives app restart and process death.
- **Error handling in API calls:** Wrapped all LTA DataMall API calls in try-catch with typed error states (NetworkError, Timeout, ServerError, Unknown). Errors surfaced to UI via sealed Result classes instead of crashing or showing blank screens.

### Changed
- **Improve test quality and assertions:** Replaced vague boolean checks with precise assertions — assertEquals for expected values, assertNotNull for nullable fields, and descriptive failure messages. Added timeout-bound async test helpers to prevent flaky CI runs.
- **Build configuration cleanup:** Removed unused plugin declarations, aligned compileSdk/minSdk/targetSdk across modules, standardized JVM target to 17, and removed redundant proguard files. Reduces Gradle sync time and build warnings.
## [0.1.1] — 2026-05-08

### Fixed
- **Transparent header on scroll:** Header bar smoothly transitions from opaque to fully transparent as the user scrolls down. Driven by nestedScroll connection between the LazyColumn and the TopAppBar composable — content layers correctly behind the vanishing scrim.
- **Scroll-behavior for top bar translucency:** Dedicated TopAppBarScrollBehavior that maps scroll offset (in pixels) to an alpha value (1.0 at top to ~0.15 when fully scrolled). Uses Compose Animatable for smooth interpolation during fling gestures.
- **Layers icon for double-decker buses:** Distinct `directions_bus` outlined icon variant for double-decker bus services — helps users visually distinguish bus types at a glance without parsing the service number.

## [0.1.0] — 2026-05-08

### Added
- **Initial release:** First public version of Bus-Hop for Singapore bus arrival tracking. Core architecture: Jetpack Compose UI with MVVM pattern, Kotlin coroutines for async API calls, and LTA DataMall-compatible data layer. Established the 3-module structure (app, domain, data) for clean separation of concerns.
- **Distinct load/bus icons:** Separate Material Icon states for loading (shimmer placeholder), single-decker bus, and double-decker bus — immediate visual feedback while arrival data is being fetched from the API.
- **Translucent top bar:** Semi-transparent scrim on the top app bar allowing content to be partially visible underneath when scrolling. Uses scrollState to drive alpha transitions between transparent and opaque states.
- **Subtle header press feedback:** Touch ripple animation via Compose indication modifier — provides tactile confirmation when tapping the header area for pull-to-refresh or navigation actions.
- **Combined collapsed pill design:** Unified compact pill showing both bus service number and next arrival time in a single horizontally-constrained element when a card is collapsed. Reduces vertical footprint while preserving the two most critical data points.

[Unreleased]: https://github.com/icemedica341/Bus-Hop/compare/v1.0.3...HEAD
[1.0.3]: https://github.com/icemedica341/Bus-Hop/compare/v1.0.1...v1.0.3
[1.0.1]: https://github.com/icemedica341/Bus-Hop/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/icemedica341/Bus-Hop/compare/v0.9.7...v1.0.0
[0.9.7]: https://github.com/icemedica341/Bus-Hop/compare/v0.9.6...v0.9.7
[0.9.6]: https://github.com/icemedica341/Bus-Hop/compare/v0.9.5...v0.9.6
[0.9.5]: https://github.com/icemedica341/Bus-Hop/compare/v0.9.4...v0.9.5
[0.9.4]: https://github.com/icemedica341/Bus-Hop/compare/v0.9.3...v0.9.4
[0.9.3]: https://github.com/icemedica341/Bus-Hop/compare/v0.9.2...v0.9.3
[0.9.2]: https://github.com/icemedica341/Bus-Hop/compare/v0.9.1...v0.9.2
[0.9.1]: https://github.com/icemedica341/Bus-Hop/compare/v0.9.0...v0.9.1
[0.9.0]: https://github.com/icemedica341/Bus-Hop/compare/v0.8.9...v0.9.0
[0.8.9]: https://github.com/icemedica341/Bus-Hop/compare/v0.8.8...v0.8.9
[0.8.8]: https://github.com/icemedica341/Bus-Hop/compare/v0.8.7...v0.8.8
[0.8.7]: https://github.com/icemedica341/Bus-Hop/compare/v0.8.6...v0.8.7
[0.8.6]: https://github.com/icemedica341/Bus-Hop/compare/v0.8.5...v0.8.6
[0.8.5]: https://github.com/icemedica341/Bus-Hop/compare/v0.8.4...v0.8.5
[0.8.4]: https://github.com/icemedica341/Bus-Hop/compare/v0.8.3...v0.8.4
[0.8.3]: https://github.com/icemedica341/Bus-Hop/compare/v0.8.2...v0.8.3
[0.8.2]: https://github.com/icemedica341/Bus-Hop/compare/v0.8.1...v0.8.2
[0.8.1]: https://github.com/icemedica341/Bus-Hop/compare/v0.8.0...v0.8.1
[0.8.0]: https://github.com/icemedica341/Bus-Hop/compare/v0.7.10...v0.8.0
[0.7.10]: https://github.com/icemedica341/Bus-Hop/compare/v0.7.9...v0.7.10
[0.7.9]: https://github.com/icemedica341/Bus-Hop/compare/v0.7.8...v0.7.9
[0.7.8]: https://github.com/icemedica341/Bus-Hop/compare/v0.7.7...v0.7.8
[0.7.7]: https://github.com/icemedica341/Bus-Hop/compare/v0.7.6...v0.7.7
[0.7.6]: https://github.com/icemedica341/Bus-Hop/compare/v0.7.5...v0.7.6
[0.7.5]: https://github.com/icemedica341/Bus-Hop/compare/v0.7.4...v0.7.5
[0.7.4]: https://github.com/icemedica341/Bus-Hop/compare/v0.7.3...v0.7.4
[0.7.3]: https://github.com/icemedica341/Bus-Hop/compare/v0.7.2...v0.7.3
[0.7.2]: https://github.com/icemedica341/Bus-Hop/compare/v0.7.1...v0.7.2
[0.7.1]: https://github.com/icemedica341/Bus-Hop/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/icemedica341/Bus-Hop/compare/v0.6.9...v0.7.0
[0.6.9]: https://github.com/icemedica341/Bus-Hop/compare/v0.6.8...v0.6.9
[0.6.8]: https://github.com/icemedica341/Bus-Hop/compare/v0.6.7...v0.6.8
[0.6.7]: https://github.com/icemedica341/Bus-Hop/compare/v0.5.0...v0.6.7
[0.5.0]: https://github.com/icemedica341/Bus-Hop/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/icemedica341/Bus-Hop/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/icemedica341/Bus-Hop/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/icemedica341/Bus-Hop/compare/v0.1.1...v0.2.0
[0.1.1]: https://github.com/icemedica341/Bus-Hop/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/icemedica341/Bus-Hop/releases/tag/v0.1.0
