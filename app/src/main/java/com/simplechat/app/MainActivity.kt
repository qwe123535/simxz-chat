package com.simplechat.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplechat.app.data.AppLanguage
import com.simplechat.app.data.AppForeground
import com.simplechat.app.data.AppLock
import com.simplechat.app.data.PendingOpen
import com.simplechat.app.service.CompletionNotifier
import com.simplechat.app.data.LocaleHelper
import com.simplechat.app.net.ProviderKind
import com.simplechat.app.ui.LockScreen
import com.simplechat.app.ui.OnboardingScreen
import com.simplechat.app.ui.RootScreen
import com.simplechat.app.ui.theme.AccentColor
import com.simplechat.app.ui.theme.ApplySystemBarAppearance
import com.simplechat.app.ui.theme.CustomColors
import com.simplechat.app.ui.theme.UiStyle
import com.simplechat.app.ui.theme.SimpleChatTheme
import com.simplechat.app.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // composable 的 `stringResource` 走这里的 resources，所以语言也得在这包一层。
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    /**
     * 退到后台就上锁。
     *
     * 用 `onStop` 而不是 `onPause`：下拉通知栏、系统弹窗都不该触发上锁，
     * 否则用户切出去看一眼消息回来就得再验一次。
     * 读的是缓存值（`lastKnown`），这条路径不能等一次 DataStore 读取。
     */
    override fun onStart() {
        super.onStart()
        // 生成完成的通知靠这个判断要不要发：用户正看着屏幕就不打扰
        AppForeground.update(true)
    }

    override fun onStop() {
        super.onStop()
        AppForeground.update(false)
        val store = (application as App).container.settingsStore
        if (store.lastKnown?.appLock == true) AppLock.lock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as App).container
        val store = container.settingsStore

        // 通知点进来：交给 UI 层去开那个会话（见 PendingOpen 的说明）
        handleOpenIntent(intent)

        val settingsStore = (application as App).container.settingsStore
        val systemFontScale = resources.configuration.fontScale.coerceAtLeast(0.01f)

        setContent {
            val stored by settingsStore.flow.collectAsStateWithLifecycle(
                initialValue = null,
            )

            val current = stored

            // 设置里换了语言：改完缓存整页重建，两个 context（App / Activity）重包一遍。
            // 首帧 `current` 还是 null（异步未到），那不是"语言变了"，不能重建。
            LaunchedEffect(current?.languageId) {
                if (current == null) return@LaunchedEffect
                val language = AppLanguage.fromId(current.languageId)
                if (language != LocaleHelper.currentLanguage(this@MainActivity)) {
                    LocaleHelper.onLanguageChanged(language)
                    recreate()
                }
            }

            val themeMode = runCatching {
                ThemeMode.valueOf(current?.themeModeId ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM)

            // 存的是枚举名；认不出来（比如回滚到旧版本后又被改坏）就回落到蓝色
            val accent = runCatching {
                AccentColor.valueOf(current?.accentColorId ?: AccentColor.BLUE.name)
            }.getOrDefault(AccentColor.BLUE)

            // 跟随系统时交给系统（倍率传 1 即"原样"）；不跟随时用用户值**替换**系统倍率，
            // 所以要先除掉系统那一层，否则会和系统设置相乘。
            val fontScale = when {
                current == null -> 1f
                current.followSystemFont -> 1f
                else -> current.fontScale / systemFontScale
            }

            // 自定义配色：存的是 ARGB 整数，null 表示"跟随主题"
            val custom = CustomColors(
                primary = current?.customPrimary?.let { Color(it) },
                userBubble = current?.customUserBubble?.let { Color(it) },
                pageBackground = current?.customPageBackground?.let { Color(it) },
                card = current?.customCard?.let { Color(it) },
                text = current?.customText?.let { Color(it) },
            )

            SimpleChatTheme(
                themeMode = themeMode,
                accent = accent,
                fontScale = fontScale,
                custom = custom,
                lineHeightScale = current?.lineHeightScale ?: 1f,
                bodyMonospace = current?.bodyMonospace ?: false,
                uiStyle = UiStyle.fromId(current?.uiStyleId),
                // 原版永远显示全部；DeepSeek 皮肤按开关决定
                showExtras = current?.showExtrasInDeepSeek ?: false,
            ) {
                ApplySystemBarAppearance()
                // 禁止截屏：系统级开关，改完立刻生效，不用重建 Activity
                LaunchedEffect(current?.secureScreen) {
                    if (current?.secureScreen == true) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
                val onboard = current
                val showOnboarding = onboard != null && !onboard.onboardingDone
                if (AppLock.locked) {
                    LockScreen()
                } else if (showOnboarding && onboard != null) {
                    /*
                     * 首次引导盖在对话页**之前**：它不是一个可以返回的页面，
                     * 走完就再也看不到（没有设置入口去重开，想重看只能清数据）。
                     * 放在 Activity 这一层而不是 RootScreen 里，是因为 RootScreen
                     * 要建两个 ViewModel、拉会话列表，引导页不需要那些。
                     */
                    OnboardingScreen(
                        stored = onboard,
                        provider = runCatching { ProviderKind.valueOf(onboard.providerId) }
                            .getOrDefault(ProviderKind.DEEPSEEK),
                        chatApi = container.chatApi,
                        onSaveKey = { store.setApiKey(it) },
                        onSaveModel = { model ->
                            store.setProvider(
                                providerId = onboard.providerId.ifBlank { ProviderKind.DEEPSEEK.name },
                                baseUrl = onboard.baseUrl,
                                modelId = model,
                            )
                        },
                        onDone = { store.setOnboardingDone(true) },
                    )
                } else {
                    RootScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    /** 从通知的 Intent 里取出「打开哪个会话、滚到哪条消息」。 */
    private fun handleOpenIntent(intent: Intent?) {
        val conversationId = intent?.getStringExtra(CompletionNotifier.EXTRA_CONVERSATION_ID) ?: return
        val messageId = intent.getStringExtra(CompletionNotifier.EXTRA_MESSAGE_ID)
        PendingOpen.post(conversationId, messageId)
    }
}
