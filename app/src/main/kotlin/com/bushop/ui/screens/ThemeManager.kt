package com.bushop.ui.screens

/**
 * ┌─ ThemeManager ───────────────────────────────────┐
 * │  app/ layer · Theme state management             │
 * │                                                   │
 * │  themeMode ─→ SYSTEM / LIGHT / DARK              │
 * │  colorSchemeOption ─→ BLUE / CONTRAST_BLUE       │
 * │  Persisted in DataStore via BusRepository        │
 * │  Exposed as StateFlow to MainViewModel           │
 * └───────────────────────────────────────────────────┘
 */

import com.bushop.domain.model.ColorSchemeOption
import com.bushop.domain.model.ThemeMode
import com.bushop.domain.repository.BusRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages theme mode (system/light/dark) and color scheme (blue/contrast) state
 * with persistence via [BusRepository].
 *
 * Separated from MainViewModel for SRP. Instantiated by MainViewModel and exposed
 * through its public API so MainActivity needs no changes.
 */
class ThemeManager(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val repository: BusRepository,
) {
    // ── Theme mode ──

    private val _themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeModeFlow.value = mode
        scope.launch { repository.setThemeMode(mode) }
    }

    fun toggleThemeMode() {
        setThemeMode(
            when (_themeModeFlow.value) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            },
        )
    }

    // ── Colour scheme ──

    private val _colorSchemeOptionFlow = MutableStateFlow(ColorSchemeOption.BLUE)
    val colorSchemeOptionFlow: StateFlow<ColorSchemeOption> = _colorSchemeOptionFlow.asStateFlow()

    fun setColorSchemeOption(option: ColorSchemeOption) {
        _colorSchemeOptionFlow.value = option
        scope.launch { repository.setColorSchemeOption(option) }
    }

    // ── Persistence ──

    /** Collect persisted theme/colour-scheme preferences. Launches two flows. */
    fun observePersisted() {
        scope.launch {
            try {
                repository.themeModeFlow.collect { mode -> _themeModeFlow.value = mode }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // flow ended
            }
        }
        scope.launch {
            try {
                repository.colorSchemeOptionFlow.collect { option ->
                    _colorSchemeOptionFlow.value = option
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // flow ended
            }
        }
    }
}
