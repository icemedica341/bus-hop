# ADR-002: Pure domain layer with zero framework dependencies

**Status:** ✅ Adopted  
**Date:** 2024 (initial), 2026-07-01 (documented)  

## Context

The domain layer contains business logic, use cases, and domain models.
We wanted this layer to be: (a) easily testable without Android infrastructure,
(b) portable if the UI framework changes, and (c) a single source of truth for
business rules.

## Decision

The `domain/` module:
- Targets pure JVM (`kotlin("jvm")` plugin) — no Android plugin
- Declares **zero runtime dependencies** — no Kotlinx libraries, no coroutines,
  no AndroidX, no networking
- Domain models are plain data classes with `@JvmInline` value types where appropriate
- Repository interfaces are pure Kotlin interfaces (no annotations)
- Use cases are stateless functions/classes

The only build dependency is the Kotlin standard library (implicit via the plugin).

## Consequences

- ✅ All domain tests run via `./gradlew :domain:test` — pure JVM, sub-second execution
- ✅ Domain can be extracted into a standalone Kotlin Multiplatform module if needed
- ✅ No Android SDK stubs or Robolectric needed for domain testing
- ✅ ArchitectureTest.kt enforces that no domain file imports Android or external packages
- ❌ Domain cannot use Kotlinx Coroutines for async (not a real limitation — callers
  handle threading in the data/app layers)
