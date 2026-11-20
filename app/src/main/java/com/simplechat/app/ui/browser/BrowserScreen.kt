package com.simplechat.app.ui.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.simplechat.app.R
import com.simplechat.app.ui.common.AppIcons
import com.simplechat.app.ui.common.MenuShape
import com.simplechat.app.ui.theme.LocalChatColors
import com.simplechat.app.ui.common.IconCircleButton

/**
 * 内置浏览器：一个地址栏、一个 WebView，够用就好。
 *
 * 为什么现在做：以前点链接一律甩给系统浏览器 —— 出去再回来，对话上下文就断了，
 * 而"看一眼前面引用的那个网页"是对话里最高频的动作之一。
 *
 * 刻意**不做**多标签、书签、历史：那是浏览器的活。这里只解决"不离开 App 看一眼"。
 * 无痕模式下由调用方决定要不要清 Cookie —— 这里只管显示。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    url: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChatColors.current
    val context = LocalContext.current

    var address by remember { mutableStateOf(url) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = true) {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding(),
    ) {
        // ── 地址栏 ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconCircleButton(
                icon = AppIcons.ChevronLeft,
                contentDescription = stringResource(R.string.action_close),
                onClick = onClose,
            )
            Text(
                text = address,
                style = MaterialTheme.typography.bodySmall,
                color = colors.placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(MenuShape)
                    .background(colors.fieldBackground)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            IconCircleButton(
                icon = AppIcons.Upload,
                contentDescription = stringResource(R.string.action_share),
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, address)
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
                },
            )
        }

        if (progress in 0.01f..0.99f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = colors.divider,
            )
        }

        // ── 网页 ────────────────────────────────────────
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            progress = 0.05f
                            canGoBack = view.canGoBack()
                            canGoForward = view.canGoForward()
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            progress = 1f
                            canGoBack = view.canGoBack()
                            canGoForward = view.canGoForward()
                            url?.let { address = it }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress / 100f
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
            update = { view -> webView = view },
        )

        // ── 底部工具条 ───────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .navigationBarsPadding()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BrowserAction(
                icon = AppIcons.ChevronLeft,
                label = stringResource(R.string.browser_back),
                enabled = canGoBack,
                onClick = { webView?.goBack() },
            )
            BrowserAction(
                icon = AppIcons.ChevronRight,
                label = stringResource(R.string.browser_forward),
                enabled = canGoForward,
                onClick = { webView?.goForward() },
            )
            BrowserAction(
                icon = AppIcons.Refresh,
                label = stringResource(R.string.browser_reload),
                enabled = true,
                onClick = { webView?.reload() },
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webView = null
        }
    }
}

@Composable
private fun BrowserAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalChatColors.current
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else colors.placeholder.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (enabled) colors.fieldBackground else colors.fieldBackground.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(image = icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}
