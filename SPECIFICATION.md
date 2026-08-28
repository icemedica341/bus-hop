# Bus-Hop Specification

**Version:** 1.0.5 (Final)  
**Status:** Archived (ADR-004)  
**Date:** 2026-08-27  
**Traceability:** → `docs/FEATURES.md` F-### anchors  

---

## 1. Functional Requirements

### FR-01: Bus Stop Search
**Priority:** P0 | **Status:** Implemented | **Traceability:** F-002  
**Description:** User can search for bus stops by name (Chinese/English), stop ID, or route number.  
**Acceptance:** Results appear within 300ms of keystroke; fuzzy matching tolerates ≤2 character errors.  
**Test Anchor:** `domain/` TokenTrie + Levenshtein tests

### FR-02: Real-time Arrival Display
**Priority:** P0 | **Status:** Implemented | **Traceability:** F-001  
**Description:** Selected stop shows next arriving buses with ETA, destination, and operator.  
**Acceptance:** Data refreshes every 15 seconds; ETA accuracy ±30 seconds.  
**Test Anchor:** `data/` API response parsing tests

### FR-03: Auto-refresh
**Priority:** P1 | **Status:** Implemented | **Traceability:** F-005  
**Description:** Arrival data auto-refreshes while app is foregrounded.  
**Acceptance:** Refresh interval 15s ±2s; pauses on background; resumes on foreground.  
**Test Anchor:** ViewModel lifecycle tests

### FR-04: Manual Refresh
**Priority:** P1 | **Status:** Implemented | **Traceability:** F-005  
**Description:** Pull-to-refresh gesture triggers immediate data update.  
**Acceptance:** Refresh completes within 3s on 4G connection; visual indicator shown.  
**Test Anchor:** Manual QA

### FR-05: Stop Pinning
**Priority:** P1 | **Status:** Implemented | **Traceability:** F-003  
**Description:** User can pin stops for quick access from main screen.  
**Acceptance:** Pinned stops persist across app restarts; max 50 pins.  
**Test Anchor:** PinManager persistence tests

### FR-06: Drag-to-Reorder
**Priority:** P2 | **Status:** Implemented | **Traceability:** F-003  
**Description:** Pinned stops can be reordered via drag gesture.  
**Acceptance:** Order persisted immediately; no data loss on rapid drags.  
**Test Anchor:** StopStateManager ordering tests

### FR-07: Drag-to-Delete
**Priority:** P2 | **Status:** Implemented | **Traceability:** F-003  
**Description:** Pinned stops can be removed via swipe/drag gesture.  
**Acceptance:** Confirmation required; removal persisted; undo available for 5s.  
**Test Anchor:** Manual QA

### FR-08: Sort by Earliest Arrival
**Priority:** P1 | **Status:** Implemented | **Traceability:** F-001  
**Description:** Arrival list can be sorted by earliest arriving bus.  
**Acceptance:** Sort toggle persists per session; default is by route number.  
**Test Anchor:** ViewModel sort logic tests

### FR-09: Theme Selection
**Priority:** P1 | **Status:** Implemented | **Traceability:** F-004  
**Description:** User can select dark/light/system theme and custom seed color.  
**Acceptance:** Theme applies within 100ms; persists across restarts.  
**Test Anchor:** ThemeManager state tests

### FR-10: Dynamic Color
**Priority:** P2 | **Status:** Implemented | **Traceability:** F-004  
**Description:** Android 12+ devices use dynamic color from wallpaper.  
**Acceptance:** Falls back to static theme on older devices.  
**Test Anchor:** Compilation verification

### FR-11: Nearby Stops
**Priority:** P1 | **Status:** Implemented | **Traceability:** F-009  
**Description:** Shows stops sorted by distance from current location.  
**Acceptance:** Distance accuracy ±100m; refreshes on location change.  
**Test Anchor:** Distance calculation unit tests

### FR-12: Location Permission Handling
**Priority:** P1 | **Status:** Implemented | **Traceability:** F-009  
**Description:** Graceful handling of location permission denial.  
**Acceptance:** App functions fully without location; no crashes on denial.  
**Test Anchor:** Permission mock tests

### FR-13: API Health Status
**Priority:** P2 | **Status:** Implemented | **Traceability:** F-006  
**Description:** Displays API health status (healthy/degraded/offline).  
**Acceptance:** Status updates on each refresh; degrades gracefully.  
**Test Anchor:** ApiStatusBanner tests

### FR-14: Offline Indicator
**Priority:** P2 | **Status:** Implemented | **Traceability:** F-006  
**Description:** Shows offline banner when network unavailable.  
**Acceptance:** Banner appears within 2s of network loss; hides on reconnect.  
**Test Anchor:** Network state tests

### FR-15: Splash Screen
**Priority:** P2 | **Status:** Implemented | **Traceability:** F-008  
**Description:** AndroidX Splash Screen API for fast startup.  
**Acceptance:** Splash duration ≤1.5s; data loads in background.  
**Test Anchor:** Compilation verification

### FR-16: In-app Update Notification
**Priority:** P2 | **Status:** Implemented | **Traceability:** F-008  
**Description:** Notifies user when new version available on GitHub.  
**Acceptance:** Check on app launch; prompt shown once per version.  
**Test Anchor:** UpdateManager version tests

### FR-17: Operator Badge Display
**Priority:** P1 | **Status:** Implemented | **Traceability:** F-010  
**Description:** Shows operator name/icon on each arrival entry.  
**Acceptance:** Badge matches operator API data; fallback for unknown operators.  
**Test Anchor:** Data mapping tests

### FR-18: Bus Type Icons
**Priority:** P1 | **Status:** Implemented | **Traceability:** F-010  
**Description:** Displays bus type icon (normal/express/wheelchair).  
**Acceptance:** Icon matches type field; default icon for unknown types.  
**Test Anchor:** Icon resource mapping tests

### FR-19: Load Level Indicator
**Priority:** P1 | **Status:** Implemented | **Traceability:** F-010  
**Description:** Shows bus load level (empty/light/heavy) with color coding.  
**Acceptance:** Colors: green (empty), yellow (light), red (heavy).  
**Test Anchor:** Load level enum parsing tests

### FR-20: Random Hints
**Priority:** P3 | **Status:** Implemented | **Traceability:** F-001  
**Description:** Shows random usage hints on main screen.  
**Acceptance:** Hints rotate on each app launch; no repeats within 7 days.  
**Test Anchor:** Manual QA

---

## 2. Non-Functional Requirements

### NFR-01: Privacy
**Priority:** P0 | **Status:** Implemented | **Traceability:** F-007  
**Description:** Zero data collection; no analytics; no crash reporting; no user accounts.  
**Metric:** 0 third-party data collection SDKs; only TBC API calls.  
**Verification:** Dependency audit + network layer code review.

### NFR-02: Data Sovereignty
**Priority:** P0 | **Status:** Implemented | **Traceability:** F-007  
**Description:** All data stored locally on device; no cloud sync.  
**Metric:** 0 cloud endpoints besides TBC bus API.  
**Verification:** Network inspection + storage layer review.

### NFR-03: Performance
**Priority:** P1 | **Status:** Implemented | **Traceability:** F-001, F-002  
**Description:** App responds to user input within 300ms; startup ≤2s.  
**Metric:** Search results <300ms; cold start <2s on mid-range device.  
**Verification:** Manual timing + coroutine dispatcher tests.

### NFR-04: Test Coverage
**Priority:** P1 | **Status:** Implemented | **Traceability:** All F-###  
**Description:** Unit test coverage ≥60% (JaCoCo threshold).  
**Metric:** 161 tests; 60% JaCoCo threshold configured.  
**Verification:** `./gradlew testDebugUnitTest` + JaCoCo report.

### NFR-5: Build Reproducibility
**Priority:** P1 | **Status:** Implemented | **Traceability:** ADR-003  
**Description:** Builds are reproducible via version catalog + dependency locking.  
**Metric:** `gradle.lockfile` present; version catalog pins all dependencies.  
**Verification:** Clean build from lockfile.

### NFR-06: Accessibility
**Priority:** P2 | **Status:** Partial | **Traceability:** F-004  
**Description:** Material 3 components provide baseline accessibility.  
**Metric:** Dynamic type support; sufficient color contrast.  
**Verification:** Manual inspection (no automated accessibility tests).

### NFR-07: Offline Resilience
**Priority:** P1 | **Status:** Implemented | **Traceability:** F-005, F-006  
**Description:** App shows cached data when offline; no crashes.  
**Metric:** 0 crashes on network loss; last data shown within 1s.  
**Verification:** Network disable testing.

---

## 3. Traceability Matrix

| Requirement | Feature | Test Module | Status |
|------------|---------|------------|--------|
| FR-01 | F-002 | domain/ | ✅ |
| FR-02 | F-001 | data/, app/ | ✅ |
| FR-03 | F-005 | app/ | ✅ |
| FR-04 | F-005 | manual | ✅ |
| FR-05 | F-003 | app/ | ✅ |
| FR-06 | F-003 | app/ | ✅ |
| FR-07 | F-003 | manual | ✅ |
| FR-08 | F-001 | app/ | ✅ |
| FR-09 | F-004 | app/ | ✅ |
| FR-10 | F-004 | compilation | ✅ |
| FR-11 | F-009 | domain/ | ✅ |
| FR-12 | F-009 | manual | ✅ |
| FR-13 | F-006 | data/, app/ | ✅ |
| FR-14 | F-006 | manual | ✅ |
| FR-15 | F-008 | compilation | ✅ |
| FR-16 | F-008 | app/ | ✅ |
| FR-17 | F-010 | data/ | ✅ |
| FR-18 | F-010 | data/ | ✅ |
| FR-19 | F-010 | data/ | ✅ |
| FR-20 | F-001 | manual | ✅ |
| NFR-01 | F-007 | review | ✅ |
| NFR-02 | F-007 | review | ✅ |
| NFR-03 | F-001, F-002 | manual | ✅ |
| NFR-04 | All | JaCoCo | ✅ |
| NFR-05 | ADR-003 | build | ✅ |
| NFR-06 | F-004 | manual | ⚠️ Partial |
| NFR-07 | F-005, F-006 | manual | ✅ |

---

## Notes

- All requirements satisfied at v1.0.5 release
- NFR-06 (accessibility) marked partial: Material 3 provides baseline, but no automated accessibility testing configured
- See `docs/RETROSPECTIVE_SPEC.md` for original detailed specification with code references
- See `docs/FEATURES.md` for feature behavior contracts
