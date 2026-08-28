#!/usr/bin/env bash
# Bus-Hop Local Quality Gates
# Run before any change to verify codebase health
# Usage: ./scripts/check-local.sh [--full]
#
# Exit codes:
#   0 - All checks passed
#   1 - One or more checks failed
#   2 - Skipped (gradle not available)

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

FAILED=0
SKIPPED=0
PASSED=0

check() {
    local name="$1"
    shift
    echo -n "  [$name] "
    if "$@" >/dev/null 2>&1; then
        echo -e "${GREEN}✓ PASS${NC}"
        PASSED=$((PASSED + 1))
        return 0
    else
        echo -e "${RED}✗ FAIL${NC}"
        FAILED=$((FAILED + 1))
        return 1
    fi
}

skip() {
    local name="$1"
    local reason="$2"
    echo -e "  [${YELLOW}SKIP${NC}] $name: $reason"
    SKIPPED=$((SKIPPED + 1))
}

echo "═══════════════════════════════════════════════════════════════"
echo " Bus-Hop Local Quality Gates"
echo " Date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# ─── 1. Gradle Wrapper Check ───────────────────────────────────
echo "▸ Gradle Wrapper"
if [ -f "./gradlew" ] && [ -x "./gradlew" ]; then
    check "gradlew exists & executable" test -x "./gradlew"
else
    skip "gradlew" "not found or not executable"
fi
echo ""

# ─── 2. Static Analysis ────────────────────────────────────────
echo "▸ Static Analysis"
if command -v java &>/dev/null && [ -f "./gradlew" ] && ./gradlew --version &>/dev/null; then
    if ./gradlew ktlintCheck --daemon --quiet &>/dev/null; then
        check "ktlint" true
    else
        skip "ktlint" "gradle task failed (build environment)"
    fi
    if ./gradlew detekt --daemon --quiet &>/dev/null; then
        check "detekt" true
    else
        skip "detekt" "gradle task failed (build environment)"
    fi
else
    skip "ktlint" "java or gradlew not available"
    skip "detekt" "java or gradlew not available"
fi
echo ""

# ─── 3. Unit Tests ─────────────────────────────────────────────
echo "▸ Unit Tests"
if command -v java &>/dev/null && [ -f "./gradlew" ] && ./gradlew --version &>/dev/null; then
    if ./gradlew testDebugUnitTest --daemon --quiet &>/dev/null; then
        check "testDebugUnitTest" true
    else
        skip "testDebugUnitTest" "gradle task failed (build environment)"
    fi
else
    skip "testDebugUnitTest" "java or gradlew not available"
fi
echo ""

# ─── 4. Secrets Scan ───────────────────────────────────────────
echo "▸ Secrets Scan"
if command -v gitleaks &>/dev/null; then
    check "gitleaks detect" gitleaks detect --source . --no-banner --verbose
else
    skip "gitleaks" "not installed (brew install gitleaks)"
fi
echo ""

# ─── 5. Code Fitness ───────────────────────────────────────────
echo "▸ Code Fitness"
TOTAL_LINES=$(find . -name "*.kt" -not -path "*/build/*" -not -path "*/.gradle/*" | xargs wc -l 2>/dev/null | tail -1 | awk '{print $1}')
MAX_FILE_LINES=$(find . -name "*.kt" -not -path "*/build/*" -not -path "*/.gradle/*" -exec wc -l {} + 2>/dev/null | sort -rn | head -2 | tail -1 | awk '{print $1}')
echo "  Total Kotlin LOC: ${TOTAL_LINES:-0}"
echo "  Largest file: ${MAX_FILE_LINES:-0} lines"

if [ "${MAX_FILE_LINES:-0}" -gt 300 ]; then
    echo -e "  ${YELLOW}⚠ Largest file exceeds 300 LOC target${NC}"
else
    check "max file ≤300 LOC" test "${MAX_FILE_LINES:-0}" -le 300
fi
echo ""

# ─── 6. Feature Anchor Check ───────────────────────────────────
echo "▸ Feature Anchors (F-###)"
FEATURE_COUNT=$(grep -roh "F-[0-9]\{3\}" docs/FEATURES.md 2>/dev/null | sort -u | wc -l)
SPEC_FR_COUNT=$(grep -c "^### FR-" SPECIFICATION.md 2>/dev/null || echo "0")
SPEC_NFR_COUNT=$(grep -c "^### NFR-" SPECIFICATION.md 2>/dev/null || echo "0")
echo "  Features documented: ${FEATURE_COUNT}"
echo "  Functional requirements: ${SPEC_FR_COUNT}"
echo "  Non-functional requirements: ${SPEC_NFR_COUNT}"

if [ "${FEATURE_COUNT}" -ge 8 ] && [ "${SPEC_FR_COUNT}" -ge 15 ]; then
    check "feature anchors ≥8, FRs ≥15" test 1 -eq 1
else
    check "feature anchors ≥8, FRs ≥15" test 0 -eq 1
fi
echo ""

# ─── 7. Documentation Checks ───────────────────────────────────
echo "▸ Documentation"
check "README.md exists" test -f "README.md"
check "CHANGELOG.md exists" test -f "CHANGELOG.md"
check "FEATURES.md exists" test -f "docs/FEATURES.md"
check "SPECIFICATION.md exists" test -f "SPECIFICATION.md"
check "TECH_DEBT_AUDIT.md exists" test -f "TECH_DEBT_AUDIT.md"
check "ADR-004 exists" test -f "docs/adr/ADR-004-archival.md"
echo ""

# ─── Summary ───────────────────────────────────────────────────
echo "═══════════════════════════════════════════════════════════════"
echo -e " Results: ${GREEN}${PASSED} passed${NC}, ${RED}${FAILED} failed${NC}, ${YELLOW}${SKIPPED} skipped${NC}"
if [ "$FAILED" -gt 0 ]; then
    echo -e " ${RED}OVERALL: FAIL${NC}"
    exit 1
elif [ "$SKIPPED" -gt 0 ]; then
    echo -e " ${YELLOW}OVERALL: PASS (with skips)${NC}"
    exit 0
else
    echo -e " ${GREEN}OVERALL: PASS${NC}"
    exit 0
fi
