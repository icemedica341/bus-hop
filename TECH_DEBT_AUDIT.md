# Bus-Hop Technical Debt Audit

**Date:** 2026-08-27  
**Status:** Archived (ADR-004)  
**Auditor:** Automated + manual review  
**Scope:** All modules (app, data, domain)  

---

## Executive Summary

**Total Debt Items:** 0 active, 3 known wont-fix  
**Risk Level:** LOW — project is archived, no active development  
**Recommendation:** No action required; items documented for awareness only

---

## Active Debt Items

**None.** All identified items are in the "known wont-fix" category due to archival status.

---

## Known Won't-Fix (Archived Project)

### WNF-001: No Automated Accessibility Testing
**Severity:** Low | **Effort:** Medium | **Category:** Testing  
**Description:** No automated accessibility testing (e.g., Espresso AccessibilityChecks, Axe). Material 3 provides baseline, but no automated verification.  
**Impact:** Manual accessibility review not performed; potential issues undetected.  
**Justification:** Archived project; Material 3 components provide reasonable baseline; no user-reported issues.  
**If Active Would Fix:** Add Espresso AccessibilityChecks to instrumented test suite.

### WNF-002: No UI Test Automation
**Severity:** Low | **Effort:** High | **Category:** Testing  
**Description:** All UI interactions verified manually; no Espresso/Compose UI tests.  
**Impact:** Regression risk on UI changes; manual QA required for any future modification.  
**Justification:** Archived project; 161 unit tests cover business logic; UI is stable and simple.  
**If Active Would Fix:** Add Compose UI test rules for critical flows (search, pin, theme).

### WNF-003: No Screenshot/Visual Regression Tests
**Severity:** Low | **Effort:** Medium | **Category:** Testing  
**Description:** No screenshot comparison tests (e.g., Paparazzi, Roborazzi).  
**Impact:** Visual regressions undetectable without manual inspection.  
**Justified:** Archived project; theme is stable; Material 3 components are well-tested upstream.  
**If Active Would Fix:** Add Paparazzi for key screens.

---

## Debt Prevention (Archived Mode)

Since the project is archived, no new debt should accumulate. The following safeguards are in place:

1. **Local gates** (`scripts/check-local.sh`): ktlint, detekt, unit tests must pass before any change
2. **Dependency freezing**: No automated updates; manual quarterly audit only for CVEs
3. **Scope restriction**: Security fixes only; no feature work or enhancements

---

## Metrics

| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| Unit test count | 161 | ≥100 | ✅ |
| JaCoCo threshold | 60% | ≥60% | ✅ |
| Detekt issues | 0 | 0 | ✅ |
| Ktlint issues | 0 | 0 | ✅ |
| Active debt items | 0 | 0 | ✅ |
| Known wont-fix | 3 | — | Documented |

---

## Notes

- All 3 wont-fix items are testing-related; business logic is well-covered by unit tests
- No security debt identified (dependencies current as of archival)
- No architectural debt (clean architecture properly implemented per ADR-001/002)
- This audit is a snapshot at archival; no future audits planned unless project is reactivated per ADR-004 criteria
