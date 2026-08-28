# ADR-004: Project Archival — Intentional CI Removal & Maintenance Mode

**Status:** Accepted  
**Date:** 2026-08-27  
**Deciders:** Solo maintainer  

---

## Context

Bus-Hop reached functional completeness at v1.0.5 (2026-07-09). The application is a read-only bus arrival viewer for Taipei MRT bus stops — no server-side component under our control, no user accounts, no write operations. All features described in `docs/FEATURES.md` are implemented and tested (161 unit tests, 100% pass rate at last run).

On 2026-08-26, GitHub Actions CI workflows (`.github/workflows/`) and Dependabot configuration (`.github/dependabot.yml`) were removed via commits `06fd121` and `5594f35`. This ADR documents that removal as an **intentional archival decision**, not abandonment.

## Decision

Bus-Hop enters **archival maintenance mode** effective 2026-08-27.

### What Was Removed

| Artifact | Commit | Rationale |
|----------|--------|-----------|
| `.github/workflows/android.yml` | `06fd121` | No active development; CI runs are wasted compute |
| `.github/dependabot.yml` | `5594f35` | Dependencies frozen at v1.0.5; no automated bumps |

### What Is Preserved

| Artifact | Purpose |
|----------|---------|
| `scripts/check-local.sh` | Local quality gates (ktlint, detekt, unit tests) — run manually before any future change |
| `scripts/release.sh` | Release artifact generation (kept for reproducibility) |
| `docs/adr/` (001-004) | Architectural decision history |
| `docs/FEATURES.md` | Feature inventory with F-### anchors |
| `docs/RETROSPECTIVE_SPEC.md` | Original recursive spec framework |
| `SPECIFICATION.md` | Functional/non-functional requirements with traceability |
| `TECH_DEBT_AUDIT.md` | Technical debt status (triaged) |
| `CHANGELOG.md` | Full version history through archival |
| `detekt.yml` | Static analysis configuration (used by local gates) |
| Gradle wrapper + config | Reproducible builds if needed |

### Maintenance Mode Definition

- **Activity budget:** ≤2 hours/week maximum
- **Scope:** Security fixes only (vulnerabilities in dependencies or runtime behavior)
- **No feature work:** New features, enhancements, or non-critical bug fixes are declined
- **No dependency updates:** Dependencies are frozen unless a CVE is published
- **Local gates:** `scripts/check-local.sh` must pass before any security patch is applied

### Re-Activation Criteria

Archival can be reversed if ANY of:

1. **Upstream API change:** TBC (Taipei bus API) deprecates current endpoints, breaking core functionality
2. **Security vulnerability:** A dependency CVE rated CVSS ≥7.0 affects runtime behavior
3. **Platform deprecation:** Android SDK minimum API level rises above current target (API 26)
4. **Explicit sponsor:** A contributor commits to ≥3 months active maintenance with weekly releases

Re-activation requires: new ADR superseding this one, CI restoration PR, dependency update sweep, and test suite verification.

## Alternatives Considered

| Alternative | Why Rejected |
|-------------|-------------|
| Keep CI enabled, ignore failures | Creates noisy failure badges; signals neglect not intent |
| Delete repo entirely | Loss of reference implementation for Kotlin/Android patterns |
| Transfer to another maintainer | No identified successor; solo project |
| Pin CI to manual dispatch only | Over-engineered for a frozen codebase; local gates suffice |

## Consequences

- **Positive:** Clear signal of intentional archival; no wasted CI compute; documented maintenance mode
- **Negative:** No automated dependency scanning; security patches depend on manual monitoring
- **Mitigation:** Quarterly manual dependency audit via `./gradlew dependencyUpdates` (see dependency audit section in this ADR)

## Dependency Audit Strategy

With Dependabot removed, dependency monitoring shifts to manual:

- **Frequency:** Quarterly (next: 2026-11-27)
- **Command:** `./gradlew dependencyUpdates` (requires ben-manesVersions plugin — if not configured, manual check against releases)
- **Scope:** Only act on CVEs rated CVSS ≥7.0; ignore minor/patch updates
- **Record:** Append findings to this ADR section with date and action taken

### Current Dependency Snapshot (2026-08-27)

| Dependency | Version | Status |
|-----------|---------|--------|
| Kotlin | 2.4.10 | Current |
| Compose BOM | 2026.06.00 | Current |
| Ktor | 3.1.3 | Current |
| KotlinX Serialization | 1.9.0 | Current |
| KotlinX Coroutines | 1.10.2 | Current |
| Material3 | 1.13.2 | Current |
| AndroidX Core | 1.16.0 | Current |
| AndroidX Activity | 1.10.1 | Current |
| AndroidX Lifecycle | 2.9.1 | Current |
| AndroidX Navigation | 2.9.0 | Current |
| Coil | 3.2.0 | Current |
| JUnit | 4.13.2 | Current |
| MockK | 1.14.2 | Current |
| Turbine | 1.2.0 | Current |
| JaCoCo | 0.8.12 | Current |
| Detekt | 1.23.8 | Current |

All dependencies were current as of v1.0.5 release. No known CVEs at archival date.
