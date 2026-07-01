#!/usr/bin/env bash
set -euo pipefail

# ── BusHop Release Script ──
# Usage:  ./scripts/release.sh [patch|minor|major]
# Example: ./scripts/release.sh patch   →  v1.0.3 → v1.0.4
#          ./scripts/release.sh minor   →  v1.0.3 → v1.1.0
#          ./scripts/release.sh major   →  v1.0.3 → v2.0.0

if ! command -v gh &>/dev/null; then
  echo "❌ GitHub CLI (gh) required. Install: https://cli.github.com/"
  exit 1
fi

# Parse bump type
bump="${1:-patch}"
case "$bump" in
  patch|minor|major) ;;
  *) echo "Usage: $0 [patch|minor|major]" && exit 1 ;;
esac

# Get current version from latest git tag
current_tag=$(git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0")
current=${current_tag#v}
IFS=. read -r major minor patch <<< "$current"

case "$bump" in
  patch) new="v$major.$minor.$((patch + 1))" ;;
  minor) new="v$major.$((minor + 1)).0" ;;
  major) new="v$((major + 1)).0.0" ;;
esac

echo "📦 $current_tag → $new"

# Check changelog
if ! grep -q "^## \[${new#v}\]" CHANGELOG.md 2>/dev/null; then
  echo "⚠️  No entry in CHANGELOG.md for version ${new#v} — edit it first, then re-run"
  exit 1
fi

# Create signed tag and push
git tag -s "$new" -m "release $new"
git push origin "$new"

echo "✅ Tag $new pushed — GitHub Actions will build the release"
echo "   Monitor: gh run list --workflow release.yml"
