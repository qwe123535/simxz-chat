package com.simplechat.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simplechat.app.App
import com.simplechat.app.data.StartupTrace
import com.simplechat.app.R
import com.simplechat.app.db.ConversationEntity
import com.simplechat.app.ui.chat.ChatScreen
import com.simplechat.app.ui.chat.ChatViewModel
import com.simplechat.app.ui.chat.HorizontalScrollRegions
import com.simplechat.app.ui.common.AppConfirmDialog
import com.simplechat.app.ui.common.AppIcons
import com.simplechat.app.ui.common.rememberHaptics
import com.simplechat.app.ui.common.AppShadow
import com.simplechat.app.ui.common.AppTextInputDialog
import com.simplechat.app.ui.common.MotionDurationMs
import com.simplechat.app.ui.common.MotionEasing
import com.simplechat.app.ui.history.ConversationSearchPage
import com.simplechat.app.ui.history.HistoryDrawerContent
import com.simplechat.app.ui.history.HistoryViewModel
import com.simplechat.app.ui.history.searchResultKey
import com.simplechat.app.ui.settings.SettingsScreen
import com.simplechat.app.ui.theme.LocalChatColors
import kotlinx.coroutines.launch

/**
 * 顶层路由 —— 也就是底部标签栏的两个标签。
 *
 * 只有两个：对话页与设置页。**搜索不在其中** —— 它不是"另一个页面"，
 * 而是抽屉自己的一种状态（见 [RootScreen] 的 `searchMode`）。
 *
 * 两者是**平级**的：切换走淡入淡出，没有"进出一层"的方向感，
 * 也就没有理由再记深度（早先的 `depth` 是给左右推入转场算方向的）。
 */
private enum class Route {
    CHAT,
    SETTINGS,
}

/** 抽屉平时的宽度。 */
private const val DrawerWidthFraction = 0.78f

/** 抽屉圆角。撑满时必须收成 0，否则全屏面板会在四角露出底下的内容。 */
private val DrawerCorner = 16.dp

/**
 * 顶层路由与侧拉抽屉。
 *
 * 刻意**不引 Navigation 组件** —— 页面就两个，一个状态枚举足够。
 *
 * ### 搜索为什么不是一条路由
 *
 * 点搜索框时**抽屉不关**：同一个面板从 78% 撑到满屏，内容换成搜索页。
 * 全程只有**一个**运动。
 *
 * 先前是"关掉抽屉（往左跑）+ 切到搜索路由（从右边推入）" ——
 * 两个方向相反的运动同时发生，看着就是各走各的。
 * 而搜索本来就该是"把抽屉拉开看清楚"，不是"跳到另一个页面"：
 * 它的搜索框、它的列表，都在同一块面板上。
 */
@Composable
fun RootScreen(chat: ChatViewModel? = null, history: HistoryViewModel? = null) {
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()

    val chatViewModel: ChatViewModel = chat ?: viewModel(
        factory = ChatViewModel.factory(
            chatApi = container.chatApi,
            settingsStore = container.settingsStore,
            repository = container.chatRepository,
            openCodeSessionId = container.openCodeSessionId,
        ),
    )
    val historyViewModel: HistoryViewModel = history ?: viewModel(
        factory = HistoryViewModel.factory(container.chatRepository, container.settingsStore),
    )

    val conversations by historyViewModel.conversations.collectAsStateWithLifecycle()
    val searchPage by historyViewModel.searchPage.collectAsStateWithLifecycle()
    val recentSearches by historyViewModel.recentSearches.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    /*
     * `rememberSaveable` 而不是 `remember`：换语言是 `recreate()` 整页重建，
     * 普通 `remember` 会把导航状态丢掉 —— 表现就是"切个语言，人被送回对话页"。
     * 存档过一次实例状态，重建后就还停在原来那一页（设置）。
     */
    var route by rememberSaveable { mutableStateOf(Route.CHAT) }

    /** 抽屉是否处于搜索态。见类注释：它是抽屉的状态，不是一条路由。 */
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var readingSearchResult by rememberSaveable { mutableStateOf(false) }
    var openingSearchResult by remember { mutableStateOf(false) }
    var selectedSearchResultKey by rememberSaveable { mutableStateOf<String?>(null) }
    val searchListState = rememberLazyListState()
    var horizontalRegionPressed by remember { mutableStateOf(false) }
    var drawerOrigin by remember { mutableStateOf(Offset.Zero) }

    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var confirmBatchDelete by remember { mutableStateOf(false) }

    // 首次进入播种内置预设
    remember { historyViewModel.seedPresets(); Unit }

    /*
     * 冷启动打点（见 `StartupTrace`）。
     * 两个点就够定位"白屏到底卡在哪一段"：首帧组合完 = UI 起来了，
     * 会话列表第一次有数据 = 数据层回来了。两者之间的差值就是数据库那一段。
     */
    val startupContext = LocalContext.current
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) { StartupTrace.mark(startupContext, "ui.root.firstCompose") }
    LaunchedEffect(conversations.isNotEmpty()) {
        if (conversations.isNotEmpty()) StartupTrace.mark(startupContext, "data.history.first")
    }

    /** 关抽屉时顺带退出多选，避免下次打开还停在选择态。 */
    fun closeDrawer() {
        historyViewModel.exitSelection()
        scope.launch { drawerState.close() }
    }

    /*
     * 返回键。优先级**从低到高**书写 ——
     * `OnBackPressedDispatcher` 是后添加的先响应，所以写在最后的优先级最高。
     *
     *   ① 设置页 → 回对话页
     *   ② 抽屉打开 → 关抽屉
     *   ③ 搜索态 → 退出搜索（抽屉保持打开）
     */

    BackHandler(enabled = route != Route.CHAT) {
        route = Route.CHAT
    }

    // Android's edge-back gesture and the back button return to the same search session.
    BackHandler(enabled = route == Route.CHAT && readingSearchResult && !drawerState.isOpen) {
        scope.launch { drawerState.open() }
    }

    /*
     * ⚠️ 这条不能省，而且**不是** Material3 帮我们做的。
     *
     * 实测：抽屉打开时按返回键，`ModalNavigationDrawer` 不消费、上面那条也
     * 是 disabled，事件一路落空到 Activity → 直接 `finish()`，用户被丢出 App。
     * （旧版本同样如此，只是没人从抽屉里按过返回键。）
     */
    BackHandler(enabled = drawerState.isOpen) {
        closeDrawer()
    }

    // 搜索态按返回 → 回抽屉列表。抽屉**保持打开** —— 用户本来就是从那儿进来的。
    BackHandler(enabled = searchMode && drawerState.isOpen) {
        historyViewModel.updateQuery("")
        searchMode = false
        readingSearchResult = false
        selectedSearchResultKey = null
    }

    // ── 抽屉：搜索时把面板从 78% 撑满 ──────────────────────
    val sheetFraction by animateFloatAsState(
        targetValue = if (searchMode) 1f else DrawerWidthFraction,
        animationSpec = tween(MotionDurationMs, easing = MotionEasing),
        label = "sheetFraction",
    )
    val sheetCorner by animateDpAsState(
        targetValue = if (searchMode) 0.dp else DrawerCorner,
        animationSpec = tween(MotionDurationMs, easing = MotionEasing),
        label = "sheetCorner",
    )
    val sheetShape = RoundedCornerShape(topEnd = sheetCorner, bottomEnd = sheetCorner)

    ModalNavigationDrawer(
        modifier = Modifier.onGloballyPositioned { drawerOrigin = it.boundsInRoot().topLeft }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    horizontalRegionPressed = HorizontalScrollRegions.any { it.contains(drawerOrigin + down.position) }
                    try {
                        do { val event = awaitPointerEvent(PointerEventPass.Initial) }
                        while (event.changes.any { it.pressed })
                    } finally { horizontalRegionPressed = false }
                }
            },
        drawerState = drawerState,
        // 撑满时禁止拖动：那时它已经不是"抽屉"了，往左拖会把它拖成一个奇怪的宽度
        gesturesEnabled = !horizontalRegionPressed && route == Route.CHAT && (!searchMode || (readingSearchResult && !drawerState.isOpen)),
        // 遮罩由下面 content 里的显式 scrim 画，内置 scrim 关掉，避免双层遮罩。
        scrimColor = Color.Transparent,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxWidth(sheetFraction)
                    .fillMaxHeight()
                    // 抽屉自己也要"浮起来"，否则右边缘是一道生硬的白灰交界
                    .shadow(
                        elevation = AppShadow.RaisedElevation,
                        shape = sheetShape,
                        clip = false,
                        ambientColor = AppShadow.RaisedAmbient,
                        spotColor = AppShadow.RaisedSpot,
                    ),
                drawerShape = sheetShape,
                // 与抽屉内容同色，否则状态栏那一条会露出 Material 的默认容器色（淡紫）
                drawerContainerColor = LocalChatColors.current.card,
                /*
                 * 系统栏内边距**由抽屉内容自己处理**（`HistoryDrawerContent` 里
                 * 有 `statusBarsPadding` / `navigationBarsPadding`）。
                 * 两边都加会得到双倍高度，顶部那一截还会是容器的底色 —— 实测就是
                 * 抽屉顶上多出一条淡紫色带。
                 */
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                /*
                 * 列表 ↔ 搜索交叉淡入。
                 *
                 * 宽度正在变，内容如果硬切会像"闪一下"；
                 * 叠一层淡入淡出，视觉上就是"同一块面板换了个内容"。
                 */
                Crossfade(
                    targetState = searchMode,
                    modifier = Modifier.fillMaxSize(),
                    animationSpec = tween(MotionDurationMs, easing = MotionEasing),
                    label = "drawerContent",
                ) { searching ->
                    if (searching) {
                        ConversationSearchPage(
                            hits = searchPage.hits,
                            hasMore = searchPage.hasMore,
                            onLoadMore = historyViewModel::loadMoreSearchResults,
                            listState = searchListState,
                            autoFocus = !readingSearchResult && drawerState.targetValue == DrawerValue.Open,
                            selectedResultKey = selectedSearchResultKey,
                            recentSearches = recentSearches,
                            onRecentClick = { keyword ->
                                selectedSearchResultKey = null
                                historyViewModel.updateQuery(keyword)
                            },
                            onClearRecent = historyViewModel::clearRecentSearches,
                            onRememberQuery = { historyViewModel.rememberSearch(historyViewModel.query) },
                            query = historyViewModel.query,
                            onQueryChange = { selectedSearchResultKey = null; historyViewModel.updateQuery(it) },
                            onBack = {
                                historyViewModel.updateQuery("")
                                searchMode = false
                                readingSearchResult = false
                                selectedSearchResultKey = null
                            },
                            onClick = { hit ->
                                val keyword = historyViewModel.query.trim()
                                if (!openingSearchResult) {
                                    openingSearchResult = true
                                    selectedSearchResultKey = hit.searchResultKey
                                    readingSearchResult = true
                                    historyViewModel.exitSelection()
                                    scope.launch {
                                        try {
                                            // Let the drawer and keyboard leave before locating visible text.
                                            drawerState.close()
                                            chatViewModel.openConversation(hit.conversation.id, hit.messageId, keyword)
                                        } finally { openingSearchResult = false }
                                    }
                                }
                            },
                            onRename = { renameTarget = it },
                            onTogglePin = { historyViewModel.togglePin(it) },
                            onDelete = { deleteTarget = it },
                        )
                    } else {
                        HistoryDrawerContent(
                            conversations = conversations,
                            currentConversationId = chatViewModel.currentConversationId,
                            selectionMode = historyViewModel.selectionMode,
                            selectedIds = historyViewModel.selectedIds.toList(),
                            onSearchClick = { readingSearchResult = false; searchMode = true },
                            onClick = { conversation ->
                                closeDrawer()
                                chatViewModel.openConversation(conversation.id)
                            },
                            onEnterSelection = { historyViewModel.enterSelection(it.id) },
                            onToggleSelection = { historyViewModel.toggleSelection(it) },
                            onRename = { renameTarget = it },
                            onTogglePin = { historyViewModel.togglePin(it) },
                            onDelete = { deleteTarget = it },
                            onExitSelection = { historyViewModel.exitSelection() },
                            onPinSelected = { pinned -> historyViewModel.pinSelected(pinned) },
                            onDeleteSelected = { confirmBatchDelete = true },
                            generatingConversationId = chatViewModel.generatingConversationId,
                            unseenDone = chatViewModel.unseenDone,
                            failedConversations = chatViewModel.failedConversations,
                        )
                    }
                }
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            /*
             * 底层内容：对话页 / 设置页，两个**平级的标签**。
             *
             * SaveableStateHolder 保住各页自己的滚动位置 —— 页面离开组合后
             * `rememberSaveable` 的存档会一并丢掉，于是"从设置返回对话就得重新滚到底"。
             * 存档本来就是导航组件该管的事，我们不引导航组件，就得自己接上。
             */
            val pageStates = rememberSaveableStateHolder()

            /*
             * 键盘弹出时把标签栏收起来。
             *
             * 聊天页的输入区已经用 imePadding 贴着键盘，再留一条 56dp 的标签栏，
             * 键盘上方就会多出一条什么也没有的带子 —— 而那正好是用户正在看的
             * 最后一行回复的位置。
             */
            val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        /*
                         * 标签栏在场时，导航栏内边距归标签栏自己吃。
                         *
                         * 消费掉之后 ChatScreen 里的 navigationBarsPadding() 归零，
                         * 否则输入区会被顶起一条导航栏的高度，标签栏和输入框之间
                         * 空出 20 多 dp 的灰带（双层内边距）。
                         */
                        .then(
                            if (imeVisible) Modifier
                            else Modifier.consumeWindowInsets(WindowInsets.navigationBars),
                        ),
                ) {
                    AnimatedContent(
                        targetState = route,
                        transitionSpec = {
                            /*
                             * 两个标签是**平级**的，不再用左右推入。
                             * 横滑会让人以为"还能再往右滑回去"，而这里没有第三页。
                             */
                            fadeIn(tween(MotionDurationMs, easing = MotionEasing))
                                .togetherWith(fadeOut(tween(MotionDurationMs, easing = MotionEasing)))
                        },
                        label = "route",
                    ) { target ->
                        pageStates.SaveableStateProvider(target) {
                            when (target) {
                                Route.CHAT -> ChatScreen(
                                    vm = chatViewModel,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onNewConversation = {
                                        readingSearchResult = false
                                        selectedSearchResultKey = null
                                        searchMode = false
                                        historyViewModel.updateQuery("")
                                        chatViewModel.newConversation()
                                    },
                                )

                                // 设置不再"盖"在对话上面：它是右边那个标签，
                                // 所以一级页没有返回箭头（见 SettingsScreen 的 topBar）
                                Route.SETTINGS -> SettingsScreen(
                                    onBack = { route = Route.CHAT },
                                    onOpenConversation = { id ->
                                        route = Route.CHAT
                                        chatViewModel.openConversation(id)
                                    },
                                )
                            }
                        }
                    }
                }

                if (!imeVisible) {
                    BottomTabBar(
                        current = route,
                        onSelect = { picked ->
                            if (picked != route) {
                                // 切走时把抽屉的搜索态收干净，回来不该还停在搜索页
                                readingSearchResult = false
                                selectedSearchResultKey = null
                                searchMode = false
                            }
                            route = picked
                        },
                    )
                }
            }

            /*
             * 抽屉遮罩 —— 显式画，不依赖 Material 内置 scrim。
             *
             * 内置 scrim 在本项目的组合下（自定义抽屉宽度 + 搜索态撑满）表现不稳定，
             * 用户反馈"遮罩只在点按钮时闪一下"。这里自己铺一层：抽屉一旦开始
             * 打开/拖动就淡入，点遮罩即关抽屉，行为与官方一致。
             */
            val scrim = LocalChatColors.current.scrim
            val drawerShown by remember(drawerState) {
                derivedStateOf { drawerState.targetValue != DrawerValue.Closed }
            }
            AnimatedVisibility(
                visible = drawerShown,
                enter = fadeIn(tween(MotionDurationMs, easing = MotionEasing)),
                exit = fadeOut(tween(MotionDurationMs, easing = MotionEasing)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrim)
                        .pointerInput(Unit) {
                            detectTapGestures { closeDrawer() }
                        },
                )
            }
        }
    }

    // ── 弹窗 ─────────────────────────────────────────────

    renameTarget?.let { target ->
        AppTextInputDialog(
            title = stringResource(R.string.hist_rename_title),
            initial = target.title,
            onDismiss = { renameTarget = null },
            onConfirm = { value ->
                historyViewModel.rename(target.id, value)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        AppConfirmDialog(
            title = stringResource(R.string.hist_delete_title),
            message = stringResource(R.string.hist_delete_body),
            confirmText = stringResource(R.string.hist_delete_confirm),
            destructive = true,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                // 删的正是当前正在看的会话 → 立刻回到空态，
                // 否则对话页还挂着一个已经不存在的会话
                if (chatViewModel.currentConversationId == target.id) {
                    chatViewModel.newConversation()
                }
                if (chatViewModel.hapticDelete) haptics.tick()
                historyViewModel.delete(target.id)
                deleteTarget = null
            },
        )
    }

    if (confirmBatchDelete) {
        val count = historyViewModel.selectedIds.size
        AppConfirmDialog(
            title = stringResource(R.string.hist_delete_selected_title, count),
            message = stringResource(R.string.hist_delete_selected_body),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onDismiss = { confirmBatchDelete = false },
            onConfirm = {
                // 当前会话也在被删之列 → 同样立刻回空态
                if (chatViewModel.currentConversationId in historyViewModel.selectedIds) {
                    chatViewModel.newConversation()
                }
                if (chatViewModel.hapticDelete) haptics.tick()
                historyViewModel.deleteSelected()
                confirmBatchDelete = false
            },
        )
    }
}

/**
 * 底部标签栏：对话 / 设置。
 *
 * 为什么把它当成"标签"而不是"从右边推入的一页"：设置与对话是**平级**的日常去处，
 * 谁都会来回切好几次（改个模型、回来接着聊）。每次切换都做一次左右推入，
 * 会让人以为自己在"进出一层"，而不是"换一个标签"—— 于是每次回对话都要重新定位。
 * 标签栏 + 淡入淡出，语义才对得上。
 *
 * 键盘弹出时由调用方整个收起来（见 [RootScreen] 里 `imeVisible` 的判断）。
 */
@Composable
private fun BottomTabBar(current: Route, onSelect: (Route) -> Unit) {
    val colors = LocalChatColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card)
            /*
             * 导航栏内边距由标签栏自己吃：上面那层 Box 已经 consume 掉了，
             * 但手势导航条仍然需要一段底部留白，否则文字贴在屏幕边缘。
             */
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TabBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(
                route = Route.CHAT,
                icon = AppIcons.Bubble,
                label = stringResource(R.string.nav_chat),
                current = current,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )
            TabItem(
                route = Route.SETTINGS,
                icon = AppIcons.Settings,
                label = stringResource(R.string.nav_settings),
                current = current,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 标签栏高度（不含导航栏内边距）。 */
private val TabBarHeight = 56.dp

/** 单个标签：图标 + 一行文字，选中用品牌色。 */
@Composable
private fun TabItem(
    route: Route,
    icon: ImageVector,
    label: String,
    current: Route,
    onSelect: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChatColors.current
    val selected = route == current
    val tint = if (selected) MaterialTheme.colorScheme.primary else colors.placeholder
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onSelect(route) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}
