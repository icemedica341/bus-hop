package com.bushop.ui.screens

/**
 * ┌─ MainScreen ─────────────────────────────────────┐
 * │  app/ layer · Root composable                    │
 * │                                                   │
 * │  Scaffold ─→ TopAppBar + Content                 │
 * │    TopAppBar: sort, theme, settings, feature flags│
 * │    Content: LazyColumn of BusStopCards            │
 * │                                                   │
 * │  PullToRefresh ─→ viewModel.refreshAll()          │
 * │  Drag-to-reorder + Drag-to-delete                │
 * │  SettingsSheet ─→ theme, refresh interval, update │
 * │  FeatureFlagDialog ─→ long-press version label   │
 * └───────────────────────────────────────────────────┘
 */

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bushop.domain.model.ThemeMode
import com.bushop.ui.components.AddBusStopDialog
import com.bushop.ui.components.BusStopCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val savedStops by viewModel.savedStops.collectAsState()
    val sortByEarliest by viewModel.sortByEarliest.collectAsState()
    val apiStatus by viewModel.apiStatus.collectAsState()
    val pinnedServices by viewModel.pinnedServices.collectAsState()
    val themeMode by viewModel.themeModeFlow.collectAsState()
    val colorSchemeOption by viewModel.colorSchemeOptionFlow.collectAsState()
    val isIndexReady by viewModel.isIndexReady.collectAsState()
    val listState = rememberLazyListState()
    var showSettings by remember { mutableStateOf(false) }
    var showFeatureFlags by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()

    val newStopCode by viewModel.recentlyAddedStop.collectAsState()

    // Scroll to and highlight newly added stop
    LaunchedEffect(newStopCode) {
        if (newStopCode != null) {
            val index = savedStops.indexOfFirst { it.busStop.code == newStopCode }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
            kotlinx.coroutines.delay(2000)
            viewModel.clearNewStopHighlight()
        }
    }

    var deleteTarget by remember { mutableStateOf<String?>(null) }

    // Drag state
    var draggedCode by remember { mutableStateOf<String?>(null) }
    var isDragOverDeleteZone by remember { mutableStateOf(false) }
    var deleteZoneTopPx by remember { mutableStateOf(Float.POSITIVE_INFINITY) }

    val density = LocalDensity.current
    val dragItemHeightPx = remember { with(density) { 140.dp.toPx() } }

    // Scroll to top when a new stop is pinned
    var prevPinnedCount by remember { mutableStateOf(0) }
    val currentPinnedCount = savedStops.count { it.isPinned }
    LaunchedEffect(currentPinnedCount) {
        if (currentPinnedCount > prevPinnedCount && currentPinnedCount > 0) {
            listState.animateScrollToItem(0)
        }
        prevPinnedCount = currentPinnedCount
    }

    // Cancel previous snackbar when a new message arrives — prevents stale
    // queued notifications on rapid pin/unpin.
    // Using arrayOfNulls to avoid Compose MutableState overhead (snackbarJob
    // is never read for rendering, only for cancellation).
    val snackbarJob = remember { arrayOfNulls<Job>(1) }
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarJob[0]?.cancel()
            snackbarJob[0] =
                launch {
                    snackbarHostState.showSnackbar(message)
                }
        }
    }

    val onSortClick = remember { { viewModel.toggleSortOrder() } }
    val onThemeClick = remember { { viewModel.toggleThemeMode() } }
    val onRefreshClick = remember { { viewModel.refreshAll() } }
    val onSettingsClick = remember { { showSettings = true } }

    // ── Nearby stops permission launcher ──
    val nearbyLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { granted ->
            if (granted.values.any { it }) {
                viewModel.findNearbyStops()
            }
        }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.bushop.R.drawable.ic_directions_bus),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BusHop",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSortClick) {
                        Icon(
                            painter = painterResource(com.bushop.R.drawable.ic_sort),
                            contentDescription = "Sort",
                            tint = if (sortByEarliest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onThemeClick) {
                        Icon(
                            imageVector =
                            when (themeMode) {
                                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.DARK -> Icons.Default.DarkMode
                            },
                            contentDescription =
                            when (themeMode) {
                                ThemeMode.SYSTEM -> "Auto theme"
                                ThemeMode.LIGHT -> "Light mode"
                                ThemeMode.DARK -> "Dark mode"
                            },
                            tint =
                            when (themeMode) {
                                ThemeMode.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
                                ThemeMode.LIGHT -> MaterialTheme.colorScheme.onSurfaceVariant
                                ThemeMode.DARK -> MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    IconButton(onClick = onRefreshClick) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
                colors =
                TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddStopDialog() },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add bus stop",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    start = paddingValues.calculateLeftPadding(LayoutDirection.Ltr),
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateRightPadding(LayoutDirection.Ltr),
                    bottom = paddingValues.calculateBottomPadding(),
                ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ApiStatusBanner(
                    status = apiStatus,
                    onDismiss = { viewModel.dismissApiBanner() },
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (!isIndexReady) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Loading stops…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                )
                            }
                        }
                    } else if (savedStops.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "No bus stops saved yet.\nTap + to add your first stop.",
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        val stopListContent: @Composable () -> Unit = {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = 40.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(
                                    items = savedStops,
                                    key = { it.busStop.code },
                                ) { stopWithArrivals ->
                                    val stopCode = stopWithArrivals.busStop.code
                                    val isNewlyAdded = stopWithArrivals.busStop.code == newStopCode
                                    BusStopCard(
                                        isNewlyAdded = isNewlyAdded,
                                        modifier = Modifier.animateItem(placementSpec = tween(durationMillis = 0)),
                                        stop = stopWithArrivals,
                                        onRefresh = remember(stopCode) { { viewModel.refreshArrivals(stopCode) } },
                                        onToggleCollapse = remember(stopCode) { { viewModel.toggleCollapse(stopCode) } },
                                        onTogglePin = remember(stopCode) { { viewModel.togglePin(stopCode) } },
                                        onDelete = remember(stopCode) { { deleteTarget = stopCode } },
                                        onTogglePinService =
                                        remember(stopCode) {
                                            { serviceNo ->
                                                viewModel.togglePinService(stopCode, serviceNo)
                                            }
                                        },
                                        pinnedServiceNos =
                                        remember(stopCode, pinnedServices) {
                                            pinnedServices
                                                .filter { it.startsWith("$stopCode:") }
                                                .map { it.substringAfter(":") }
                                                .toSet()
                                        },
                                        onMoveStop = { delta ->
                                            viewModel.moveStop(stopWithArrivals.busStop.code, delta)
                                        },
                                        onDragStart = { code ->
                                            draggedCode = code
                                            isDragOverDeleteZone = false
                                        },
                                        onDragProgress = { code, lastTotalY, draggedCenterY ->
                                            if (draggedCode == code) {
                                                // Delete zone detection (uses same card-center-in-zone check as deletion)
                                                if (deleteZoneTopPx.isFinite()) {
                                                    isDragOverDeleteZone = draggedCenterY >= deleteZoneTopPx
                                                }
                                            }
                                        },
                                        onDragEnd = { code, lastTotalY ->
                                            if (isDragOverDeleteZone) {
                                                viewModel.removeBusStop(code)
                                            } else if (lastTotalY != 0f) {
                                                val delta = (lastTotalY / dragItemHeightPx).toInt()
                                                if (delta != 0) viewModel.moveStop(code, delta)
                                            }
                                            draggedCode = null
                                            isDragOverDeleteZone = false
                                        },
                                        isDeleteTargeted = draggedCode == stopWithArrivals.busStop.code && isDragOverDeleteZone,
                                    )
                                }
                            }
                        }

                        PullToRefreshBox(
                            isRefreshing = viewModel.isRefreshing,
                            onRefresh = {
                                if (draggedCode == null) viewModel.refreshAll()
                            },
                            state = pullToRefreshState,
                            indicator = {
                                PullToRefreshDefaults.Indicator(
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    isRefreshing = viewModel.isRefreshing,
                                    state = pullToRefreshState,
                                    color = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                )
                            },
                        ) {
                            stopListContent()
                        }
                    }
                } // close weight(1f) Box
            } // close Column

            // ── First-time hint (tap to expand, auto-dismiss 5s, never again) ──
            val hintVisible = !viewModel.hasSeenDragHint && savedStops.isNotEmpty() && draggedCode == null
            LaunchedEffect(hintVisible) {
                if (hintVisible) {
                    delay(5000)
                    viewModel.dismissHint()
                }
            }
            AnimatedVisibility(
                visible = hintVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Box(
                    modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { viewModel.dismissHint() }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Tip: Tap a bus stop to see arrival times",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            if (viewModel.lastUpdatedAll > 0) {
                val pillBg by animateColorAsState(
                    targetValue =
                    if (viewModel.isRefreshing) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    },
                    animationSpec = tween(durationMillis = 300),
                )
                Row(
                    modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(pillBg)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text =
                            buildString {
                                append("Updated: ${formatLastUpdated(viewModel.lastUpdatedAll)}")
                                if (savedStops.any { it.isStale }) {
                                    append("  •  Some data may be stale")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // ── Drag-to-delete zone overlay ──
            if (draggedCode != null) {
                val deleteZoneColor =
                    if (isDragOverDeleteZone) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
                    }
                Box(
                    modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(80.dp)
                        .onGloballyPositioned { coordinates ->
                            deleteZoneTopPx = coordinates.positionInRoot().y
                        }.background(deleteZoneColor)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = if (isDragOverDeleteZone) "Release to delete" else "Drag here to delete",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        } // close outer Box
    } // close Scaffold content

    if (showFeatureFlags) {
        FeatureFlagDialog(onDismiss = { showFeatureFlags = false })
    }

    if (showSettings) {
        SettingsSheet(
            currentTheme = themeMode,
            currentInterval = viewModel.autoRefreshIntervalSeconds,
            currentColorScheme = colorSchemeOption,
            onThemeChange = { viewModel.setThemeMode(it) },
            onColorSchemeChange = { viewModel.setColorSchemeOption(it) },
            onIntervalChange = { seconds ->
                viewModel.setAutoRefreshInterval(seconds)
                showSettings = false
            },
            onCheckUpdate = { viewModel.checkForUpdate() },
            isCheckingUpdate = viewModel.isCheckingUpdate,
            isDownloadingUpdate = viewModel.isDownloadingUpdate,
            updateInfo = viewModel.updateInfo,
            onDownloadUpdate = { viewModel.downloadAndInstallUpdate() },
            onDismiss = { showSettings = false },
            onOpenFeatureFlags = {
                showSettings = false
                showFeatureFlags = true
            },
        )
    }

    if (viewModel.addStopDialogVisible) {
        val searchResults by viewModel.searchResults.collectAsState()

        AddBusStopDialog(
            error = viewModel.addStopError,
            isLoading = viewModel.addStopIsLoading,
            searchResults = searchResults,
            onSearchQueryChanged = { query ->
                viewModel.searchBusStops(query)
            },
            onFindNearby = {
                nearbyLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION))
            },
            nearbyStops = viewModel.nearbyStops,
            isLoadingNearby = viewModel.isLoadingNearby,
            nearbyError = viewModel.nearbyError,
            onDismiss = {
                viewModel.clearNearby()
                viewModel.hideAddStopDialog()
            },
            onConfirm = { code, name ->
                // If name equals code (manual entry), try to find the real name
                val resolvedName =
                    if (name == code) {
                        viewModel.findBusStopByCode(code)?.name ?: name
                    } else {
                        name
                    }
                viewModel.addBusStop(code, resolvedName)
            },
            randomHint = viewModel.randomHint,
        )
    }

    if (deleteTarget != null) {
        val targetStop = savedStops.find { it.busStop.code == deleteTarget }
        val isPinned = targetStop?.isPinned == true

        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (isPinned) "Pinned Bus Stop" else "Delete Bus Stop?") },
            text = {
                Text(
                    if (isPinned) {
                        "Bus stop $deleteTarget is pinned. Unpin first before deleting."
                    } else {
                        "Are you sure you want to delete bus stop $deleteTarget? This cannot be undone."
                    },
                )
            },
            confirmButton = {
                val target = deleteTarget ?: return@AlertDialog
                if (isPinned) {
                    TextButton(
                        onClick = {
                            viewModel.togglePin(target)
                            deleteTarget = null
                        },
                    ) {
                        Text("Unpin")
                    }
                } else {
                    TextButton(
                        onClick = {
                            viewModel.removeBusStop(target)
                            deleteTarget = null
                        },
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
