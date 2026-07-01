# ADR-001: Multi-module project layout

**Status:** ✅ Adopted  
**Date:** 2024 (initial), 2026-07-01 (documented)  

## Context

BusHop is a Singapore bus arrival app. The initial codebase was a single-module Android
project. As features grew (arrival data, local stop index, auto-refresh, theme switching),
the flat structure made it hard to enforce layer boundaries and slowed compile times.

## Decision

Split into three Gradle modules:

```
app/        — Android framework layer (UI, DI, platform integration)
domain/     — Pure Kotlin: use cases, domain models, repository interfaces
data/       — Android library: API clients, local storage, repository impls
```

Dependency direction: `app → data → domain` (app depends on data depends on domain).
Domain has zero dependencies on Android framework or any external library.

## Consequences

- ✅ Domain layer is fully testable with plain JVM tests (no Robolectric/AndroidX)
- ✅ Layer violations are detected by ArchitectureTest.kt (8 automated rules)
- ✅ Faster incremental builds (domain changes don't trigger app recompilation)
- ❌ Slightly more boilerplate (interface + implementation pairs)
- ⚠️ Module graph needs periodic review to prevent dependency leaks
