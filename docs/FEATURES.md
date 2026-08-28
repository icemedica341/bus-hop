# Bus-Hop Feature Inventory

**Status:** Archived (ADR-004)  
**Last Updated:** 2026-08-27  
**Total Features:** 10  
**Test Coverage:** 161 unit tests across 3 modules  

---

## Feature Index

| ID | Feature | Status | Priority |
|----|---------|--------|----------|
| F-001 | Real-time bus arrival display | Archived | P0 |
| F-002 | Smart search with fuzzy matching | Archived | P0 |
| F-003 | Stop management (pin/reorder/delete) | Archived | P1 |
| F-004 | Material 3 theming | Archived | P1 |
| F-005 | Auto-refresh & pull-to-refresh | Archived | P1 |
| F-006 | API health monitoring | Archived | P2 |
| F-007 | Privacy-first architecture | Archived | P0 |
| F-008 | In-app updates & splash screen | Archived | P2 |
| F-009 | Nearby stops with location | Archived | P1 |
| F-010 | Bus type icons & load indicators | Archived | P1 |

---

## F-001: Real-time Bus Arrival Display

**Status:** Archived | **Priority:** P0  
**Behavior Contract:**

- **Pre-condition:** User has selected a bus stop (via search, pin, or nearby)
- **Primary behavior:** Fetches real-time arrival data from TBC (Taipei) open data API; displays next arriving buses with ETA, destination, and operator
- **Post-condition:** Arrival list shown; auto-refreshes every 15 seconds when app is foregrounded
- **Error states:** API timeout → offline banner; empty response → "No arrivals" message; rate limit → retry after delay

**Test Anchoring:**
- Unit tests in `data/` module for API response parsing
- ViewModel state transitions tested in `app/` module
- `docs/RETROSPECTIVE_SPEC.md` §3.1 arrival display spec

---

## F-002: Smart Search with Fuzzy Matching

**Status:** Archived | **Priority:** P0  
**Behavior Contract:**

- **Pre-condition:** User taps search field on main screen
- **Primary behavior:** Token-based search (TokenTrie index) with Levenshtein distance for typo tolerance; filters stops and bus services in real-time as user types
- **Post-condition:** Results ranked by relevance; tapping a result navigates to that stop's arrival board
- **Error states:** Empty query → show recent/pinned stops; no matches → "No results" with suggestion to check spelling

**Test Anchoring:**
- TokenTrie and Levenshtein algorithms unit-tested in `domain/` module
- SearchManager state machine tests in `app/` module
- Edge cases: CJK characters, partial pinyin, mixed input

---

## F-003: Stop Management (Pin/Reorder/Delete)

**Status:** Archived | **Priority:** P1  
**Behavior Contract:**

- **Pre-condition:** User has search results or a displayed stop
- **Primary behavior:** Pin stops for quick access; drag-to-reorder pinned list; swipe/drag-to-delete; persisted via local storage (no cloud sync)
- **Post-condition:** Pinned list order saved; deleted stops removed from pinned only (not from search results)
- **Error states:** Storage full → warning; duplicate pin → no-op with feedback

**Test Anchoring:**
- PinManager persistence tests in `app/` module
- StopStateManager ordering logic tests
- Drag interaction verified via manual QA (no UI tests)

---

## F-004: Material 3 Theming

**Status:** Archived | **Priority:** P1  
**Behavior Contract:**

- **Pre-condition:** App launched or settings opened
- **Primary behavior:** Dynamic color support (Android 12+); manual dark/light/system theme toggle; custom theme seed colors; typography scale
- **Post-condition:** Theme applied immediately; persisted across restarts
- **Error states:** Fallback to system theme if custom theme fails to load

**Test Anchoring:**
- ThemeManager state persistence tests
- Theme.kt / Typography.kt configuration verified by compilation
- Visual QA via manual inspection (no screenshot tests)

---

## F-005: Auto-refresh & Pull-to-refresh

**Status:** Archived | **Priority:** P1  
**Behavior Contract:**

- **Pre-condition:** Arrival board is displayed
- **Primary behavior:** Auto-refresh every 15 seconds while app is foregrounded; manual pull-to-refresh triggers immediate update; refresh pauses when app backgrounded
- **Post-condition:** Data stays current; battery-efficient pause on background
- **Error states:** Network unavailable → pause auto-refresh, show offline indicator; API error → show last cached data with stale indicator

**Test Anchoring:**
- Refresh interval logic tested in ViewModel
- Lifecycle-aware refresh (foreground/background) tested via coroutine scope management
- Pull-to-refresh UI interaction verified manually

---

## F-006: API Health Monitoring

**Status:** Archived | **Priority:** P2  
**Behavior Contract:**

- **Pre-condition:** App has network connectivity
- **Primary behavior:** Background health check against TBC API endpoint; displays status banner (healthy/degraded/offline) on main screen
- **Post-condition:** Status updates on each refresh cycle; degrades gracefully if health endpoint unreachable
- **Error states:** Health check timeout → show "Unknown" status; persistent failure → degraded indicator

**Test Anchoring:**
- ApiStatusBanner state rendering tests
- Health check response parsing tests in `data/` module
- Status enum transitions tested

---

## F-007: Privacy-First Architecture

**Status:** Archived | **Priority:** P0  
**Behavior Contract:**

- **Pre-condition:** App installed
- **Primary behavior:** Zero data collection; no analytics; no crash reporting; no user accounts; no network calls except TBC bus API; all data stored locally only
- **Post-condition:** No PII leaves the device; no third-party SDKs with data collection
- **Error states:** N/A (design constraint, not runtime behavior)

**Test Anchoring:**
- Network layer audit: only TBC API endpoints called (verified by code review)
- No analytics/crash SDK dependencies in `libs.versions.toml`
- Privacy policy in README.md §Privacy

---

## F-008: In-App Updates & Splash Screen

**Status:** Archived | **Priority:** P2  
**Behavior Contract:**

- **Pre-condition:** App launched
- **Primary behavior:** AndroidX Splash Screen API for fast startup; in-app update notification when new version available (checked against GitHub releases)
- **Post-condition:** Splash dismissed after data load; update prompt shown once per version
- **Error states:** Update check fails → silent skip; splash timeout → proceed to main screen

**Test Anchoring:**
- UpdateManager version comparison logic tested
- Splash screen configuration verified by compilation
- Update flow tested manually against mock version numbers

---

## F-009: Nearby Stops with Location

**Status:** Archived | **Priority:** P1  
**Behavior Contract:**

- **Pre-condition:** Location permission granted (optional)
- **Primary behavior:** Requests device location; calculates distance to known stops; shows nearest stops sorted by distance; graceful fallback if permission denied
- **Post-condition:** Nearby list populated or "Location unavailable" shown
- **Error states:** Permission denied → show all stops without distance; GPS off → prompt to enable; no stops in range → "No nearby stops"

**Test Anchoring:**
- Distance calculation logic unit-tested in `domain/` module
- Location permission handling tested via mock permissions
- Nearby sorting algorithm tested with synthetic coordinates

---

## F-010: Bus Type Icons & Load Indicators

**Status:** Archived | **Priority:** P1  
**Behavior Contract:**

- **Pre-condition:** Arrival data loaded
- **Primary behavior:** Displays bus type icons (normal, express, wheelchair-accessible); shows load level indicator (empty/light/heavy) with color coding; operator badge displayed
- **Post-condition:** Visual information supplements text arrival data
- **Error states:** Missing icon data → default icon; unknown load level → no indicator shown

**Test Anchoring:**
- Icon resource mapping tested (compilation verification)
- Load level enum parsing tests in `data/` module
- Visual rendering verified manually

---

## Traceability Matrix

| Feature | Test Module | RETROSPECTIVE_SPEC § | SPECIFICATION FR |
|---------|------------|---------------------|-----------------|
| F-001 | data/, app/ | §3.1 | FR-01, FR-02, FR-03 |
| F-002 | domain/, app/ | §3.2 | FR-04, FR-05 |
| F-003 | app/ | §3.3 | FR-06, FR-07, FR-08 |
| F-004 | app/ | §3.4 | FR-09, FR-10 |
| F-005 | app/ | §3.5 | FR-11, FR-12 |
| F-006 | data/, app/ | §3.6 | FR-13, FR-14 |
| F-007 | (code review) | §3.7 | NFR-01, NFR-02 |
| F-008 | app/ | §3.8 | FR-15, FR-16 |
| F-009 | domain/, app/ | §3.9 | FR-17, FR-18 |
| F-010 | data/ | §3.10 | FR-19, FR-20 |

---

## Notes

- All features are in **Archived** status per ADR-004
- No active development; security-only maintenance mode
- Test suite (161 tests) preserved for regression verification
- See `docs/RETROSPECTIVE_SPEC.md` for original detailed specification
