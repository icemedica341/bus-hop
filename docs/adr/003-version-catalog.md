# ADR-003: Version catalog (libs.versions.toml) dependency management

**Status:** ✅ Adopted  
**Date:** 2024 (initial), 2026-07-01 (documented)  

## Context

Multi-module projects need consistent dependency versions across all modules.
Hardcoding versions in each `build.gradle.kts` leads to drift and upgrade pain.

## Decision

Use Gradle's built-in version catalog (`gradle/libs.versions.toml`) for all dependencies.
- All dependency coordinates and versions live in the catalog
- Modules reference dependencies via `libs.*` type-safe accessors
- Plugin aliases are also declared in the catalog

## Consequences

- ✅ Single location for version bumps — one line change propagates everywhere
- ✅ Dependabot can scan the TOML and open version bumps automatically
- ✅ Type-safe accessors prevent typos (`libs.retrofit` vs `libs.retrofti`)
- ✅ New modules don't need to repeat dependency declarations
- ❌ Renaming a catalog entry requires updating every module that uses it
