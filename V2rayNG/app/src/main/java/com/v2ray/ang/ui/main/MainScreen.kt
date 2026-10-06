package com.v2ray.ang.ui.main
import androidx.compose.foundation.BorderStroke

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.verticalScrollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

private val SoftBackground  = Color(0xFFFBF4F8)
private val TopBarPurple    = Color(0xFF160B24)
private val BarSurfaceColor = Color(0xFFFFFFFF)
private val ActiveColor     = Color(0xFFD81B60)
private val InactiveColor   = Color(0xFF757575)
private val PillActiveBg    = Color(0x22D81B60)

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val displayText = mainViewModel.formatStatus(uiState.status)
    val selectedGuid = uiState.selectedGuid
    val doubleColumnDisplay = uiState.doubleColumnDisplay
    val confirmRemove = uiState.confirmRemove
    val shareQRCodeBitmap = uiState.shareQRCodeBitmap

    val isDarkTheme = LocalDarkTheme.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by rememberSaveable(stateSaver = ServerDeleteTarget.Saver) {
        mutableStateOf<ServerDeleteTarget?>(null)
    }

    var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }
    val removeServer: (String, String) -> Unit = { guid, profileName ->
        if (confirmRemove) {
            showRemoveConfirm = ServerDeleteTarget(guid, profileName)
        } else {
            onAction(MainAction.RemoveServer(guid))
        }
    }

    var currentBottomNav by rememberSaveable { mutableStateOf(0) }
    var showImportDropdown by remember { mutableStateOf(false) }
    val importScrollState = rememberScrollState()
    val maxMenuHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.7f)

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { groups.size.coerceAtLeast(1) }
    )

    val lazyListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val lazyGridStates = remember { mutableStateMapOf<String, LazyGridState>() }

    LaunchedEffect(groups) {
        val validGroupIds = groups.map { it.id }.toSet()
        lazyListStates.keys.retainAll(validGroupIds)
        lazyGridStates.keys.retainAll(validGroupIds)
    }

    LaunchedEffect(groups, uiState.selectedGroupId) {
        if (groups.isEmpty()) return@LaunchedEffect
        val selectedIndex = groups.indexOfFirst { it.id == uiState.selectedGroupId }
            .takeIf { it >= 0 } ?: 0
        if (!pagerState.isScrollInProgress && pagerState.settledPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    val latestGroups by rememberUpdatedState(groups)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val currentGroups = latestGroups
                if (page in currentGroups.indices) {
                    onAction(MainAction.SelectGroup(currentGroups[page].id))
                }
            }
    }

    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = { showDelAllConfirm = false },
        onConfirmDelAll = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = { showDelDuplicateConfirm = false },
        onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = { showDelInvalidConfirm = false },
        onConfirmDelInvalid = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) }
    )

    if (shareTarget != null) {
        val (guid, profile, more) = shareTarget!!
        ShareMethodDialog(
            guid = guid,
            profile = profile,
            more = more,
            onDismiss = { shareTarget = null },
            onAction = onAction,
            onRemove = removeServer,
        )
    }
    if (shareQRCodeBitmap != null) {
        QRCodeDialog(bitmap = shareQRCodeBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawerContent(
                drawerState = drawerState,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    onNavigate(route)
                }
            )
        }
    ) {
        Scaffold(
            containerColor = SoftBackground,
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            topBar = {
                Column(modifier = Modifier.background(TopBarPurple)) {
                    MainTopBar(
                        isLoading = isLoading,
                        showSearch = showSearch,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { query: String ->
                            searchQuery = query
                            onAction(MainAction.Search(query))
                        },
                        onSearchClose = {
                            searchQuery = ""
                            onAction(MainAction.Search(""))
                            showSearch = false
                        },
                        onSearchToggle = { show: Boolean -> showSearch = show },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onAction = onAction,
                        onMoreMenuAction = { action ->
                            when (action) {
                                MainMoreMenuAction.RestartService -> onAction(MainAction.RestartService)
                                MainMoreMenuAction.DeleteAll -> showDelAllConfirm = true
                                MainMoreMenuAction.DeleteDuplicate -> showDelDuplicateConfirm = true
                                MainMoreMenuAction.DeleteInvalid -> showDelInvalidConfirm = true
                                MainMoreMenuAction.ExportAll -> onAction(MainAction.ExportAll)
                                MainMoreMenuAction.LocateSelected -> onAction(MainAction.LocateSelectedServer)
                                MainMoreMenuAction.SortByTestResults -> onAction(MainAction.SortByTestResults)
                                MainMoreMenuAction.TestAll -> onAction(MainAction.TestAllServers)
                                MainMoreMenuAction.TestAllRealPing -> onAction(MainAction.TestRealAllServers)
                                MainMoreMenuAction.UpdateSubscriptions -> onAction(MainAction.UpdateSubscriptions)
                            }
                        }
                    )

                    if (groups.isNotEmpty() && currentBottomNav == 0) {
                        ScrollableTabRow(
                            selectedTabIndex = pagerState.currentPage.coerceIn(0, groups.lastIndex),
                            containerColor = TopBarPurple,
                            contentColor = Color.White,
                            edgePadding = 16.dp,
                            indicator = { tabPositions ->
                                val activeIdx = pagerState.currentPage.coerceIn(0, groups.lastIndex)
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeIdx]),
                                    color = Color(0xFFFF4081),
                                    height = 3.dp
                                )
                            }
                        ) {
                            groups.forEachIndexed { index, group ->
                                val isSelected = pagerState.currentPage == index
                                Tab(
                                    selected = isSelected,
                                    onClick = {
                                        scope.launch {
                                            pagerState.navigateToPageOptimized(index, true)
                                        }
                                    },
                                    text = {
                                        Text(
                                            text = group.remarks.ifEmpty { "Default" },
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else Color(0xFFD1C4E9),
                                            fontSize = 14.sp
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                                        // CONSOLE PING ALA DARKTUNNEL
                    if (isRunning) {
                        val pingLogs by mainViewModel.pingLogs.collectAsStateWithLifecycle()
                        val consoleListState = rememberLazyListState()

                        LaunchedEffect(pingLogs.size) {
                            if (pingLogs.isNotEmpty()) {
                                consoleListState.animateScrollToItem(pingLogs.size - 1)
                            }
                        }

                        Surface(
                            color = Color(0xFF10091D),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF332047)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            if (pingLogs.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "[LOG] Menunggu auto-ping...",
                                        color = Color(0xFF7E728F),
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = consoleListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    items(pingLogs) { item ->
                                        val logColor = when {
                                            item.delay < 0L -> Color(0xFFFF5252) // Merah (RTO/Gagal)
                                            item.delay < 150L -> Color(0xFF00E676) // Hijau (Ping Bagus)
                                            item.delay < 350L -> Color(0xFFFFD600) // Kuning (Sedang)
                                            else -> Color(0xFFFF5252) // Merah (Tinggi)
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "[${item.time}] ${item.msg}",
                                                color = logColor,
                                                fontSize = 11.sp,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                            if (item.delay >= 0L) {
                                                Text(
                                                    text = "${item.delay} ms",
                                                    color = logColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // BOTTOM NAVIGATION BAR
                    Box {
                        ModernBottomBar(
                            currentTab = currentBottomNav,
                            isRunning = isRunning,
                            onTabSelected = { tabIndex -> currentBottomNav = tabIndex },
                            onAddClicked = { showImportDropdown = true },
                            onConnectToggle = { onAction(MainAction.ToggleService) },
                            onOpenSettings = { onNavigate(MainDestination.Settings) }
                        )

                        DropdownMenu(
                            expanded = showImportDropdown,
                            onDismissRequest = { showImportDropdown = false },
                            scrollState = importScrollState,
                            containerColor = Color.White,
                            modifier = Modifier
                                .heightIn(max = maxMenuHeight)
                                .verticalScrollbar(importScrollState)
                        ) {
                            ImportMenuContent(
                                onAction = { action ->
                                    showImportDropdown = false
                                    onAction(action)
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (currentBottomNav == 0) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true,
                        beyondViewportPageCount = 1,
                        key = { page -> groups.getOrNull(page)?.id ?: "group-page-$page" }
                    ) { page ->
                        val group = groups.getOrNull(page) ?: return@HorizontalPager

                        GroupPagerPage(
                            groupId = group.id,
                            mainViewModel = mainViewModel,
                            selectedGuid = selectedGuid,
                            locateTarget = uiState.locateTarget,
                            doubleColumnDisplay = doubleColumnDisplay,
                            searchQuery = searchQuery,
                            lazyListStates = lazyListStates,
                            lazyGridStates = lazyGridStates,
                            onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                            onEditServer = { guid, profile -> onAction(MainAction.EditServer(guid, profile)) },
                            onShareServer = { guid, profile -> shareTarget = Triple(guid, profile, false) },
                            onMoreServer = { guid, profile -> shareTarget = Triple(guid, profile, true) },
                            onRemoveServer = removeServer,
                            contentPadding = PaddingValues(start = 6.dp, top = 10.dp, end = 6.dp, bottom = 16.dp)
                        )
                    }
                } else {
                    FullScreenLogScreen(isRunning = isRunning)
                }
            }
        }
    }
}

@Composable
private fun ModernBottomBar(
    currentTab: Int,
    isRunning: Boolean,
    onTabSelected: (Int) -> Unit,
    onAddClicked: () -> Unit,
    onConnectToggle: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = BarSurfaceColor,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    iconRes = R.drawable.ic_file_24dp,
                    label = "Config",
                    isSelected = currentTab == 0,
                    onClick = { onTabSelected(0) }
                )

                BottomNavItem(
                    iconRes = R.drawable.ic_add_24dp,
                    label = "Tambah",
                    isSelected = false,
                    onClick = onAddClicked
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isRunning) Color(0xFFD81B60) else Color(0xFF00C853))
                        .clickable { onConnectToggle() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isRunning) "■" else "▶",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isRunning) "STOP" else "START",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }

                BottomNavItem(
                    iconRes = R.drawable.ic_logcat_24dp,
                    label = "Live Log",
                    isSelected = currentTab == 1,
                    onClick = { onTabSelected(1) }
                )

                BottomNavItem(
                    iconRes = R.drawable.ic_settings_24dp,
                    label = "Setelan",
                    isSelected = false,
                    onClick = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    iconRes: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) PillActiveBg else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = if (isSelected) ActiveColor else InactiveColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) ActiveColor else InactiveColor
        )
    }
}

@Composable
private fun FullScreenLogScreen(isRunning: Boolean) {
    val logLines = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            logLines.clear()
            logLines.add("● Engine Standby. Tekan START untuk memantau log.")
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("logcat -v time -s GoLog:V v2ray:V Xray:V v2rayNG:V *:S")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (isActive) {
                    line = reader.readLine() ?: break
                    if (line.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            if (logLines.size > 250) logLines.removeAt(0)
                            logLines.add(line)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logLines.add("Error stream: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) listState.animateScrollToItem(logLines.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
            .background(Color(0xFF1E1E2E), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isRunning) "● LIVE ENGINE LOG" else "○ IDLE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = if (isRunning) Color(0xFFFF4081) else Color.Gray
            )
            Text(
                text = "${logLines.size} entries",
                fontSize = 11.sp,
                color = Color.LightGray
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = Color(0xFF33334D))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(logLines) { line ->
                Text(
                    text = line,
                    color = Color(0xFFECEFF1),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
