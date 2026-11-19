package com.simplechat.app.ui.history

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simplechat.app.R
import com.simplechat.app.db.ConversationEntity
import com.simplechat.app.db.ConversationSearchHit
import com.simplechat.app.ui.common.AppIcons
import com.simplechat.app.ui.common.FieldThickness
import com.simplechat.app.ui.common.PillShape
import com.simplechat.app.ui.theme.LocalChatColors

/**
 * 全屏搜索页。
 *
 * 从抽屉的搜索框进入 —— 抽屉是「浏览」，搜索是「查找」，
 * 全屏让结果有足够空间，也避免在窄抽屉里挤着看。
 */
@Composable
fun ConversationSearchPage(
    hits: List<ConversationSearchHit>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onClick: (ConversationSearchHit) -> Unit,
    onRename: (ConversationEntity) -> Unit,
    onTogglePin: (ConversationEntity) -> Unit,
    onDelete: (ConversationEntity) -> Unit,
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    autoFocus: Boolean = true,
    selectedResultKey: String? = null,
    /** 最近搜过的关键词（最新在前）。空关键词时显示成一行可点的标签。 */
    recentSearches: List<String> = emptyList(),
    onRecentClick: (String) -> Unit = {},
    onClearRecent: () -> Unit = {},
    /** 用户点开某条结果时回调：由调用方决定要不要把当前关键词记进历史。 */
    onRememberQuery: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LocalChatColors.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    LaunchedEffect(autoFocus) { if (autoFocus) focusRequester.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.card),
    ) {
        Spacer(Modifier.statusBarsPadding())

        // ── 顶栏：返回 + 搜索输入 ─────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        keyboard?.hide()
                        onBack()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.ChevronLeft,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(PillShape)
                    .background(colors.fieldBackground)
                    .padding(horizontal = 14.dp, vertical = FieldThickness),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = AppIcons.Search,
                    contentDescription = null,
                    tint = colors.placeholder,
                    modifier = Modifier.size(17.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.placeholder,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = stringResource(R.string.action_clear),
                            tint = colors.placeholder,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        // ── 结果 ─────────────────────────────────────────
        if (hits.isEmpty()) {
            if (query.isBlank() && recentSearches.isNotEmpty()) {
                RecentSearches(
                    keywords = recentSearches,
                    onClick = onRecentClick,
                    onClear = onClearRecent,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (query.isBlank()) {
                            stringResource(R.string.search_prompt)
                        } else {
                            stringResource(R.string.search_no_result)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.placeholder,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
            ) {
                items(items = hits, key = { it.searchResultKey }) { hit ->
                    val conversation = hit.conversation
                    ConversationRow(
                        conversation = conversation,
                        current = hit.searchResultKey == selectedResultKey,
                        selectionMode = false,
                        selected = false,
                        onClick = {
                            focus.clearFocus()
                            keyboard?.hide()
                            onRememberQuery()
                            onClick(hit)
                        },
                        onEnterSelection = {},
                        onRename = { onRename(conversation) },
                        onTogglePin = { onTogglePin(conversation) },
                        onDelete = { onDelete(conversation) },
                        highlightQuery = query,
                        // 命中正文时补一行片段，能一眼看出"是在哪句话里命中的"
                        snippet = snippetForDisplay(hit.snippet, query),
                        showMultiSelect = false,
                        searchRole = hit.messageRole,
                        searchDate = hit.messageCreatedAt ?: conversation.updatedAt,
                        searchVariant = if (hit.variantCount > 1) "${(hit.variantIndex ?: 0) + 1}/${hit.variantCount}" else null,
                    )
                }
                if (hasMore) item {
                    TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.search_load_more))
                    }
                }
            }
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * 空关键词时的「最近搜索」。
 *
 * 用一行横向滚动的标签而不是纵向列表：搜索页空着的时候，用户的注意力在
 * 上方的输入框，历史只是"省一次打字"的快捷方式 —— 竖着排八行会把
 * 屏幕占满，看着像另一个页面。
 */
@Composable
private fun RecentSearches(
    keywords: List<String>,
    onClick: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChatColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_recent),
                style = MaterialTheme.typography.labelMedium,
                color = colors.placeholder,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) {
                Text(
                    text = stringResource(R.string.action_clear),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.placeholder,
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(items = keywords, key = { it }) { keyword ->
                Text(
                    text = keyword,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(PillShape)
                        .background(colors.fieldBackground)
                        .clickable { onClick(keyword) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}
