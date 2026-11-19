package com.simplechat.app.ui.settings

import android.content.Intent
import java.io.File
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import com.simplechat.app.BuildConfig
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.simplechat.app.ui.common.MenuShape
import com.simplechat.app.ui.common.ContainerShape
import com.simplechat.app.ui.common.BubbleShapeOut
import com.simplechat.app.App
import com.simplechat.app.R
import com.simplechat.app.data.ApiKey
import com.simplechat.app.data.CrashDiagnostics
import com.simplechat.app.data.ErrorLog
import com.simplechat.app.data.MemoryEntry
import com.simplechat.app.data.SlashCommand
import com.simplechat.app.db.ConversationEntity
import com.simplechat.app.db.FavoriteMessageRow
import com.simplechat.app.ui.common.AppPairInputDialog
import com.simplechat.app.net.UpdateChecker
import com.simplechat.app.data.AppLanguage
import com.simplechat.app.data.ImportMode
import com.simplechat.app.data.Res
import com.simplechat.app.data.SettingsStore
import com.simplechat.app.data.normalizeLoadingWord
import com.simplechat.app.data.StoredSettings
import com.simplechat.app.data.StorageUsage
import com.simplechat.app.data.ApiProfile
import com.simplechat.app.data.userMessage
import com.simplechat.app.data.copyExportFile
import com.simplechat.app.data.stageArchive
import com.simplechat.app.net.ModelCatalog
import com.simplechat.app.net.DeepSeekTts
import com.simplechat.app.net.ProviderKind
import com.simplechat.app.ui.common.AppIcons
import com.simplechat.app.ui.common.AppConfirmDialog
import com.simplechat.app.ui.common.AppTextInputDialog
import com.simplechat.app.ui.common.AppMenu
import com.simplechat.app.ui.common.AppMenuCheck
import com.simplechat.app.ui.common.AppMenuItem
import com.simplechat.app.ui.chat.Preset
import com.simplechat.app.ui.chat.SpeechVoice
import com.simplechat.app.ui.chat.loadSpeechVoices
import com.simplechat.app.ui.chat.rememberSpeechOutput
import com.simplechat.app.ui.common.AppSlider
import com.simplechat.app.ui.common.AppSwitch
import com.simplechat.app.ui.common.AppColorPickerDialog
import com.simplechat.app.ui.common.CapsuleButton
import com.simplechat.app.ui.common.IconCircleButton
import com.simplechat.app.ui.common.PillShape
import com.simplechat.app.ui.common.screenTransform
import com.simplechat.app.ui.theme.AccentColor
import com.simplechat.app.ui.theme.LocalChatColors
import com.simplechat.app.ui.theme.LocalIsDarkTheme
import com.simplechat.app.ui.theme.LocalUiStyle
import com.simplechat.app.ui.theme.UiStyle
import com.simplechat.app.ui.theme.ThemeMode
import com.simplechat.app.ui.theme.primary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 设置页内的一级 / 二级页面。
 *
 * [depth] 只用来判断转场方向：二级页从右侧进来，返回时原路退回。
 */
private enum class SettingsPage(val depth: Int) {
    ROOT(0),
    DATA(1),
    API(1),
    API_PROFILES(2),
    PRESETS(1),
    FONT(1),
    /** 朗读音色（系统 TTS）。 */
    VOICE(1),
    ABOUT(1),
    /** 开源许可与版权清单（关于 → 二级）。 */
    LICENSES(2),

    /** 触感反馈：三个动作各一个开关。 */
    INTERACTION(1),

    /** 存储占用与清理。 */
    STORAGE(1),

    /** 自定义配色：五项颜色逐项可改。 */
    CUSTOM_THEME(1),

    /** 阅读排版：行高与正文等宽。 */
    READING(1),

    /** AI 行为：语气、思考显示、学习模式、极简模式。 */
    BEHAVIOR(1),

    /** 输出与预览：代码块换行、思考块展开、链接打开方式。 */
    OUTPUT(1),

    /** 工具：联网搜索服务商、图像理解模型。 */

    /** 统计：按模型的请求数与 token 用量。 */
    STATS(1),

    /** 记忆：长期记忆条目（增删改）。 */
    MEMORY(1),

    /** 错误日志：最近的崩溃与网络错误。 */
    LOGS(1),

    /** 后台保护：前台服务、WakeLock、电池优化、音频保活。 */
    KEEPALIVE(1),

    /** 安全：应用锁、禁止截屏、明文 HTTP。 */
    SECURITY(1),

    /** 快捷指令：输入框打 / 调用的可编辑列表。 */
    SLASH_COMMANDS(1),
    LOADING_WORDS(1),

    /** 收藏夹：收藏的消息与会话。 */
    FAVORITES(1),
}

private sealed interface SettingsDialog {
    data object Provider : SettingsDialog
    data object BaseUrl : SettingsDialog
    data object Model : SettingsDialog
    /** 记忆条目编辑（`null` = 新增）。 */
    data class MemoryEditor(val entry: MemoryEntry?) : SettingsDialog
    /** 图像理解模型 id（空 = 跟随当前对话模型）。 */
    /** 手动填写模型 id：端点没实现 `/models`、或列表里没有目标模型时的出口。 */
    data object ModelManual : SettingsDialog
    data object ApiKey : SettingsDialog
    data class ApiProfileName(val profile: ApiProfile? = null, val copyCurrent: Boolean = false) : SettingsDialog
    data class DeleteApiProfile(val profile: ApiProfile) : SettingsDialog
    data object DeleteAll : SettingsDialog

    /** 清理存储：只清残留附件与临时缓存，见 [StorageUsage]。 */
    data object ClearStorage : SettingsDialog

    /** 给某一项自定义配色取色。 */
    data class PickColor(val slot: SettingsStore.CustomColorSlot) : SettingsDialog

    /** 清空搜索历史（只删关键词，不碰会话）。 */
    data object ClearSearchHistory : SettingsDialog

    /** 新增 / 编辑一条快捷指令；`original` 为 null 表示新增。 */
    data class SlashEditor(val original: SlashCommand? = null) : SettingsDialog

    /** 删除一条快捷指令前的确认。 */
    data class DeleteSlash(val command: SlashCommand) : SettingsDialog

    /** [original] 为空 = 新增。 */
    data class LoadingWordEditor(val original: String? = null) : SettingsDialog

    data class DeleteLoadingWord(val word: String) : SettingsDialog
    data object ImportMode : SettingsDialog
    /** 协议通道（自动 / OpenAI 兼容 / Anthropic 兼容）。 */
    data object ApiStyle : SettingsDialog
    /** 地区（时间地点提醒里的地点那一项）。 */
    data object Location : SettingsDialog
    /** 编辑预设；[preset] 为 null 表示新建。 */
    data class PresetEditor(val preset: Preset?) : SettingsDialog
    data class DeletePreset(val preset: Preset) : SettingsDialog
}

/**
 * 协议通道的**可选档位**（持久化的是枚举名）。
 *
 * 与 `net.ApiStyle` 刻意分开：那个是"实际走哪条"，只有两档；
 * 这里的 `AUTO` 是用户意图（"你看着办"），落库后由 `ApiStyle.resolve` 按端点解析。
 * 把 `AUTO` 塞进 `ApiStyle` 会让"实际通道"变成可选值，到处都是 null 判断。
 */
private enum class ApiStylePreference(val labelRes: Int) {
    AUTO(R.string.api_style_auto),
    OPENAI(R.string.api_style_openai),
    ANTHROPIC(R.string.api_style_anthropic),
    ;

    companion object {
        fun fromId(id: String?): ApiStylePreference =
            entries.firstOrNull { it.name == id } ?: AUTO
    }
}

/** 字号档位。与官方一致：中间一档为「标准」。 */private val fontSizeSteps = listOf(
    0.85f to R.string.font_step_small,
    0.925f to R.string.font_step_smaller,
    1.0f to R.string.font_step_standard,
    1.10f to R.string.font_step_larger,
    1.20f to R.string.font_step_large,
    1.30f to R.string.font_step_largest,
)

/**
 * 设置页。
 *
 * 一级列表只放**入口**，具体编辑都进二级页面 —— 一级列表保持一屏内可扫完。
 * 外观例外：只有三个互斥选项，弹个菜单比切页面快。
 *
 * 每个一级入口配**各自不同**的图标（数据管理 / API 配置 / 外观 / 字体大小 /
 * 自动折叠思考 / 关于）；二级页面里的行不再配图标，避免图标语义打架。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 收藏夹里点开一个会话：交给上层切到聊天标签并打开它。 */
    onOpenConversation: (String) -> Unit = {},
) {
    val colors = LocalChatColors.current
    val context = LocalContext.current
    val container = (context.applicationContext as App).container
    val scope = rememberCoroutineScope()
    val store = container.settingsStore
    val repository = container.chatRepository

    var stored by remember { mutableStateOf(StoredSettings()) }
    // rememberSaveable：换语言会 recreate()（见 MainActivity），二级页要停在原地
    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var profileError by remember { mutableStateOf<String?>(null) }
    fun updateProfile(action: suspend () -> Unit) {
        scope.launch {
            try { action(); profileError = null }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { profileError = error.userMessage() }
        }
    }
    var dataMessage by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<File?>(null) }
    // 导入进度：null = 没在导入；0..1 = 解析进度（拷贝阶段是 indeterminate）
    val importProgress = remember { MutableStateFlow<Float?>(null) }
    val importPercent by importProgress.collectAsState()
    // 读文件 / 开包阶段：没有可用进度，界面给循环进度条 + 「读取中」
    var importReading by remember { mutableStateOf(false) }
    val archiveProgress = remember { MutableStateFlow<Float?>(null) }
    val archivePercent by archiveProgress.collectAsState()
    var archiveSaving by remember { mutableStateOf(false) }
    var pendingArchive by remember { mutableStateOf<File?>(null) }
    DisposableEffect(Unit) {
        onDispose { pendingArchive?.delete(); pendingImport?.delete() }
    }
    var presets by remember { mutableStateOf<List<Preset>>(emptyList()) }
    // 存储占用：进页面才测（要扫文件系统，别在启动路径上做）
    var storageReport by remember { mutableStateOf<StorageUsage.Report?>(null) }
    var storageClearing by remember { mutableStateOf(false) }
    var storageMessage by remember { mutableStateOf<String?>(null) }

    /*
     * 朗读音色。
     *
     * - 清单要**临时起一个 TTS 引擎**才拿得到（见 loadSpeechVoices），所以只在
     *   进二级页时读一次，读到就缓存到页面关闭；
     * - [speaker] 只用于「试听」与选完顺手念一句，不参与对话页的朗读。
     */
    var speechVoices by remember { mutableStateOf<List<SpeechVoice>?>(null) }

    /*
     * DeepSeek 官方 TTS（/v1/tools/tts，音色 stella / tide）。
     *
     * 只在「服务商 = DeepSeek 官方 + Base URL 指向官方域 + Key 已填」时可用 ——
     * 那个端点是官方域专有的。可用时列在音色页最前面；不可用时整块不出现，
     * 只留一句说明（不支持的 API 不做入口，也不在点了之后才报错）。
     */
    val officialTtsConfig = remember(stored.providerId, stored.apiKey) {
        if (DeepSeekTts.isOfficialTtsVoiceAvailable(stored.providerId, stored.apiKey)) {
            DeepSeekTts.Config(stored.apiKey)
        } else {
            null
        }
    }
    val officialVoices = remember(officialTtsConfig) {
        if (officialTtsConfig == null) {
            emptyList()
        } else {
            DeepSeekTts.Voice.entries.map { voice ->
                SpeechVoice(
                    name = DeepSeekTts.storageNameOf(voice.id),
                    label = Res.get(voice.labelRes),
                    // 官方合成走网络（不是设备引擎），标"需联网"符合用户预期
                    needsNetwork = true,
                )
            }
        }
    }

    val speaker = rememberSpeechOutput(stored.ttsVoiceName, officialTtsConfig)
    /** 朗读相关的报错（引擎缺失 / 合成失败），显示在音色页的说明位置。 */
    var speechError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(speaker.error) {
        speaker.error?.let {
            speechError = it
            speaker.consumeError()
        }
    }
    LaunchedEffect(page) {
        if (page == SettingsPage.VOICE && speechVoices == null) {
            speechError = null
            /*
             * 加超时兜底：个别设备的 TTS 引擎初始化回调可能**永远不来**
             * （引擎被禁用 / 服务被冻结）。不设限的话这一页会永远停在「正在读取…」，
             * 而用户完全不知道该怎么办。超时按"没有可用引擎"处理，至少给一句提示。
             */
            speechVoices = withTimeoutOrNull(TtsVoiceLoadTimeoutMs) {
                loadSpeechVoices(context)
            } ?: emptyList()
        }
    }

    /*
     * 统计 / 记忆 / 错误日志：三页的数据都**进页才读**。
     * 统计要扫一遍 messages 表，常驻读取等于每次进设置都全表聚合一次。
     */
    var statsRows by remember { mutableStateOf<List<com.simplechat.app.db.ModelUsageRow>?>(null) }
    var memories by remember { mutableStateOf<List<MemoryEntry>>(emptyList()) }
    // 快捷指令与收藏：前者随设置变，后者只存 id、内容要现取
    var slashCommands by remember { mutableStateOf<List<SlashCommand>>(emptyList()) }
    var favoriteMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoriteConversationIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoriteMessages by remember { mutableStateOf<List<FavoriteMessageRow>>(emptyList()) }
    var favoriteConversations by remember { mutableStateOf<List<ConversationEntity>>(emptyList()) }
    var logText by remember { mutableStateOf("") }
    var logCrash by remember { mutableStateOf("") }

    LaunchedEffect(page) {
        when (page) {
            SettingsPage.STATS -> statsRows = runCatching { repository.modelUsage() }
                .getOrDefault(emptyList())
            SettingsPage.LOGS -> {
                logText = ErrorLog.read(context)
                logCrash = CrashDiagnostics.file(context)?.readText().orEmpty()
            }
            else -> Unit
        }
    }
    LaunchedEffect(Unit) { store.memories.collect { memories = it } }
    LaunchedEffect(Unit) { store.slashCommands.collect { slashCommands = it } }
    LaunchedEffect(Unit) { store.favoriteMessages.collect { favoriteMessageIds = it } }
    LaunchedEffect(Unit) { store.favoriteConversations.collect { favoriteConversationIds = it } }
    // 收藏只存 id，内容要现取；进了收藏夹页才查，避免设置页一启动就扫库
    LaunchedEffect(page, favoriteMessageIds) {
        if (page != SettingsPage.FAVORITES) return@LaunchedEffect
        favoriteMessages = runCatching { repository.favoriteMessages(favoriteMessageIds.toList()) }
            .getOrDefault(emptyList())
    }
    LaunchedEffect(page, favoriteConversationIds) {
        if (page != SettingsPage.FAVORITES) return@LaunchedEffect
        favoriteConversations = runCatching { repository.conversationsByIds(favoriteConversationIds.toList()) }
            .getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) { store.flow.collect { stored = it } }
    LaunchedEffect(Unit) { repository.observePresets().collect { presets = it } }

    // 二级页面按返回键先回一级，而不是直接退出设置
    fun previousPage() { page = if (page == SettingsPage.API_PROFILES) SettingsPage.API else SettingsPage.ROOT }
    BackHandler(enabled = page != SettingsPage.ROOT) { previousPage() }
    LaunchedEffect(stored.activeApiProfileId, stored.baseUrl) { testResult = null }

    val provider = runCatching { ProviderKind.valueOf(stored.providerId) }
        .getOrDefault(ProviderKind.DEEPSEEK)

    /**
     * 拉一次模型列表，并把它落库（设置页「模型」选择器读的就是这份）。
     *
     * 手动点「测试连接」和进页面时自动拉**走同一段逻辑** —— 两处各写一遍的话，
     * "失败怎么提示""拉到的列表要不要落库"迟早会走岔（早先就只报了个条数、
     * 列表丢掉，于是自定义端点明明"连接成功"，点开「模型」却是一个白框）。
     */
    suspend fun refreshModels() {
        if (testing) return
        testing = true
        testResult = null
        val source = stored
        try {
            val result = container.chatApi.fetchModels(
                baseUrl = source.baseUrl.ifBlank { provider.defaultBaseUrl },
                apiKey = source.apiKey,
            )
            result.onSuccess { ids ->
                store.setModelIds(ids, source.activeApiProfileId, source.baseUrl)
            }
            // 拉取期间用户可能已经切走了服务商 / 改了地址：那条结果不再属于当前页
            if (stored.activeApiProfileId == source.activeApiProfileId &&
                stored.baseUrl == source.baseUrl
            ) {
                testResult = result.fold(
                    onSuccess = { Res.get(R.string.api_test_ok, it.size) },
                    onFailure = { Res.get(R.string.api_test_failed, it.userMessage()) },
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            profileError = error.userMessage()
        } finally {
            testing = false
        }
    }

    /*
     * 进「API 配置」页时，模型列表还是空的就自动拉一次。
     *
     * 本地服务（Ollama / LM Studio）装好往往只有一个默认模型，用户不知道要手打名字 ——
     * 自动拉一次，选择器里就有人可选了。
     *
     * 三种情况不拉：
     * - 列表已有内容：不打扰，换服务端模型可以手动点「测试连接」；
     * - 地址为空：还没填，拉必然失败，失败提示纯属噪音；
     * - 需要 Key 但没填：先去填 Key，这里只会拿到 401。
     * 失败**不弹窗**，就留在「测试连接」下面那一行小字里。
     */
    LaunchedEffect(page, stored.activeApiProfileId, stored.baseUrl, stored.modelIds.isEmpty()) {
        if (page != SettingsPage.API) return@LaunchedEffect
        if (stored.modelIds.isNotEmpty()) return@LaunchedEffect
        if (stored.baseUrl.isBlank() && provider.defaultBaseUrl.isBlank()) return@LaunchedEffect
        if (!stored.hasKey && !provider.apiKeyOptional) return@LaunchedEffect
        refreshModels()
    }

    val themeMode = runCatching { ThemeMode.valueOf(stored.themeModeId) }
        .getOrDefault(ThemeMode.SYSTEM)

    val accentColor = runCatching { AccentColor.valueOf(stored.accentColorId) }
        .getOrDefault(AccentColor.BLUE)

    // ── 文件读写：导出 / 导入 ─────────────────────────────

    fun savePreparedExport(uri: Uri?) {
        val staged = pendingArchive
        pendingArchive = null
        if (uri == null) {
            staged?.delete()
            archiveProgress.value = null
            dataMessage = null
            return
        }
        scope.launch {
            archiveSaving = true
            archiveProgress.value = 0f
            var saved = false
            try {
                checkNotNull(staged) { Res.get(R.string.export_failed_short) }
                withContext(Dispatchers.IO) {
                    copyExportFile(staged, openDestination = {
                        context.contentResolver.openOutputStream(uri, "wt")
                            ?: error(Res.get(R.string.error_write_failed))
                    }, onProgress = { archiveProgress.value = it })
                }
                saved = true
                dataMessage = Res.get(R.string.export_done)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                dataMessage = Res.get(R.string.export_failed, error.userMessage())
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    staged?.delete()
                    if (!saved) runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                }
                archiveSaving = false
                archiveProgress.value = null
            }
        }
    }

    fun prepareExport(extension: String, launch: (String) -> Unit, generate: suspend (File) -> Unit) {
        if (archiveProgress.value != null || importProgress.value != null || importReading) return
        archiveSaving = false
        archiveProgress.value = 0f
        dataMessage = null
        scope.launch {
            var staged: File? = null
            try {
                val target = File.createTempFile("prepared-export-", ".$extension", context.cacheDir)
                staged = target
                withContext(Dispatchers.IO) {
                    generate(target)
                    check(target.length() > 0) { Res.get(R.string.export_failed_short) }
                }
                pendingArchive = target
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                launch("simplechat-$stamp.$extension")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                pendingArchive = null
                archiveProgress.value = null
                dataMessage = Res.get(R.string.export_failed, error.userMessage())
            } finally {
                if (pendingArchive !== staged) withContext(NonCancellable + Dispatchers.IO) { staged?.delete() }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> savePreparedExport(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            /*
             * **流式**落到缓存文件，不再整包读进内存。
             *
             * 实测一份 228MB 的 DeepSeek 导出包：`readBytes()` 会直接 OOM，
             * 或者卡到用户以为"点了没反应"。这里改成按块拷贝 —— 内存占用是常量，
             * 之后 `ZipFile` 直接按中央目录读这个文件。
             */
            importReading = true
            dataMessage = null
            var staged: File? = null
            try {
                val file = withContext(Dispatchers.IO) {
                    val target = File.createTempFile("import-", ".bak", context.cacheDir)
                    staged = target
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                    } ?: error(Res.get(R.string.import_read_failed))
                    check(target.length() > 0L) { Res.get(R.string.import_read_failed) }
                    target
                }
                pendingImport = file
                dialog = SettingsDialog.ImportMode
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                dataMessage = Res.get(R.string.error_import_failed, error.userMessage())
            } finally {
                if (pendingImport !== staged) withContext(NonCancellable + Dispatchers.IO) { staged?.delete() }
                importReading = false
            }
        }
    }

    fun startExport() = prepareExport("json", { exportLauncher.launch(it) }) { file ->
        repository.exportBackup(file, onProgress = { archiveProgress.value = it })
    }

    fun startReportExport() = prepareExport("json", { exportLauncher.launch(it) }) { file ->
        val report = repository.lastImportReportFile()
            ?: error(Res.get(R.string.import_report_missing))
        report.inputStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        archiveProgress.value = 1f
    }

    val crashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
        uri: Uri? -> savePreparedExport(uri)
    }
    fun startCrashExport() = prepareExport("txt", { crashLauncher.launch(it) }) { file ->
        val report = com.simplechat.app.data.CrashDiagnostics.file(context)
            ?: error(Res.get(R.string.crash_report_missing))
        report.inputStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        archiveProgress.value = 1f
    }

    val archiveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? -> savePreparedExport(uri) }

    fun startArchiveExport() = prepareExport("zip", { archiveLauncher.launch(it) }) { file ->
        stageArchive(file) { repository.exportArchive(it) { p -> archiveProgress.value = p } }
    }
    suspend fun measureStorage() {
        storageReport = runCatching { StorageUsage.measure(context, container.database) }
            .getOrNull()
    }

    suspend fun clearStorage() {
        if (storageClearing) return
        storageClearing = true
        try {
            val freed = StorageUsage.clear(context, container.database)
            storageMessage = Res.get(
                R.string.storage_clear_done,
                StorageUsage.formatBytes(freed),
            )
            measureStorage()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            storageMessage = error.userMessage()
        } finally {
            storageClearing = false
        }
    }

    LaunchedEffect(page) {
        if (page == SettingsPage.STORAGE) measureStorage()
    }

    // ── 页面 ──────────────────────────────────────────────

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.settingsBackground),
    ) {
        SettingsTopBar(
            title = when (page) {
                SettingsPage.ROOT -> stringResource(R.string.settings_title)
                SettingsPage.DATA -> stringResource(R.string.settings_data)
                SettingsPage.API -> stringResource(R.string.settings_api)
                SettingsPage.API_PROFILES -> stringResource(R.string.api_profiles_title)
                SettingsPage.PRESETS -> stringResource(R.string.settings_presets)
                SettingsPage.FONT -> stringResource(R.string.settings_font_size)
                SettingsPage.VOICE -> stringResource(R.string.tts_voice)
                SettingsPage.ABOUT -> stringResource(R.string.settings_about)
                SettingsPage.LICENSES -> stringResource(R.string.about_licenses)
                SettingsPage.INTERACTION -> stringResource(R.string.settings_interaction)
                SettingsPage.STORAGE -> stringResource(R.string.settings_storage)
                SettingsPage.CUSTOM_THEME -> stringResource(R.string.settings_custom_theme)
                SettingsPage.READING -> stringResource(R.string.settings_reading)
                SettingsPage.BEHAVIOR -> stringResource(R.string.settings_behavior)
                SettingsPage.OUTPUT -> stringResource(R.string.settings_output)
                SettingsPage.STATS -> stringResource(R.string.settings_stats)
                SettingsPage.MEMORY -> stringResource(R.string.settings_memory)
                SettingsPage.LOGS -> stringResource(R.string.settings_logs)
                SettingsPage.KEEPALIVE -> stringResource(R.string.settings_keepalive)
                SettingsPage.SECURITY -> stringResource(R.string.settings_security)
                SettingsPage.SLASH_COMMANDS -> stringResource(R.string.settings_slash_commands)
                SettingsPage.LOADING_WORDS -> stringResource(R.string.settings_loading_words)
                SettingsPage.FAVORITES -> stringResource(R.string.settings_favorites)
            },
            showBack = page != SettingsPage.ROOT,
            onBack = {
                if (archiveProgress.value == null && importProgress.value == null && !importReading) {
                    if (page == SettingsPage.ROOT) onBack() else previousPage()
                }
            },
        )
        BackHandler(enabled = archivePercent != null || importPercent != null || importReading) { }

        /*
         * 二级页面从右侧滑入、返回原路退回（见 ui/common/Motion.kt）。
         *
         * 滚动容器放在 AnimatedContent **里面**，有两个原因：
         *   1. 转场要在**整屏宽**上滑，放在外层 16dp 内边距里会从内容边缘"挤"出来；
         *   2. 顺带修掉一个真问题 —— 六个页面原先共用同一个 scroll state，
         *      从「设置」滚到底再进「数据管理」，进去就是滚到底的。
         *      现在每页各有一份，且用 SaveableStateHolder 存档，来回切不丢位置。
         */
        val pageStates = rememberSaveableStateHolder()

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                screenTransform(forward = targetState.depth > initialState.depth)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = "settingsPage",
        ) { target ->
            pageStates.SaveableStateProvider(target) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    when (target) {
                        SettingsPage.ROOT -> RootPage(
                            stored = stored,
                            presetCount = presets.size,
                            themeMode = themeMode,
                            accentColor = accentColor,
                            language = AppLanguage.fromId(stored.languageId),
                            onOpen = { page = it },
                            onThemeChange = { scope.launch { store.setThemeMode(it.name) } },
                            onAccentChange = { scope.launch { store.setAccentColor(it.name) } },
                            onLanguageChange = { scope.launch { store.setLanguage(it.name) } },
                            onToggleAutoCollapse = {
                                scope.launch { store.setAutoCollapseThinking(it) }
                            },
                            onUiStyleChange = { scope.launch { store.setUiStyle(it.id) } },
                            onToggleExtras = { scope.launch { store.setShowExtrasInDeepSeek(it) } },
                        )

                        SettingsPage.DATA -> DataPage(
                            message = dataMessage,
                            progress = importPercent,
                            reading = importReading,
                            exportProgress = archivePercent,
                            exportSaving = archiveSaving,
                            onExport = { startExport() },
                            onExportArchive = { startArchiveExport() },
                            onExportReport = { startReportExport() },
                            onExportCrash = { startCrashExport() },
                            onImport = {
                                // 全放行：Chatbox 的备份是 .zip，有的设备报的 MIME 还很不标准。
                                // 真正的判定放在解析那一步，认不出来会给出明确原因。
                                importLauncher.launch(arrayOf("*/*"))
                            },
                            onDeleteAll = { dialog = SettingsDialog.DeleteAll },
                            onClearSearchHistory = { dialog = SettingsDialog.ClearSearchHistory },
                        )

                        SettingsPage.API -> ApiPage(
                            stored = stored,
                            provider = provider,
                            testing = testing,
                            testResult = testResult,
                            onPick = { dialog = it },
                            onProfiles = { page = SettingsPage.API_PROFILES },
                            profileError = profileError,
                            onWebSearch = { enabled ->
                                scope.launch { store.setWebSearch(enabled) }
                            },
                            onTest = { scope.launch { refreshModels() } },
                        )

                        SettingsPage.INTERACTION -> InteractionPage(
                            stored = stored,
                            onToggleSend = { scope.launch { store.setHapticSend(it) } },
                            onToggleLongPress = { scope.launch { store.setHapticLongPress(it) } },
                            onToggleDelete = { scope.launch { store.setHapticSwipeDelete(it) } },
                            onToggleSendOnEnter = { scope.launch { store.setSendOnEnter(it) } },
                        )

                        SettingsPage.STORAGE -> StoragePage(
                            report = storageReport,
                            clearing = storageClearing,
                            message = storageMessage,
                            onClear = { dialog = SettingsDialog.ClearStorage },
                        )

                        SettingsPage.CUSTOM_THEME -> CustomThemePage(
                            stored = stored,
                            onPick = { dialog = SettingsDialog.PickColor(it) },
                            onReset = { slot -> scope.launch { store.setCustomColor(slot, null) } },
                            onResetAll = { scope.launch { store.resetCustomColors() } },
                        )

                        SettingsPage.READING -> ReadingPage(
                            stored = stored,
                            onLineHeightChange = { scope.launch { store.setLineHeightScale(it) } },
                            onMonospaceChange = { scope.launch { store.setBodyMonospace(it) } },
                        )

                        SettingsPage.BEHAVIOR -> BehaviorPage(
                            stored = stored,
                            onTone = { scope.launch { store.setTone(it) } },
                            onThinkingScroll = { scope.launch { store.setThinkingScroll(it) } },
                            onStepThinking = { scope.launch { store.setStepThinking(it) } },
                            onPreserveReasoning = { scope.launch { store.setPreserveReasoning(it) } },
                            onLearningMode = { scope.launch { store.setLearningMode(it) } },
                            onSoftCompaction = { scope.launch { store.setSoftCompaction(it) } },
                            onMinimalMode = { scope.launch { store.setMinimalMode(it) } },
                            onContextWindow = { scope.launch { store.setContextWindowOverride(it) } },
                        )

                        SettingsPage.OUTPUT -> OutputPage(
                            stored = stored,
                            onCodeWrap = { scope.launch { store.setCodeWrap(it) } },
                            onProcessExpand = { scope.launch { store.setProcessAutoExpand(it) } },
                            onAutoCollapse = { scope.launch { store.setAutoCollapseThinking(it) } },
                        )

                        SettingsPage.STATS -> StatsPage(rows = statsRows)

                        SettingsPage.MEMORY -> MemoryPage(
                            entries = memories,
                            onAdd = { dialog = SettingsDialog.MemoryEditor(null) },
                            onEdit = { dialog = SettingsDialog.MemoryEditor(it) },
                            onDelete = { scope.launch { store.deleteMemory(it) } },
                        )

                        SettingsPage.LOGS -> LogsPage(
                            text = logText,
                            crash = logCrash,
                            onRefresh = {
                                scope.launch {
                                    logText = ErrorLog.read(context)
                                    logCrash = CrashDiagnostics.file(context)?.readText().orEmpty()
                                }
                            },
                            onClear = {
                                scope.launch {
                                    ErrorLog.clear(context)
                                    logText = ErrorLog.read(context)
                                }
                            },
                        )

                        SettingsPage.KEEPALIVE -> KeepAlivePage(
                            stored = stored,
                            onForeground = { scope.launch { store.setGenerationNotification(it) } },
                            onWakeLock = { scope.launch { store.setKeepAliveWakeLock(it) } },
                            onBattery = { scope.launch { store.setKeepAliveIgnoreBattery(it) } },
                            onAudio = { scope.launch { store.setKeepAliveFakeAudio(it) } },
                        )

                        SettingsPage.SECURITY -> SecurityPage(
                            stored = stored,
                            onAppLock = { scope.launch { store.setAppLock(it) } },
                            onSecureScreen = { scope.launch { store.setSecureScreen(it) } },
                            onAllowHttp = { scope.launch { store.setAllowAnyHttp(it) } },
                        )

                        SettingsPage.LOADING_WORDS -> LoadingWordsPage(
                            words = stored.loadingWords,
                            useThemeColor = stored.loadingWordsUseThemeColor,
                            onUseThemeColor = { scope.launch { store.setLoadingWordsUseThemeColor(it) } },
                            onAdd = { dialog = SettingsDialog.LoadingWordEditor() },
                            onEdit = { dialog = SettingsDialog.LoadingWordEditor(it) },
                            onDelete = { dialog = SettingsDialog.DeleteLoadingWord(it) },
                        )

                        SettingsPage.SLASH_COMMANDS -> SlashCommandsPage(
                            commands = slashCommands,
                            onAdd = { dialog = SettingsDialog.SlashEditor() },
                            onEdit = { dialog = SettingsDialog.SlashEditor(original = it) },
                            onDelete = { dialog = SettingsDialog.DeleteSlash(it) },
                        )

                        SettingsPage.FAVORITES -> FavoritesPage(
                            messages = favoriteMessages,
                            conversations = favoriteConversations,
                            onOpenConversation = onOpenConversation,
                            onUnfavoriteMessage = { scope.launch { store.toggleFavoriteMessage(it) } },
                            onUnfavoriteConversation = { scope.launch { store.toggleFavoriteConversation(it) } },
                        )

                        SettingsPage.API_PROFILES -> ApiProfilesPage(
                            stored = stored, error = profileError,
                            onSelect = { profile -> updateProfile { store.selectApiProfile(profile.id); page = SettingsPage.API } },
                            onNew = { dialog = SettingsDialog.ApiProfileName() },
                            onCopy = { dialog = SettingsDialog.ApiProfileName(copyCurrent = true) },
                            onRename = { dialog = SettingsDialog.ApiProfileName(profile = it) },
                            onDelete = { dialog = SettingsDialog.DeleteApiProfile(it) },
                        )

                        SettingsPage.PRESETS -> PresetsPage(
                            presets = presets,
                            onEdit = { dialog = SettingsDialog.PresetEditor(it) },
                            onDelete = { dialog = SettingsDialog.DeletePreset(it) },
                        )

                        SettingsPage.FONT -> FontPage(
                            stored = stored,
                            onFollowSystem = { scope.launch { store.setFollowSystemFont(it) } },
                            onScaleCommit = { scope.launch { store.setFontScale(it) } },
                        )

                        SettingsPage.ABOUT -> AboutPage(
                            onOpenLicenses = { page = SettingsPage.LICENSES },
                        )

                        SettingsPage.LICENSES -> LicensesPage()

                        SettingsPage.VOICE -> VoicePage(
                            selectedName = stored.ttsVoiceName,
                            voices = speechVoices,
                            officialVoices = officialVoices,
                            officialAvailable = officialTtsConfig != null,
                            previewing = speaker.previewing,
                            error = speechError,
                            onSelect = { voice ->
                                scope.launch { store.setTtsVoice(voice?.name.orEmpty(), voice?.label.orEmpty()) }
                                // 选完顺手念一句：省得用户"选了但不知道变了没有"
                                speaker.preview(Res.get(R.string.tts_voice_preview_text), voice?.name.orEmpty())
                            },
                            onPreview = { voice ->
                                speaker.preview(Res.get(R.string.tts_voice_preview_text), voice?.name.orEmpty())
                            },
                        )
                    }

                    Spacer(Modifier.size(32.dp))
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }
    }

    // ── 弹窗 ──────────────────────────────────────────────

    // 先收进局部变量：dialog 是委托属性，直接 when(dialog) 无法智能转换
    val activeDialog = dialog
    when (activeDialog) {
        SettingsDialog.Provider -> ChoiceDialog(
            title = stringResource(R.string.label_provider),
            options = ProviderKind.entries.map { it to it.displayName },
            selected = provider,
            onDismiss = { dialog = null },
            onSelect = { picked ->
                dialog = null
                updateProfile { store.selectProvider(picked) }
            },
        )

        is SettingsDialog.ApiProfileName -> TextInputDialog(
            title = stringResource(if (activeDialog.profile != null) R.string.api_profile_rename
                else if (activeDialog.copyCurrent) R.string.api_profile_copy else R.string.api_profile_new),
            initial = activeDialog.profile?.name ?: if (activeDialog.copyCurrent)
                stored.apiProfiles.firstOrNull { it.id == stored.activeApiProfileId }?.name.orEmpty() +
                    stringResource(R.string.conversation_fork_suffix) else "",
            placeholder = stringResource(R.string.api_profile_name_hint),
            onDismiss = { dialog = null },
            onConfirm = { value ->
                if (value.isNotBlank()) {
                    dialog = null
                    updateProfile {
                        if (activeDialog.profile != null) store.renameApiProfile(activeDialog.profile.id, value)
                        else { store.createApiProfile(value, activeDialog.copyCurrent); page = SettingsPage.API }
                    }
                }
            },
        )

        is SettingsDialog.DeleteApiProfile -> AppConfirmDialog(
            title = stringResource(R.string.api_profile_delete),
            message = stringResource(R.string.api_profile_delete_body, activeDialog.profile.name),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; updateProfile { store.deleteApiProfile(activeDialog.profile.id) } },
        )

        SettingsDialog.BaseUrl -> TextInputDialog(
            title = "Base URL",
            initial = stored.baseUrl.ifBlank { provider.defaultBaseUrl },
            placeholder = "https://api.deepseek.com",
            onDismiss = { dialog = null },
            onConfirm = { value ->
                dialog = null
                updateProfile { store.setBaseUrl(value) }
            },
        )

        SettingsDialog.Model -> {
            /*
             * 选项 = **这个端点拉到的模型**（落库的那份）在前，内置能力表补在后。
             *
             * 早先只读 `knownModels(provider)`：自定义服务商的内置目录是空的，
             * 于是「测试连接」显示"可用模型 45 个"、点开「模型」却什么都没有 ——
             * 拉取结果根本没进界面。
             */
            val models = ModelCatalog.pickerModels(provider, stored.modelIds)
            ChoiceDialog(
                title = stringResource(R.string.label_model),
                options = models.map { it to it.displayName },
                selected = models.firstOrNull { it.id == stored.modelId },
                emptyHint = stringResource(R.string.api_model_empty_hint),
                extraAction = stringResource(R.string.api_model_manual) to {
                    dialog = SettingsDialog.ModelManual
                },
                onDismiss = { dialog = null },
                onSelect = { picked ->
                    dialog = null
                    scope.launch { store.setModel(picked.id) }
                },
            )
        }

        SettingsDialog.ModelManual -> TextInputDialog(
            title = stringResource(R.string.api_model_manual),
            initial = stored.modelId,
            // 例子用**当前服务商的默认模型**：用户照抄一个多半就是能用的
            placeholder = provider.defaultModel.ifBlank { ProviderKind.DEEPSEEK.defaultModel },
            onDismiss = { dialog = null },
            onConfirm = { value ->
                dialog = null
                val id = value.trim()
                // 空值等于"没选"，不要拿它覆盖已选模型
                if (id.isNotEmpty()) scope.launch { store.setModel(id) }
            },
        )

        SettingsDialog.ApiKey -> TextInputDialog(
            title = "API Key",
            initial = stored.apiKey,
            placeholder = "sk-…",
            secret = true,
            /*
             * 粘进来的东西经常带换行、空格，甚至整段中文占位符。HTTP 头只接受可见 ASCII，
             * 不拦的话错误会以 `Unexpected char 0x4f55 at 8 in Authorization value`
             * 的形式出现在对话里 —— 用户完全看不懂（见 `ApiKey`）。
             */
            validate = { raw ->
                val clean = ApiKey.normalize(raw)
                val illegal = ApiKey.firstIllegalChar(clean)
                if (illegal == null) {
                    null
                } else {
                    Res.get(
                        R.string.api_key_illegal_chars,
                        "0x%04x".format(illegal.code),
                    )
                }
            },
            onDismiss = { dialog = null },
            onConfirm = { value ->
                dialog = null
                updateProfile { store.setApiKey(value) }
            },
        )

        SettingsDialog.ApiStyle -> ChoiceDialog(
            title = stringResource(R.string.settings_api_style),
            options = ApiStylePreference.entries.map { it to stringResource(it.labelRes) },
            selected = ApiStylePreference.fromId(stored.apiStyleId),
            onDismiss = { dialog = null },
            onSelect = { picked ->
                dialog = null
                scope.launch { store.setApiStyle(picked.name) }
            },
        )

        SettingsDialog.Location -> TextInputDialog(
            title = stringResource(R.string.settings_location),
            initial = stored.locationRegion,
            // 占位符要给出**可照抄的例子**，而不是复述"这是干什么用的"
            placeholder = stringResource(R.string.settings_location_placeholder),
            onDismiss = { dialog = null },
            onConfirm = { value ->
                dialog = null
                scope.launch { store.setLocationRegion(value) }
            },
        )

        is SettingsDialog.MemoryEditor -> AppTextInputDialog(
            title = stringResource(
                if (activeDialog.entry == null) R.string.memory_add else R.string.memory_edit,
            ),
            initial = activeDialog.entry?.text.orEmpty(),
            placeholder = stringResource(R.string.memory_placeholder),
            onDismiss = { dialog = null },
            onConfirm = { value ->
                val editing = activeDialog.entry
                dialog = null
                scope.launch {
                    if (editing == null) store.addMemory(value) else store.updateMemory(editing.id, value)
                }
            },
        )

        is SettingsDialog.SlashEditor -> AppPairInputDialog(
            title = stringResource(
                if (activeDialog.original == null) R.string.slash_add else R.string.slash_name,
            ),
            firstLabel = stringResource(R.string.slash_name),
            firstInitial = activeDialog.original?.name.orEmpty(),
            secondLabel = stringResource(R.string.slash_text),
            secondInitial = activeDialog.original?.text.orEmpty(),
            onDismiss = { dialog = null },
            onConfirm = { name, text ->
                val editing = activeDialog.original
                dialog = null
                if (name.isBlank() || text.isBlank()) return@AppPairInputDialog
                scope.launch {
                    // 改名 = 删旧的 + 存新的：存储层按名字去重，同名会覆盖
                    if (editing != null && editing.name != name.trim().trimStart('/')) {
                        store.deleteSlashCommand(editing.name)
                    }
                    store.addSlashCommand(name, text)
                }
            },
        )

        is SettingsDialog.LoadingWordEditor -> AppTextInputDialog(
            title = stringResource(
                if (activeDialog.original == null) R.string.loading_words_add else R.string.loading_words_edit,
            ),
            initial = activeDialog.original.orEmpty(),
            placeholder = stringResource(R.string.loading_words_placeholder),
            onDismiss = { dialog = null },
            onConfirm = { raw ->
                val original = activeDialog.original
                dialog = null
                scope.launch {
                    store.editLoadingWords { current ->
                        val word = normalizeLoadingWord(raw)
                        if (word.isEmpty()) return@editLoadingWords current
                        when {
                            // 新增
                            original == null -> (current + word).distinct()
                            // 改名：原位替换，顺序不乱
                            else -> current.map { if (it == original) word else it }.distinct()
                        }
                    }
                }
            },
        )

        is SettingsDialog.DeleteLoadingWord -> AppConfirmDialog(
            title = stringResource(R.string.loading_words_delete_title),
            message = stringResource(R.string.loading_words_delete_body, activeDialog.word),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onDismiss = { dialog = null },
            onConfirm = {
                val word = activeDialog.word
                dialog = null
                scope.launch { store.editLoadingWords { current -> current - word } }
            },
        )

        is SettingsDialog.DeleteSlash -> AppConfirmDialog(
            title = stringResource(R.string.slash_delete_title),
            message = stringResource(R.string.slash_delete_body, activeDialog.command.name),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onDismiss = { dialog = null },
            onConfirm = {
                val name = activeDialog.command.name
                dialog = null
                scope.launch { store.deleteSlashCommand(name) }
            },
        )

        SettingsDialog.ClearSearchHistory -> AppConfirmDialog(
            title = stringResource(R.string.settings_clear_search_history),
            message = stringResource(R.string.settings_clear_search_history_summary),
            confirmText = stringResource(R.string.settings_clear_search_history),
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                scope.launch {
                    store.clearRecentSearches()
                    dataMessage = Res.get(R.string.settings_clear_search_history_done)
                }
            },
        )

        SettingsDialog.DeleteAll -> AppConfirmDialog(
            title = stringResource(R.string.settings_delete_all_title),
            message = stringResource(R.string.settings_delete_all_body),
            confirmText = stringResource(R.string.settings_delete_all_confirm),
            destructive = true,
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                scope.launch {
                    val outcome = runCatching { repository.deleteAllConversations() }
                    dataMessage = outcome.fold(
                        onSuccess = { Res.get(R.string.settings_delete_all_done) },
                        onFailure = { Res.get(R.string.error_delete_failed, it.userMessage()) },
                    )
                }
            },
        )

        SettingsDialog.ClearStorage -> AppConfirmDialog(
            title = stringResource(R.string.storage_clear_title),
            message = stringResource(R.string.storage_clear_body),
            confirmText = stringResource(R.string.storage_clear_confirm),
            destructive = true,
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                scope.launch { clearStorage() }
            },
        )

        is SettingsDialog.PickColor -> {
            val slot = activeDialog.slot
            AppColorPickerDialog(
                title = stringResource(slot.labelRes),
                initial = stored.customColor(slot)?.let { Color(it) } ?: defaultCustomColor(slot),
                onDismiss = { dialog = null },
                onConfirm = { picked ->
                    dialog = null
                    scope.launch { store.setCustomColor(slot, picked.toArgb()) }
                },
            )
        }

        SettingsDialog.ImportMode -> ChoiceDialog(
            title = stringResource(R.string.import_mode_title),
            options = listOf(
                ImportMode.MERGE to stringResource(R.string.import_mode_merge),
                ImportMode.REPLACE_SAME to stringResource(R.string.import_mode_replace_same),
                ImportMode.RENAME to stringResource(R.string.import_mode_rename),
            ),
            selected = null,
            onDismiss = {
                dialog = null
                pendingImport?.delete()
                pendingImport = null
            },
            onSelect = { mode ->
                dialog = null
                val file = pendingImport
                pendingImport = null
                if (file == null) return@ChoiceDialog
                scope.launch {
                    dataMessage = Res.get(R.string.import_working)
                    importProgress.value = 0f
                    // 认得三种备份：本应用的 JSON / ZIP、Chatbox 的 ZIP / JSON、DeepSeek 导出
                    try {
                        withContext(Dispatchers.IO) {
                            val (_, summary) = repository.importExternal(file, context.cacheDir, mode) { p ->
                                // 回调来自 IO 线程：StateFlow 是线程安全的，界面侧 collectAsState 收。
                                // 0 就先不给 —— 停在 0% 看着像卡死，等真有进展再显示百分比
                                importProgress.value = p.coerceIn(0f, 1f)
                            }
                            dataMessage = summary
                        }
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) { dataMessage = Res.get(R.string.error_import_failed, error.userMessage()) }
                    finally {
                        importProgress.value = null
                        withContext(NonCancellable + Dispatchers.IO) { file.delete() }
                    }
                }
            },
        )

        is SettingsDialog.PresetEditor -> PresetEditorDialog(
            initial = activeDialog.preset,
            onDismiss = { dialog = null },
            onConfirm = { name, content ->
                val target = activeDialog.preset
                dialog = null
                scope.launch {
                    repository.upsertPreset(
                        preset = Preset(
                            id = target?.id ?: UUID.randomUUID().toString(),
                            name = name,
                            content = content,
                        ),
                        // 新建排在末尾；编辑保持原有位置
                        order = target?.let { presets.indexOfFirst { p -> p.id == it.id } }
                            ?.takeIf { it >= 0 }
                            ?: presets.size,
                    )
                }
            },
        )

        is SettingsDialog.DeletePreset -> AppConfirmDialog(
            title = stringResource(R.string.preset_delete_title, activeDialog.preset.name),
            message = stringResource(R.string.preset_delete_body),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onDismiss = { dialog = null },
            onConfirm = {
                val id = activeDialog.preset.id
                dialog = null
                scope.launch { repository.deletePreset(id) }
            },
        )

        null -> Unit
    }
}

// ══════════════════════════════════════════════════════════
//  一级页面
// ══════════════════════════════════════════════════════════

@Composable
private fun RootPage(
    stored: StoredSettings,
    presetCount: Int,
    themeMode: ThemeMode,
    accentColor: AccentColor,
    language: AppLanguage,
    onOpen: (SettingsPage) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onAccentChange: (AccentColor) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onToggleAutoCollapse: (Boolean) -> Unit,
    onUiStyleChange: (UiStyle) -> Unit,
    onToggleExtras: (Boolean) -> Unit,
) {
    var themeMenuOpen by remember { mutableStateOf(false) }
    var uiStyleMenuOpen by remember { mutableStateOf(false) }
    var accentMenuOpen by remember { mutableStateOf(false) }
    var languageMenuOpen by remember { mutableStateOf(false) }

    /*
     * 一级页是**两列方格**，不再是竖排列表。
     *
     * 十二个入口排成一列时每条只有一行字，翻两屏才看得全；改成方格后一屏扫完，
     * 手指落点也大得多。每行固定两格，缺的那个用空位补上，左右永远对齐。
     */
    val provider = runCatching { ProviderKind.valueOf(stored.providerId) }
        .getOrDefault(ProviderKind.DEEPSEEK)

    SettingsSectionLabel(stringResource(R.string.settings_group_account))
    SettingsTileRow(
        listOf(
            SettingsTileSpec(
                icon = AppIcons.Database,
                label = stringResource(R.string.settings_data),
                value = stringResource(R.string.settings_data_summary),
                onClick = { onOpen(SettingsPage.DATA) },
            ),
            SettingsTileSpec(
                icon = AppIcons.Server,
                label = stringResource(R.string.settings_api),
                value = provider.displayName,
                onClick = { onOpen(SettingsPage.API) },
            ),
        ),
    )

    SettingsSectionLabel(stringResource(R.string.settings_group_personal))
    SettingsTileRow(
        listOf(
            // 外观：三个互斥选项，点格子直接弹菜单
            SettingsTileSpec(
                icon = AppIcons.Contrast,
                label = stringResource(R.string.settings_appearance),
                value = when (themeMode) {
                    ThemeMode.SYSTEM -> stringResource(R.string.value_follow_system)
                    ThemeMode.LIGHT -> stringResource(R.string.value_light)
                    ThemeMode.DARK -> stringResource(R.string.value_dark)
                },
                onClick = { themeMenuOpen = true },
                menu = {
                    AppMenu(
                        expanded = themeMenuOpen,
                        onDismissRequest = { themeMenuOpen = false },
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            AppMenuItem(
                                label = when (mode) {
                                    ThemeMode.SYSTEM -> stringResource(R.string.value_follow_system)
                                    ThemeMode.LIGHT -> stringResource(R.string.value_light)
                                    ThemeMode.DARK -> stringResource(R.string.value_dark)
                                },
                                selected = mode == themeMode,
                                onClick = {
                                    themeMenuOpen = false
                                    onThemeChange(mode)
                                },
                            )
                        }
                    }
                },
            ),
            // 语言：换语言 = 改缓存 + 整页重建（见 MainActivity）
            SettingsTileSpec(
                icon = AppIcons.Globe,
                label = stringResource(R.string.settings_language),
                value = when (language) {
                    AppLanguage.SYSTEM -> stringResource(R.string.value_follow_system)
                    AppLanguage.ZH -> stringResource(R.string.lang_chinese)
                    AppLanguage.EN -> stringResource(R.string.lang_english)
                },
                onClick = { languageMenuOpen = true },
                menu = {
                    AppMenu(
                        expanded = languageMenuOpen,
                        onDismissRequest = { languageMenuOpen = false },
                    ) {
                        listOf(
                            AppLanguage.SYSTEM to stringResource(R.string.value_follow_system),
                            AppLanguage.ZH to stringResource(R.string.lang_chinese),
                            AppLanguage.EN to stringResource(R.string.lang_english),
                        ).forEach { (option, label) ->
                            AppMenuItem(
                                label = label,
                                selected = option == language,
                                onClick = {
                                    languageMenuOpen = false
                                    onLanguageChange(option)
                                },
                            )
                        }
                    }
                },
            ),
        ),
    )
    SettingsTileRow(
        listOf(
            // 主题色：点开弹一排色块
            SettingsTileSpec(
                icon = AppIcons.Palette,
                label = stringResource(R.string.settings_accent),
                value = accentColor.label,
                onClick = { accentMenuOpen = true },
                menu = {
                    AppMenu(
                        expanded = accentMenuOpen,
                        onDismissRequest = { accentMenuOpen = false },
                        // 七个色块横排，比默认菜单宽
                        width = AccentMenuWidth,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AccentColor.entries.forEach { color ->
                                AccentSwatch(
                                    color = color,
                                    selected = color == accentColor,
                                    onClick = {
                                        accentMenuOpen = false
                                        onAccentChange(color)
                                    },
                                )
                            }
                        }
                    }
                },
            ),
            SettingsTileSpec(
                icon = AppIcons.FontSize,
                label = stringResource(R.string.settings_font_size),
                value = stringResource(
                    fontSizeSteps
                        .minByOrNull { kotlin.math.abs(it.first - stored.fontScale) }
                        ?.second ?: R.string.font_step_standard,
                ),
                onClick = { onOpen(SettingsPage.FONT) },
            ),
        ),
    )
    SettingsTileRow(
        listOf(
            // 界面风格：原版 / DeepSeek 皮肤
            SettingsTileSpec(
                icon = AppIcons.Contrast,
                label = stringResource(R.string.settings_ui_style),
                value = when (UiStyle.fromId(stored.uiStyleId)) {
                    UiStyle.ORIGINAL -> stringResource(R.string.ui_style_original)
                    UiStyle.DEEPSEEK -> stringResource(R.string.ui_style_deepseek)
                },
                onClick = { uiStyleMenuOpen = true },
                menu = {
                    AppMenu(
                        expanded = uiStyleMenuOpen,
                        onDismissRequest = { uiStyleMenuOpen = false },
                    ) {
                        listOf(
                            UiStyle.ORIGINAL to stringResource(R.string.ui_style_original),
                            UiStyle.DEEPSEEK to stringResource(R.string.ui_style_deepseek),
                        ).forEach { (option, label) ->
                            AppMenuItem(
                                label = label,
                                selected = option == UiStyle.fromId(stored.uiStyleId),
                                onClick = {
                                    uiStyleMenuOpen = false
                                    onUiStyleChange(option)
                                },
                            )
                        }
                    }
                },
            ),
            // 只有 DeepSeek 皮肤才需要问"高级功能留不留"
            if (UiStyle.fromId(stored.uiStyleId) == UiStyle.DEEPSEEK) {
                SettingsTileSpec(
                    icon = AppIcons.Sliders,
                    label = stringResource(R.string.settings_show_extras),
                    value = stringResource(
                        if (stored.showExtrasInDeepSeek) R.string.value_on else R.string.value_off,
                    ),
                    onClick = { onToggleExtras(!stored.showExtrasInDeepSeek) },
                )
            } else {
                null
            },
        ).filterNotNull(),
    )
    SettingsTileRow(
        listOf(
            // 自定义配色：逐项覆盖主题色（见 CustomThemePage）
            SettingsTileSpec(
                icon = AppIcons.Circle,
                label = stringResource(R.string.settings_custom_theme),
                value = customColorCount(stored).let { count ->
                    if (count == 0) {
                        stringResource(R.string.settings_custom_follow)
                    } else {
                        stringResource(R.string.settings_custom_count, count)
                    }
                },
                onClick = { onOpen(SettingsPage.CUSTOM_THEME) },
            ),
            // 阅读排版：行高与字形，跟字号分开（见 ReadingPage）
            SettingsTileSpec(
                icon = AppIcons.Feather,
                label = stringResource(R.string.settings_reading),
                value = "${(stored.lineHeightScale * 100).toInt()}%",
                onClick = { onOpen(SettingsPage.READING) },
            ),
        ),
    )
    SettingsTileRow(
        listOf(
            SettingsTileSpec(
                icon = AppIcons.Prompt,
                label = stringResource(R.string.settings_presets),
                value = if (presetCount == 0) {
                    stringResource(R.string.preset_count_empty)
                } else {
                    stringResource(R.string.preset_count, presetCount)
                },
                onClick = { onOpen(SettingsPage.PRESETS) },
            ),
            // 朗读音色：系统 TTS 引擎提供，进二级页挑选（见 VoicePage）
            SettingsTileSpec(
                icon = AppIcons.Volume,
                label = stringResource(R.string.tts_voice),
                value = stored.ttsVoiceLabel.ifBlank { stringResource(R.string.tts_voice_default) },
                onClick = { onOpen(SettingsPage.VOICE) },
            ),
        ),
    )
    SettingsTileRow(
        listOf(
            SettingsTileSpec(
                icon = AppIcons.Sliders,
                label = stringResource(R.string.settings_interaction),
                value = when {
                    stored.hapticSend && stored.hapticLongPress && stored.hapticSwipeDelete ->
                        stringResource(R.string.value_on)
                    !stored.hapticSend && !stored.hapticLongPress && !stored.hapticSwipeDelete ->
                        stringResource(R.string.value_off)
                    else -> stringResource(R.string.value_custom)
                },
                onClick = { onOpen(SettingsPage.INTERACTION) },
            ),
            SettingsTileSpec(
                icon = AppIcons.Database,
                label = stringResource(R.string.settings_storage),
                value = stringResource(R.string.settings_storage_summary),
                onClick = { onOpen(SettingsPage.STORAGE) },
            ),
        ),
    )
    SettingsSectionLabel(stringResource(R.string.settings_group_features))
    SettingsTileRow(
        listOf(
            SettingsTileSpec(
                icon = AppIcons.Brain,
                label = stringResource(R.string.settings_behavior),
                value = if (stored.toneId == "CODING") {
                    stringResource(R.string.behavior_tone_coding)
                } else {
                    stringResource(R.string.behavior_tone_chat)
                },
                onClick = { onOpen(SettingsPage.BEHAVIOR) },
            ),
            SettingsTileSpec(
                icon = AppIcons.Monitor,
                label = stringResource(R.string.settings_output),
                value = if (stored.codeWrap) {
                    stringResource(R.string.value_on)
                } else {
                    stringResource(R.string.value_off)
                },
                onClick = { onOpen(SettingsPage.OUTPUT) },
            ),
            SettingsTileSpec(
                icon = AppIcons.FontSize,
                label = stringResource(R.string.settings_loading_words),
                value = if (stored.loadingWords.isEmpty()) {
                    stringResource(R.string.settings_loading_words_summary)
                } else {
                    stored.loadingWords.size.toString()
                },
                onClick = { onOpen(SettingsPage.LOADING_WORDS) },
            ),
        ),
    )
    SettingsTileRow(
        listOf(
            SettingsTileSpec(
                icon = AppIcons.ListChecks,
                label = stringResource(R.string.settings_stats),
                value = null,
                onClick = { onOpen(SettingsPage.STATS) },
            ),
        ),
    )
    SettingsTileRow(
        listOf(
            SettingsTileSpec(
                icon = AppIcons.Sparkles,
                label = stringResource(R.string.settings_memory),
                value = null,
                onClick = { onOpen(SettingsPage.MEMORY) },
            ),
            SettingsTileSpec(
                icon = AppIcons.FileText,
                label = stringResource(R.string.settings_logs),
                value = null,
                onClick = { onOpen(SettingsPage.LOGS) },
            ),
        ),
    )
    SettingsTileRow(
        listOf(
            SettingsTileSpec(
                icon = AppIcons.Zap,
                label = stringResource(R.string.settings_slash_commands),
                value = stringResource(R.string.settings_slash_commands_summary),
                onClick = { onOpen(SettingsPage.SLASH_COMMANDS) },
            ),
            SettingsTileSpec(
                icon = AppIcons.Star,
                label = stringResource(R.string.settings_favorites),
                value = stringResource(R.string.settings_favorites_summary),
                onClick = { onOpen(SettingsPage.FAVORITES) },
            ),
        ),
    )
    SettingsTileRow(
        listOf(
            SettingsTileSpec(
                icon = AppIcons.Refresh,
                label = stringResource(R.string.settings_keepalive),
                value = if (stored.generationNotification) {
                    stringResource(R.string.value_on)
                } else {
                    stringResource(R.string.value_off)
                },
                onClick = { onOpen(SettingsPage.KEEPALIVE) },
            ),
            SettingsTileSpec(
                icon = AppIcons.EyeOff,
                label = stringResource(R.string.settings_security),
                value = if (stored.appLock) {
                    stringResource(R.string.value_on)
                } else {
                    stringResource(R.string.value_off)
                },
                onClick = { onOpen(SettingsPage.SECURITY) },
            ),
        ),
    )

    SettingsTileRow(
        listOf(
            // 自动折叠：方格本身就是开关，点一下切一次
            SettingsTileSpec(
                icon = AppIcons.Collapse,
                label = stringResource(R.string.settings_auto_collapse),
                value = if (stored.autoCollapseThinking) {
                    stringResource(R.string.value_on)
                } else {
                    stringResource(R.string.value_off)
                },
                onClick = { onToggleAutoCollapse(!stored.autoCollapseThinking) },
            ),
            SettingsTileSpec(
                icon = AppIcons.Info,
                label = stringResource(R.string.settings_about_title),
                value = null,
                onClick = { onOpen(SettingsPage.ABOUT) },
            ),
        ),
    )
}

/**
 * 触感反馈：三个动作各一个开关。
 *
 * 为什么不给一个总开关：见 `SettingsStore.setHapticSend` 的说明 ——
 * 有人只讨厌某一处的震动，合成一个开关只会逼人全关。
 */
@Composable
private fun InteractionPage(
    stored: StoredSettings,
    onToggleSend: (Boolean) -> Unit,
    onToggleLongPress: (Boolean) -> Unit,
    onToggleDelete: (Boolean) -> Unit,
    onToggleSendOnEnter: (Boolean) -> Unit,
) {
    val colors = LocalChatColors.current
    SettingsSectionLabel(stringResource(R.string.settings_haptic_group))
    SettingsCard {
        SettingsSwitchRow(
            label = stringResource(R.string.haptic_send),
            subtitle = stringResource(R.string.haptic_send_hint),
            checked = stored.hapticSend,
            onCheckedChange = onToggleSend,
        )
        RowDivider(inset = 16.dp)
        SettingsSwitchRow(
            label = stringResource(R.string.haptic_long_press),
            subtitle = stringResource(R.string.haptic_long_press_hint),
            checked = stored.hapticLongPress,
            onCheckedChange = onToggleLongPress,
        )
        RowDivider(inset = 16.dp)
        SettingsSwitchRow(
            label = stringResource(R.string.haptic_delete),
            subtitle = stringResource(R.string.haptic_delete_hint),
            checked = stored.hapticSwipeDelete,
            onCheckedChange = onToggleDelete,
        )
    }
    Text(
        text = stringResource(R.string.settings_haptic_note),
        style = MaterialTheme.typography.bodySmall,
        color = colors.placeholder,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, end = 4.dp, bottom = 8.dp),
    )

    // 输入：跟触感放同一页 —— 都是"手指怎么操作"的事，不是外观
    SettingsSectionLabel(stringResource(R.string.settings_input_group))
    SettingsCard {
        SettingsSwitchRow(
            label = stringResource(R.string.haptic_send_enter),
            subtitle = stringResource(R.string.haptic_send_enter_hint),
            checked = stored.sendOnEnter,
            onCheckedChange = onToggleSendOnEnter,
        )
    }
    Spacer(Modifier.height(24.dp))
}

/**
 * 自定义配色：五项颜色逐项覆盖主题。
 *
 * 每项都是"留空 = 跟随主题"，所以只有**改过**的项才显示色值与「重置」——
 * 五项全列出来、每项都带一个色块，反而看不出哪几项动过。
 */
@Composable
private fun CustomThemePage(
    stored: StoredSettings,
    onPick: (SettingsStore.CustomColorSlot) -> Unit,
    onReset: (SettingsStore.CustomColorSlot) -> Unit,
    onResetAll: () -> Unit,
) {
    val colors = LocalChatColors.current
    val count = customColorCount(stored)

    SettingsSectionLabel(stringResource(R.string.settings_custom_theme))
    SettingsCard {
        SettingsStore.CustomColorSlot.entries.forEachIndexed { index, slot ->
            if (index > 0) RowDivider(inset = 16.dp)
            CustomColorRow(
                label = stringResource(slot.labelRes),
                value = stored.customColor(slot),
                fallback = defaultCustomColor(slot),
                onPick = { onPick(slot) },
                onReset = { onReset(slot) },
            )
        }
    }
    Text(
        text = stringResource(R.string.settings_custom_note),
        style = MaterialTheme.typography.bodySmall,
        color = colors.placeholder,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, end = 4.dp),
    )

    if (count > 0) {
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MenuShape)
                .clickable(onClick = onResetAll)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.settings_custom_reset_all),
                style = MaterialTheme.typography.titleSmall,
                color = colors.danger,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun CustomColorRow(
    label: String,
    value: Int?,
    fallback: Color,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = LocalChatColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(value?.let { Color(it) } ?: fallback)
                .border(1.dp, colors.outline, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = value?.let { hexOf(it) } ?: stringResource(R.string.settings_custom_follow),
                style = MaterialTheme.typography.bodySmall,
                color = colors.placeholder,
            )
        }
        if (value != null) {
            Text(
                text = stringResource(R.string.settings_custom_reset),
                style = MaterialTheme.typography.labelMedium,
                color = colors.placeholder,
                modifier = Modifier
                    .clip(PillShape)
                    .clickable(onClick = onReset)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 阅读排版：正文行高与等宽。
 *
 * 与字号分成两页：字号是"看得清"，行高是"读得松"，两件事的取舍完全不同。
 */
@Composable
private fun ReadingPage(
    stored: StoredSettings,
    onLineHeightChange: (Float) -> Unit,
    onMonospaceChange: (Boolean) -> Unit,
) {
    val colors = LocalChatColors.current
    SettingsSectionLabel(stringResource(R.string.settings_reading))
    SettingsCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_reading_line_height),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(stored.lineHeightScale * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.placeholder,
                )
            }
            Slider(
                value = stored.lineHeightScale,
                onValueChange = onLineHeightChange,
                valueRange = SettingsStore.MIN_LINE_HEIGHT..SettingsStore.MAX_LINE_HEIGHT,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
        RowDivider(inset = 16.dp)
        SettingsSwitchRow(
            label = stringResource(R.string.settings_reading_monospace),
            subtitle = stringResource(R.string.settings_reading_monospace_hint),
            checked = stored.bodyMonospace,
            onCheckedChange = onMonospaceChange,
        )
    }
    SettingsSectionLabel(stringResource(R.string.settings_reading_preview))
    SettingsCard {
        Text(
            text = stringResource(R.string.settings_reading_preview_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(16.dp),
        )
    }
    Spacer(Modifier.height(24.dp))
}

/** 已改过的配色项数（一级页方格上显示）。 */
private fun customColorCount(stored: StoredSettings): Int =
    SettingsStore.CustomColorSlot.entries.count { stored.customColor(it) != null }

private fun StoredSettings.customColor(slot: SettingsStore.CustomColorSlot): Int? = when (slot) {
    SettingsStore.CustomColorSlot.PRIMARY -> customPrimary
    SettingsStore.CustomColorSlot.USER_BUBBLE -> customUserBubble
    SettingsStore.CustomColorSlot.PAGE_BACKGROUND -> customPageBackground
    SettingsStore.CustomColorSlot.CARD -> customCard
    SettingsStore.CustomColorSlot.TEXT -> customText
}

/** 未自定义时该项跟随主题的实际颜色（取色弹窗的初始值）。 */
@Composable
private fun defaultCustomColor(slot: SettingsStore.CustomColorSlot): Color {
    val colors = LocalChatColors.current
    return when (slot) {
        SettingsStore.CustomColorSlot.PRIMARY -> MaterialTheme.colorScheme.primary
        SettingsStore.CustomColorSlot.USER_BUBBLE -> colors.userBubble
        SettingsStore.CustomColorSlot.PAGE_BACKGROUND -> colors.pageBackground
        SettingsStore.CustomColorSlot.CARD -> colors.card
        SettingsStore.CustomColorSlot.TEXT -> MaterialTheme.colorScheme.onSurface
    }
}

private fun hexOf(argb: Int): String =
    "#" + (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/**
 * 存储占用与清理。
 *
 * [report] 为 null = 还在扫（或扫描失败）。这一页**不在启动路径上**：
 * 只有真的进到这一页才会去遍历附件目录与缓存目录。
 */
@Composable
private fun StoragePage(
    report: StorageUsage.Report?,
    clearing: Boolean,
    message: String?,
    onClear: () -> Unit,
) {
    val colors = LocalChatColors.current
    SettingsSectionLabel(stringResource(R.string.settings_storage_group))
    SettingsCard {
        StorageSizeRow(
            label = stringResource(R.string.storage_db),
            bytes = report?.databaseBytes,
        )
        RowDivider(inset = 16.dp)
        StorageSizeRow(
            label = stringResource(R.string.storage_attachments),
            bytes = report?.attachmentBytes,
        )
        RowDivider(inset = 16.dp)
        StorageSizeRow(
            label = stringResource(R.string.storage_orphans),
            bytes = report?.orphanBytes,
            detail = report
                ?.takeIf { it.orphanCount > 0 }
                ?.let { stringResource(R.string.storage_orphan_count, it.orphanCount) },
        )
        RowDivider(inset = 16.dp)
        StorageSizeRow(
            label = stringResource(R.string.storage_cache),
            bytes = report?.cacheBytes,
        )
    }

    SettingsSectionLabel(stringResource(R.string.settings_storage_clean_group))
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report?.let { StorageUsage.formatBytes(it.cleanableBytes) } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.storage_clean_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.placeholder,
                )
            }
            CapsuleButton(
                text = if (clearing) {
                    stringResource(R.string.storage_clearing)
                } else {
                    stringResource(R.string.storage_clear)
                },
                onClick = onClear,
                enabled = !clearing && report != null && report.cleanableBytes > 0,
            )
        }
    }
    if (message != null) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp, end = 4.dp, bottom = 24.dp),
        )
    }
}

/** 一行「名字 —— 大小」。扫描未完成时右边是「—」，不假装是 0。 */
@Composable
private fun StorageSizeRow(label: String, bytes: Long?, detail: String? = null) {
    val colors = LocalChatColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.placeholder,
                )
            }
        }
        Text(
            text = bytes?.let { StorageUsage.formatBytes(it) } ?: "—",
            style = MaterialTheme.typography.titleSmall,
            color = colors.placeholder,
        )
    }
}

/**
 * 一级页方格的一项。
 *
 * [menu] 与格子挂在同一个 `Box` 里：`AppMenu` 跟着锚点走，锚点就是这一格。
 */
private class SettingsTileSpec(
    val icon: ImageVector?,
    val label: String,
    val value: String?,
    val onClick: () -> Unit,
    val menu: (@Composable () -> Unit)? = null,
)

/** 方格间距。 */
private val TileGap = 12.dp

/** 两列方格的一行；只有一格时右侧留空，左右始终对齐。 */
@Composable
private fun SettingsTileRow(tiles: List<SettingsTileSpec>) {
    /*
     * DeepSeek 皮肤下把方格摊成整行。
     *
     * 那套界面的设置页是"一行一项、右边一个值 + 箭头"，没有方格网格 ——
     * 数据是同一份（[SettingsTileSpec]），只是排法不同，所以这里只换布局，
     * 不去复制一份设置清单：复制迟早会漏项。
     */
    if (LocalUiStyle.current == UiStyle.DEEPSEEK) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = TileGap)) {
            tiles.forEachIndexed { index, tile ->
                if (index > 0) RowDivider(inset = 16.dp)
                Box(modifier = Modifier.fillMaxWidth()) {
                    SettingsTile(
                        icon = tile.icon,
                        label = tile.label,
                        value = tile.value,
                        onClick = tile.onClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    tile.menu?.invoke()
                }
            }
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = TileGap),
        horizontalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        tiles.forEach { tile ->
            Box(modifier = Modifier.weight(1f)) {
                SettingsTile(
                    icon = tile.icon,
                    label = tile.label,
                    value = tile.value,
                    onClick = tile.onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                tile.menu?.invoke()
            }
        }
        repeat((2 - tiles.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
    }
}

/** 单个方格：左上角图标，左下角标题与当前值。 */
@Composable
private fun SettingsTile(
    icon: ImageVector?,
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChatColors.current
    if (LocalUiStyle.current == UiStyle.DEEPSEEK) {
        // 列表行：图标 + 名称 + 右侧值 + 箭头，跟官方设置页一个样子
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.placeholder,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = colors.placeholder,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(16.dp),
            )
        }
        return
    }
    Column(
        modifier = modifier
            .clip(MenuShape)
            .background(colors.card)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.fieldBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!value.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  二级：朗读音色
// ══════════════════════════════════════════════════════════

/**
 * 朗读音色选择。
 *
 * 为什么是"列表 + 试听"而不是下拉框：音色名是 `zh-cn-x-ccc-local` 这种机器标识，
 * 光看名字根本分不出区别 —— 唯一的判断方式就是**听一句**。所以每一行都带试听，
 * 选中后也顺手念一遍（见调用处）。
 */
@Composable
private fun VoicePage(
    selectedName: String,
    voices: List<SpeechVoice>?,
    /** DeepSeek 官方音色；空 = 当前服务商不支持官方朗读（整块不显示）。 */
    officialVoices: List<SpeechVoice>,
    officialAvailable: Boolean,
    previewing: Boolean,
    error: String?,
    onSelect: (SpeechVoice?) -> Unit,
    onPreview: (SpeechVoice?) -> Unit,
) {
    SettingsSectionLabel(stringResource(R.string.tts_voice))

    SettingsCard {
        VoiceRow(
            label = stringResource(R.string.tts_voice_default),
            selected = selectedName.isBlank(),
            onClick = { onSelect(null) },
        )
        voices?.forEach { voice ->
            RowDivider()
            VoiceRow(
                label = voice.label,
                selected = voice.name == selectedName,
                onClick = { onSelect(voice) },
                onPreview = { onPreview(voice) },
            )
        }
    }

    /*
     * 官方音色单独一组、排在系统音色**后面**：系统音色是"一定可用"的兜底，
     * 官方的音质更好但依赖服务商与网络。不支持时不显示这一组 —— 连入口都没有，
     * 用户就不会点了才发现不行。
     */
    if (officialVoices.isNotEmpty()) {
        Spacer(Modifier.size(24.dp))
        SettingsSectionLabel(stringResource(R.string.tts_voice_official_section))
        SettingsCard {
            officialVoices.forEachIndexed { index, voice ->
                if (index > 0) RowDivider()
                VoiceRow(
                    label = voice.label,
                    selected = voice.name == selectedName,
                    onClick = { onSelect(voice) },
                    onPreview = { onPreview(voice) },
                )
            }
        }
    }

    val colors = LocalChatColors.current
    val hint = when {
        // 报错优先：引擎缺失 / 合成失败时，"正在试听"之类的说明就是误导
        error != null -> error
        voices == null -> stringResource(R.string.tts_voice_loading)
        voices.isEmpty() && officialVoices.isEmpty() -> stringResource(R.string.tts_voice_empty)
        previewing -> stringResource(R.string.tts_voice_preview_running)
        else -> stringResource(R.string.tts_voice_hint)
    }
    Text(
        text = hint,
        style = MaterialTheme.typography.bodySmall,
        color = colors.placeholder,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
    )
    // 官方音色没出现时说明原因与条件，而不是让用户以为"这个 App 只有系统音色"
    if (!officialAvailable) {
        Text(
            text = stringResource(R.string.tts_voice_official_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.placeholder,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }
}

/** 一行音色：名字 +（可选）试听 + 选中标记。 */
@Composable
private fun VoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onPreview: (() -> Unit)? = null,
) {
    val colors = LocalChatColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (onPreview != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPreview),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.Play,
                    contentDescription = stringResource(R.string.tts_voice_preview),
                    tint = colors.placeholder,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  二级：数据管理
// ══════════════════════════════════════════════════════════

@Composable
private fun DataPage(
    message: String?,
    /** 确定进度：null = 还没开始或已经结束；0..1 = 已解析的会话占比。 */
    progress: Float? = null,
    /** 无进度阶段（读文件、开包）—— 给循环进度条，别让界面看着像卡死。 */
    reading: Boolean = false,
    exportProgress: Float? = null,
    exportSaving: Boolean = false,
    onExport: () -> Unit,
    onExportArchive: () -> Unit,
    onExportReport: () -> Unit,
    onExportCrash: () -> Unit,
    onImport: () -> Unit,
    onDeleteAll: () -> Unit,
    onClearSearchHistory: () -> Unit,
) {
    SettingsSectionLabel(stringResource(R.string.settings_group_backup))

    SettingsCard {
        SettingsActionRow(
            label = stringResource(R.string.settings_export_all),
            hint = stringResource(R.string.settings_export_all_summary),
            onClick = onExport,
            enabled = !reading && progress == null && exportProgress == null,
        )
        RowDivider()
        SettingsActionRow(
            label = stringResource(R.string.settings_export_archive),
            hint = stringResource(R.string.settings_export_archive_summary),
            onClick = onExportArchive,
            enabled = !reading && progress == null && exportProgress == null,
        )
        RowDivider()
        SettingsActionRow(
            label = stringResource(R.string.settings_import_all),
            hint = stringResource(R.string.settings_import_all_summary),
            onClick = onImport,
            // 导入期间不能重复点：同一份包并发解析会把内存和数据库都顶爆
            enabled = !reading && progress == null && exportProgress == null,
        )
    }

    /*
     * 进度条。大包（几百 MB）要走上分钟，没有进度的话界面上就是"点了没反应"——
     * 这正是先前那份 228MB 导出包给人的印象。
     *
     * 解析阶段按"已处理多少条会话"给确定进度；拷贝与收尾阶段只有 indeterminate。
     */
    if (exportProgress != null) {
        Spacer(Modifier.size(12.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = { exportProgress })
            Spacer(Modifier.size(6.dp))
            Text(
                text = stringResource(
                    if (exportSaving) R.string.export_saving_progress else R.string.export_preparing_progress,
                    (exportProgress * 100).toInt(),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = LocalChatColors.current.placeholder,
            )
        }
    }
    if (reading || progress != null) {
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            /*
             * 两个阶段用两种进度条：
             * - **读取中**（落盘、开 ZIP、读索引）：无从得知比例 → 循环进度条；
             * - **导入中**（逐条会话）：按已处理会话数给百分比。
             *
             * 先前读文件阶段也塞了个 0% 的确定进度条，几十秒不动 —— 看着就是"卡死"。
             */
            if (reading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.import_reading),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalChatColors.current.placeholder,
                )
            } else if (progress != null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = { progress },
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.import_progress, (progress * 100).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalChatColors.current.placeholder,
                )
            }
        }
    }

    /*
     * 诊断：导入对账报告与崩溃诊断都是"出了问题才用"的东西，和日常的备份 / 导入
     * 不是同一类操作。混在备份卡片里，看起来会像"又一个常规导出"。
     */
    SettingsSectionLabel(stringResource(R.string.settings_group_diagnostics))

    SettingsCard {
        SettingsActionRow(
            label = stringResource(R.string.import_report_export),
            hint = stringResource(R.string.import_report_hint),
            onClick = onExportReport,
            enabled = !reading && progress == null && exportProgress == null,
        )
        RowDivider()
        SettingsActionRow(
            label = stringResource(R.string.crash_report_export),
            hint = stringResource(R.string.crash_report_hint),
            onClick = onExportCrash,
            enabled = !reading && progress == null && exportProgress == null,
        )
    }

    // 搜索历史：属于"数据"，但删了不影响会话 —— 单独一格，别混进"清空数据"里
    SettingsSectionLabel(stringResource(R.string.settings_group_other))

    SettingsCard {
        SettingsActionRow(
            label = stringResource(R.string.settings_clear_search_history),
            hint = stringResource(R.string.settings_clear_search_history_summary),
            onClick = onClearSearchHistory,
            enabled = !reading && progress == null && exportProgress == null,
        )
    }

    SettingsSectionLabel(stringResource(R.string.settings_group_reset))

    SettingsCard {
        SettingsActionRow(
            label = stringResource(R.string.settings_delete_all),
            hint = stringResource(R.string.settings_destructive_hint),
            destructive = true,
            onClick = onDeleteAll,
            enabled = !reading && progress == null && exportProgress == null,
        )
    }

    message?.let {
        Spacer(Modifier.size(12.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.labelMedium,
            color = LocalChatColors.current.placeholder,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

// ══════════════════════════════════════════════════════════
//  二级：API 配置
// ══════════════════════════════════════════════════════════

@Composable
private fun ApiPage(
    stored: StoredSettings,
    provider: ProviderKind,
    testing: Boolean,
    testResult: String?,
    onPick: (SettingsDialog) -> Unit,
    onProfiles: () -> Unit,
    profileError: String?,
    onTest: () -> Unit,
    onWebSearch: (Boolean) -> Unit,
) {
    val colors = LocalChatColors.current

    SettingsSectionLabel(stringResource(R.string.api_profiles_title))
    SettingsCard {
        SettingsNavRow(null, stringResource(R.string.api_profile_current),
            stored.apiProfiles.firstOrNull { it.id == stored.activeApiProfileId }?.name, onProfiles)
    }
    Text(stringResource(R.string.api_profiles_hint), style = MaterialTheme.typography.labelSmall,
        color = colors.placeholder, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
    profileError?.let { Text(it, color = colors.danger, style = MaterialTheme.typography.labelMedium) }

    SettingsSectionLabel(stringResource(R.string.settings_group_service))

    SettingsCard {
        SettingsNavRow(
            icon = null,
            label = stringResource(R.string.label_provider),
            value = provider.displayName,
            onClick = { onPick(SettingsDialog.Provider) },
        )
        RowDivider(inset = 16.dp)
        SettingsNavRow(
            icon = null,
            label = "Base URL",
            value = stored.baseUrl.ifBlank { provider.defaultBaseUrl },
            onClick = { onPick(SettingsDialog.BaseUrl) },
        )
        RowDivider(inset = 16.dp)
        SettingsNavRow(
            icon = null,
            label = stringResource(R.string.label_model),
            // 自定义服务商初始没有模型，空串不该显示成一行空白
            value = stored.modelId.ifBlank { stringResource(R.string.api_model_unset) },
            onClick = { onPick(SettingsDialog.Model) },
        )
    }

    SettingsSectionLabel(stringResource(R.string.settings_group_auth))

    SettingsCard {
        SettingsNavRow(
            icon = null,
            label = "API Key",
            value = when {
                stored.hasKey -> maskKey(stored.apiKey)
                // 本地服务商通常不校验鉴权：显示「无需」而不是「未设置」，免得用户以为缺东西
                provider.apiKeyOptional -> stringResource(R.string.api_key_optional)
                else -> stringResource(R.string.api_key_unset)
            },
            onClick = { onPick(SettingsDialog.ApiKey) },
        )
        if (provider == ProviderKind.LOCAL) {
            Text(
                text = stringResource(R.string.api_local_hint),
                style = MaterialTheme.typography.labelSmall,
                color = colors.placeholder,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
            )
        }
        RowDivider(inset = 16.dp)
        SettingsActionRow(
            label = if (testing) stringResource(R.string.api_testing) else stringResource(R.string.api_test),
            hint = null,
            onClick = onTest,
        )
    }

    testResult?.let {
        Spacer(Modifier.size(12.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.labelMedium,
            color = colors.placeholder,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    /*
     * 通道与联网。
     *
     * 这一组是「扩展能力」的开关：协议通道决定走哪条端到端路径，
     * 智能搜索决定本轮要不要向服务端声明搜索工具，地区只进时间地点提醒。
     * 三者都不是必填项 —— 不碰它们，原来的行为一点不变。
     */
    SettingsSectionLabel(stringResource(R.string.settings_api_style))

    SettingsCard {
        SettingsNavRow(
            icon = null,
            label = stringResource(R.string.settings_api_style),
            value = stringResource(ApiStylePreference.fromId(stored.apiStyleId).labelRes),
            onClick = { onPick(SettingsDialog.ApiStyle) },
        )
        /*
         * 联网搜索只在扩展通道上有意义（`deepseek_web_search` 是那套协议里的工具），
         * OpenAI 兼容通道没有对应入口 —— 那就**不显示**这一行，而不是显示一个
         * 打开了也没用的开关。
         */
        if (stored.supportsWebSearch) {
            RowDivider(inset = 16.dp)
            SettingsSwitchRow(
                icon = null,
                label = stringResource(R.string.settings_web_search),
                checked = stored.webSearch,
                onCheckedChange = onWebSearch,
            )
        }
        RowDivider(inset = 16.dp)
        SettingsNavRow(
            icon = null,
            label = stringResource(R.string.settings_location),
            value = stored.locationRegion.ifBlank {
                stringResource(R.string.settings_location_hint_short)
            },
            onClick = { onPick(SettingsDialog.Location) },
        )
    }
}

@Composable
internal fun ApiProfilesPage(
    stored: StoredSettings, error: String?, onSelect: (ApiProfile) -> Unit,
    onNew: () -> Unit, onCopy: () -> Unit, onRename: (ApiProfile) -> Unit, onDelete: (ApiProfile) -> Unit,
) {
    val colors = LocalChatColors.current
    SettingsSectionLabel(stringResource(R.string.api_profiles_title))
    Text(stringResource(R.string.api_profiles_hint), style = MaterialTheme.typography.bodySmall,
        color = colors.placeholder, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
    error?.let { Text(it, color = colors.danger) }
    SettingsCard {
        stored.apiProfiles.forEachIndexed { index, profile ->
            if (index > 0) RowDivider(inset = 16.dp)
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onSelect(profile) }.padding(16.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.titleSmall,
                        color = if (profile.id == stored.activeApiProfileId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    val provider = ProviderKind.entries.firstOrNull { it.name == profile.providerId }
                    Text(listOfNotNull(provider?.displayName, profile.modelId.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = colors.placeholder,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (profile.baseUrl.isNotBlank()) Text(profile.baseUrl,
                        style = MaterialTheme.typography.labelSmall, color = colors.placeholder,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(if (profile.id == stored.activeApiProfileId) R.string.api_profile_selected
                        else if (profile.apiKeyEncrypted.isNotBlank()) R.string.api_profile_key_saved else R.string.api_key_unset),
                        style = MaterialTheme.typography.labelSmall, color = colors.placeholder)
                }
                IconCircleButton(AppIcons.Edit, stringResource(R.string.api_profile_rename), { onRename(profile) }, size = 36.dp, iconSize = 18.dp)
                if (stored.apiProfiles.size > 1) IconCircleButton(AppIcons.Trash,
                    stringResource(R.string.api_profile_delete), { onDelete(profile) }, size = 36.dp, iconSize = 18.dp)
            }
        }
    }
    Spacer(Modifier.size(16.dp))
    SettingsCard {
        SettingsActionRow(stringResource(R.string.api_profile_new), stringResource(R.string.api_profile_new_hint), onNew)
        RowDivider(inset = 16.dp)
        SettingsActionRow(stringResource(R.string.api_profile_copy), stringResource(R.string.api_profile_copy_hint), onCopy)
    }
}

// ══════════════════════════════════════════════════════════
//  二级：预设（系统提示词库）
// ══════════════════════════════════════════════════════════

/**
 * 预设 = **一段可命名的系统提示词**（设计文档 §9.1.1①）。
 *
 * 刻意不做结构化字段、不兼容外部角色卡格式 —— 那些约束最终都会限制表达。
 * 一个会话可挂多条，按顺序拼接。
 */
@Composable
private fun PresetsPage(
    presets: List<Preset>,
    onEdit: (Preset?) -> Unit,
    onDelete: (Preset) -> Unit,
) {
    val colors = LocalChatColors.current

    SettingsSectionLabel(stringResource(R.string.settings_group_presets))

    if (presets.isEmpty()) {
        SettingsCard {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.preset_empty),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.placeholder,
                )
            }
        }
    } else {
        SettingsCard {
            presets.forEachIndexed { index, preset ->
                if (index > 0) RowDivider(inset = 16.dp)
                PresetRow(
                    preset = preset,
                    onEdit = { onEdit(preset) },
                    onDelete = { onDelete(preset) },
                )
            }
        }
    }

    Spacer(Modifier.size(12.dp))

    SettingsCard {
        SettingsActionRow(
            label = stringResource(R.string.preset_new),
            hint = stringResource(R.string.preset_new_hint),
            onClick = { onEdit(null) },
        )
    }

    Spacer(Modifier.size(16.dp))

    Text(
        text = stringResource(R.string.preset_order_hint),
        style = MaterialTheme.typography.labelSmall,
        color = colors.placeholder,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun PresetRow(
    preset: Preset,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalChatColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preset.content,
                style = MaterialTheme.typography.labelSmall,
                color = colors.placeholder,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconCircleButton(
            icon = AppIcons.Trash,
            contentDescription = stringResource(R.string.preset_delete_action),
            onClick = onDelete,
            size = 34.dp,
            iconSize = 16.dp,
            tint = colors.processText,
        )
    }
}

/** 预设编辑器：名称 + 提示词正文。 */
@Composable
private fun PresetEditorDialog(
    initial: Preset?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, content: String) -> Unit,
) {
    val colors = LocalChatColors.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var content by remember { mutableStateOf(initial?.content.orEmpty()) }
    val nameFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }

    val canSave = name.isNotBlank() && content.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContainerShape)
                .background(colors.card)
                .padding(20.dp),
        ) {
            Text(
                text = if (initial == null) stringResource(R.string.preset_new) else stringResource(R.string.preset_edit),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(14.dp))

            FieldBox {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocus),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box {
                            if (name.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.preset_name_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.placeholder,
                                )
                            }
                            inner()
                        }
                    },
                )
            }

            Spacer(Modifier.size(10.dp))

            FieldBox {
                BasicTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 300.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box {
                            if (content.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.preset_body_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.placeholder,
                                )
                            }
                            inner()
                        }
                    },
                )
            }

            Spacer(Modifier.size(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextAction(stringResource(R.string.action_cancel), onClick = onDismiss)
                Spacer(Modifier.size(8.dp))
                TextAction(
                    text = stringResource(R.string.action_save),
                    primary = true,
                    enabled = canSave,
                    onClick = { onConfirm(name.trim(), content.trim()) },
                )
            }
        }
    }
}

@Composable
private fun FieldBox(content: @Composable () -> Unit) {
    val colors = LocalChatColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MenuShape)
            .background(colors.fieldBackground)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        content()
    }
}

// ══════════════════════════════════════════════════════════
//  二级：字体大小
// ══════════════════════════════════════════════════════════

@Composable
private fun FontPage(
    stored: StoredSettings,
    onFollowSystem: (Boolean) -> Unit,
    onScaleCommit: (Float) -> Unit,
) {
    val colors = LocalChatColors.current
    val follow = stored.followSystemFont

    // 拖动时只改本地状态做即时预览，松手才落盘 —— 否则一次拖动几十次写 DataStore
    var index by remember(stored.fontScale) {
        mutableStateOf(
            fontSizeSteps.indexOfFirst { it.first == stored.fontScale }.takeIf { it >= 0 } ?: 2,
        )
    }
    val previewScale = fontSizeSteps[index].first
    val baseDensity = LocalDensity.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.size(24.dp))

        // ── 预览：真实气泡，用当前档位的字号渲染 ──────────
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = baseDensity.density,
                fontScale = baseDensity.fontScale * previewScale,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = stringResource(R.string.font_preview_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onUserBubble,
                    modifier = Modifier
                        .clip(BubbleShapeOut)
                        .background(colors.userBubble)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        Spacer(Modifier.size(20.dp))

        Text(
            text = stringResource(R.string.font_scale_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.size(24.dp))

        // ── 底部控制区 ────────────────────────────────────
        SettingsCard {
            SettingsSwitchRow(
                label = stringResource(R.string.value_follow_system),
                icon = null,
                checked = follow,
                onCheckedChange = onFollowSystem,
            )
        }

        Spacer(Modifier.size(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "A",
                style = MaterialTheme.typography.labelMedium,
                color = colors.placeholder,
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = if (follow) stringResource(R.string.value_follow_system) else stringResource(fontSizeSteps[index].second),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (follow) colors.placeholder else MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = "A",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.placeholder,
            )
        }

        AppSlider(
            value = index.toFloat(),
            onValueChange = { index = it.toInt().coerceIn(0, fontSizeSteps.lastIndex) },
            intervals = fontSizeSteps.lastIndex,
            enabled = !follow,
            valueRange = 0f..fontSizeSteps.lastIndex.toFloat(),
            onValueChangeFinished = { onScaleCommit(fontSizeSteps[index].first) },
        )
    }
}

// ══════════════════════════════════════════════════════════
//  二级：关于
// ══════════════════════════════════════════════════════════

@Composable
private fun AboutPage(onOpenLicenses: () -> Unit) {
    val colors = LocalChatColors.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<UpdateChecker.Result?>(null) }

    SettingsSectionLabel(stringResource(R.string.about_version))

    SettingsCard {
        InfoRow(label = stringResource(R.string.about_version), value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        RowDivider(inset = 16.dp)
        InfoRow(label = stringResource(R.string.about_package), value = BuildConfig.APPLICATION_ID)
        RowDivider(inset = 16.dp)
        InfoRow(label = stringResource(R.string.license_type), value = APP_LICENSE)
        RowDivider(inset = 16.dp)
        InfoRow(label = stringResource(R.string.license_copyright), value = APP_COPYRIGHT)
    }

    /*
     * 「检查更新」单独一张卡：上面几行是只读信息，这一行是**可点**的 ——
     * 混排会出现"有的能点有的不能点"。
     */
    SettingsSectionLabel(stringResource(R.string.about_update))

    SettingsCard {
        SettingsActionRow(
            label = stringResource(R.string.about_check_update),
            hint = stringResource(R.string.about_current_version, BuildConfig.VERSION_NAME),
            onClick = {
                if (checking) return@SettingsActionRow
                checking = true
                checkResult = null
                scope.launch {
                    checkResult = UpdateChecker.check(BuildConfig.VERSION_NAME)
                    checking = false
                }
            },
            enabled = !checking,
        )
    }

    /*
     * 项目地址单独一组，不与上面的版本信息混排：
     * 这一行是**可点**的（点开跳到仓库），而上面几行是只读信息 ——
     * 混在一张卡里会出现「有的能点有的不能点」。
     */
    SettingsSectionLabel(stringResource(R.string.about_project))

    SettingsCard {
        LinkRow(
            label = stringResource(R.string.about_project_url),
            value = ProjectUrl,
            url = ProjectUrl,
        )
        RowDivider(inset = 16.dp)
        LinkRow(
            label = stringResource(R.string.about_original_project),
            value = OriginalProjectUrl,
            url = OriginalProjectUrl,
        )
    }

    SettingsSectionLabel(stringResource(R.string.about_licenses))

    /*
     * 只放一行入口，不把十几个组件铺在「关于」页里。
     *
     * 合规要看的是**每个组件的五项信息**（名称与版本 / 许可证 / 版权 / 官方链接 /
     * 许可证全文），那必须是一页能逐条读的清单，而不是几张 `名字 (MIT)` 的便签。
     */
    SettingsCard {
        SettingsNavRow(
            icon = AppIcons.FileText,
            label = stringResource(R.string.about_licenses),
            value = stringResource(R.string.licenses_count, thirdPartyLicenses.size),
            onClick = onOpenLicenses,
        )
    }

    Spacer(Modifier.size(16.dp))

    Text(
        text = stringResource(R.string.about_ai_disclaimer),
        style = MaterialTheme.typography.labelSmall,
        color = LocalChatColors.current.placeholder,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    checkResult?.let { result ->
        val context = LocalContext.current
        UpdateResultDialog(
            result = result,
            onDismiss = { checkResult = null },
            onOpen = { url ->
                checkResult = null
                openInBrowser(context, url)
            },
        )
    }
}

/**
 * 检查更新的结果。
 *
 * 三种结果一句话说清：有新版本 → 给一个「前往下载」；没有 → 「已是最新版」；
 * 查不出来 → 「检查失败」（不猜原因，也不催用户重试 —— 网络的事他比我们清楚）。
 */
@Composable
private fun UpdateResultDialog(
    result: UpdateChecker.Result,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val colors = LocalChatColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContainerShape)
                .background(colors.card)
                .padding(20.dp),
        ) {
            Text(
                text = when (result) {
                    is UpdateChecker.Result.Newer -> stringResource(R.string.update_new_title, result.release.tag)
                    UpdateChecker.Result.UpToDate -> stringResource(R.string.update_up_to_date)
                    is UpdateChecker.Result.Failed -> stringResource(R.string.update_failed)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (result is UpdateChecker.Result.Newer) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.about_current_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.placeholder,
                )
            }
            Spacer(Modifier.size(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextAction(stringResource(R.string.action_close), onClick = onDismiss)
                if (result is UpdateChecker.Result.Newer) {
                    Spacer(Modifier.size(8.dp))
                    TextAction(
                        stringResource(R.string.update_open),
                        primary = true,
                        onClick = { onOpen(result.release.url) },
                    )
                }
            }
        }
    }
}

/** 用浏览器打开链接；没有浏览器时静默失败（不值得为一步跳转崩掉整个设置页）。 */
private fun openInBrowser(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

// ══════════════════════════════════════════════════════════
//  三级：开源许可与版权
// ══════════════════════════════════════════════════════════

/**
 * 第三方组件清单。
 *
 * 合规要的是**每个组件的五项**：名称与版本、许可证、版权所有、官方链接、许可证全文。
 * 这必须是一页能逐条读的清单 —— 挤在「关于」页里的 `名字 (MIT)` 便签既查不到版本，
 * 也拿不到版权方。
 */
@Composable
private fun LicensesPage() {
    val colors = LocalChatColors.current
    var selected by remember { mutableStateOf<LicenseEntry?>(null) }

    SettingsSectionLabel(stringResource(R.string.licenses_third_party))

    SettingsCard {
        thirdPartyLicenses.forEachIndexed { index, entry ->
            if (index > 0) RowDivider(inset = 16.dp)
            LicenseRow(entry = entry, onClick = { selected = entry })
        }
    }

    SettingsSectionLabel(stringResource(R.string.licenses_reference_title))

    SettingsCard {
        referenceCredits.forEachIndexed { index, textRes ->
            if (index > 0) RowDivider(inset = 16.dp)
            Text(
                text = stringResource(textRes),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.processText,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }

    selected?.let { entry ->
        LicenseDetailDialog(entry = entry, onDismiss = { selected = null })
    }
}

/** 清单里的一行：名称 + 版本 · 许可证 + 版权方。 */
@Composable
private fun LicenseRow(entry: LicenseEntry, onClick: () -> Unit) {
    val colors = LocalChatColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(3.dp))
            Text(
                text = "${entry.version} · ${entry.license}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = entry.copyright,
                style = MaterialTheme.typography.labelSmall,
                color = colors.placeholder,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(8.dp))
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = colors.placeholder,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 单个组件的详情：五项信息齐全，链接可直接打开。 */
@Composable
private fun LicenseDetailDialog(entry: LicenseEntry, onDismiss: () -> Unit) {
    val colors = LocalChatColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContainerShape)
                .background(colors.card)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.size(12.dp))
            InfoRow(label = stringResource(R.string.license_version), value = entry.version)
            RowDivider(inset = 16.dp)
            InfoRow(label = stringResource(R.string.license_type), value = entry.license)
            RowDivider(inset = 16.dp)
            InfoRow(label = stringResource(R.string.license_copyright), value = entry.copyright)
            RowDivider(inset = 16.dp)
            LinkRow(
                label = stringResource(R.string.license_project),
                value = entry.projectUrl,
                url = entry.projectUrl,
            )
            RowDivider(inset = 16.dp)
            LinkRow(
                label = stringResource(R.string.license_full_text),
                value = entry.licenseUrl,
                url = entry.licenseUrl,
            )
            Spacer(Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextAction(stringResource(R.string.action_close), onClick = onDismiss)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  基础组件
// ══════════════════════════════════════════════════════════

@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit, showBack: Boolean = true) {
    val colors = LocalChatColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp),
    ) {
        /*
         * 一级页是**标签**，不是被推进来的一层：那里没有"上一页"，返回箭头
         * 只会让人以为点了会退出设置，实际却是跳回对话 —— 一个箭头两个含义。
         * 所以根页不放箭头，回对话走底部标签栏；二级页照旧。
         */
        if (showBack) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.fieldBackground)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.ChevronLeft,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun SettingsSectionLabel(text: String) {
    val colors = LocalChatColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.placeholder,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 10.dp),
    )
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    val colors = LocalChatColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MenuShape)
            .background(colors.card),
    ) {
        content()
    }
}

@Composable
private fun RowDivider(inset: androidx.compose.ui.unit.Dp = 52.dp) {
    val colors = LocalChatColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(1.dp)
            .background(colors.divider),
    )
}

/** 主题色菜单的宽度：七个 30dp 色块 + 间距，比默认菜单宽。 */
private val AccentMenuWidth = 266.dp

/**
 * 读系统音色列表的超时。
 *
 * `TextToSpeech` 的初始化回调在个别设备上可能永远不来（引擎被禁用 / 服务被冻结）。
 * 不设上限的话，音色页会永远停在「正在读取…」—— 用户既看不到音色、也不知道该怎么办。
 */
private const val TtsVoiceLoadTimeoutMs = 4_000L

/**
 * 主题色菜单里的一个色块。
 *
 * 选中的那个在**外面**套一圈描边，而不是压着色块边缘画 ——
 * 色块本身不因为"选中"而变形，只是外圈标出"当前是这个"。
 *
 * 描边色用 `onSurface`（浅色下近黑、深色下近白）：不写死黑色，
 * 否则深色主题下选中态就看不见了。
 */
@Composable
private fun AccentSwatch(
    color: AccentColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // 色块展示的是**当前主题模式下**的那个档位（深色下用提亮过的那一档）
    val dark = LocalIsDarkTheme.current
    val swatchDesc = stringResource(R.string.a11y_accent_color, color.label)
    Box(
        modifier = Modifier.size(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(color.primary(dark)),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
            )
        }
        /*
         * 点击层单独一个**圆形**节点，而且放在最上面。
         *
         * ⚠️ 不能把 `clickable` 挂到外层 Box 上：水波纹的形状就是**节点的裁剪形状**，
         * 而外层恰恰**不能裁剪**（选中那圈描边是外溢到 30dp 之外的，裁了就会缺一圈）——
         * 于是波纹就成了一个方块。这里让一层 30dp 的圆来吃点击，波纹自然是圆的。
         */
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .semantics { contentDescription = swatchDesc }
                .clickable(onClick = onClick),
        )
    }
}

/**
 * 一级入口行：图标 + 标题 +（可选）当前值 + 箭头。
 * 图标传 `null` 即为二级页面里的行 —— 二级不配图标。
 */
@Composable
fun SettingsNavRow(
    icon: ImageVector?,
    label: String,
    value: String?,
    onClick: () -> Unit,
) {
    val colors = LocalChatColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(21.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            // 标题吃掉全部剩余空间 → 值 + 箭头被顶到最右，形成一条整齐的右边线
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 190.dp),
            )
        }
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = colors.placeholder,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 二级页面的动作行：标题 +（可选）说明，无图标。 */
@Composable
private fun SettingsActionRow(
    label: String,
    hint: String?,
    onClick: () -> Unit,
    destructive: Boolean = false,
    /** 导入进行中之类的"暂时不能点"。 */
    enabled: Boolean = true,
) {
    val colors = LocalChatColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (destructive) colors.danger else MaterialTheme.colorScheme.onSurface,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = colors.placeholder,
            )
        }
    }
}

/** 开关行。 */
@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    /** 第二行小字：说明这个开关到底什么时候会震。 */
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(21.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalChatColors.current.placeholder,
                )
            }
        }
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 仓库地址。写成常量而不是资源：它是**代码里的一个事实**，不随界面语言变，
 * 也不该被翻译（翻成别的语言反而点不开）。改仓库时只改这一处。
 */
private const val ProjectUrl = "https://github.com/qwe123535/simxz-chat"

/**
 * 上游项目地址。
 *
 * 单独列一行而不是塞进说明文字：这是 MIT 的义务之一（保留原作者署名），
 * 而且用户想对比、想提 issue 到上游时，地址要是可点的。
 */
private const val OriginalProjectUrl = "https://github.com/Pokanshuu/Simple-Chat"

/**
 * 可点击的信息行 —— 样式与 [InfoRow] 一致，只是值用主色、整行可点。
 *
 * 用系统的 `ACTION_VIEW` 交给浏览器：应用内 WebView 打开 GitHub 只会带来
 * 一个返回键更别扭的界面，而"在浏览器里打开"也符合用户对这个动作的预期。
 */
@Composable
private fun LinkRow(label: String, value: String, url: String) {
    val colors = LocalChatColors.current
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                // 没有浏览器时不要崩：提示一句就够了
                runCatching { context.startActivity(intent) }
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 只读信息行。 */
@Composable
private fun InfoRow(label: String, value: String) {
    val colors = LocalChatColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(16.dp))
        /*
         * 值这一列**必须撑满剩余宽度**（`weight(1f)` 默认 `fill = true`），
         * 否则它只按自然宽度排版，右边缘随文字长短飘 ——
         * 短值停在半路，看起来就像"有的左对齐有的右对齐"。
         *
         * 撑满之后 `TextAlign.End` 才真的把每个值的右边缘对到卡片同一条线上。
         */
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.placeholder,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

// ══════════════════════════════════════════════════════════
//  弹窗
// ══════════════════════════════════════════════════════════

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    /** 一个选项都没有时显示的话。给 null 就是只留标题（其余调用方没有空列表）。 */
    emptyHint: String? = null,
    /** 列表下面多出来的那一条动作（例如「手动填写模型」），与选中状态无关。 */
    extraAction: Pair<String, () -> Unit>? = null,
) {
    val colors = LocalChatColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContainerShape)
                .background(colors.card)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )

            if (options.isEmpty()) {
                emptyHint?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.placeholder,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                    )
                }
            } else {
                /*
                 * 用 LazyColumn 而不是 Column：自定义端点的 `/models` 动辄四五十个
                 * （用户那台中转站是 45 个），一次性铺开会把弹窗顶穿屏幕、底部
                 * 超出窗口的部分**点不到也看不见** —— 看起来又像"列表没显示"。
                 */
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(options.size) { index ->
                        val (item, label) = options[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (item == selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                            AppMenuCheck(selected = item == selected)
                        }
                    }
                }
            }

            extraAction?.let { (label, onClick) ->
                if (options.isNotEmpty()) RowDivider(inset = 16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    secret: Boolean = false,
    /** 返回非空字符串 = 校验失败的原因；此时不关窗、不提交。 */
    validate: ((String) -> String?)? = null,
) {
    val colors = LocalChatColors.current
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContainerShape)
                .background(colors.card)
                .padding(20.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PillShape)
                    .background(colors.fieldBackground)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        // 用户开始改了就把旧的红字收掉，别让它跟着新内容一起错着
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = !secret,
                    visualTransformation = if (secret) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.placeholder,
                                )
                            }
                            inner()
                        }
                    },
                )
            }

            Spacer(Modifier.size(18.dp))

            /*
             * 校验失败时**不关窗**：把原因就地写在按钮上方，用户改一个字就能再点。
             * 关掉窗再弹一句提示，等于让他重新打开一遍再粘一次。
             */
            error?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalChatColors.current.danger,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextAction(stringResource(R.string.action_cancel), onClick = onDismiss)
                Spacer(Modifier.size(8.dp))
                TextAction(
                    stringResource(R.string.action_save),
                    primary = true,
                    onClick = {
                        val problem = validate?.invoke(value)
                        if (problem != null) error = problem else onConfirm(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun TextAction(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalChatColors.current
    val tint = when {
        !enabled -> colors.placeholder
        destructive -> colors.danger
        primary -> MaterialTheme.colorScheme.primary
        else -> colors.processText
    }
    Box(
        modifier = Modifier
            .clip(PillShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = tint,
        )
    }
}

private fun maskKey(key: String): String {
    if (key.length <= 8) return "••••"
    return key.take(4) + "••••" + key.takeLast(4)
}




/* ── AI 行为 ─────────────────────────────────────────────────────────── */

/**
 * AI 行为：语气、思考怎么显示、以及几个会改变回答风格的模式。
 *
 * 与 API 页的分工：那边管"发给谁、发多少"（端点、Key、温度、最大 tokens），
 * 这边管"怎么想、怎么呈现"，换服务商不用重设。
 */
@Composable
private fun BehaviorPage(
    stored: StoredSettings,
    onTone: (String) -> Unit,
    onThinkingScroll: (Boolean) -> Unit,
    onStepThinking: (Boolean) -> Unit,
    onPreserveReasoning: (Boolean) -> Unit,
    onLearningMode: (Boolean) -> Unit,
    onSoftCompaction: (Boolean) -> Unit,
    onMinimalMode: (Boolean) -> Unit,
    onContextWindow: (Int) -> Unit,
) {
    val colors = LocalChatColors.current

    SettingsSectionLabel(stringResource(R.string.behavior_group_tone))
    SettingsCard {
        SettingsChoiceRow(
            label = stringResource(R.string.behavior_tone),
            value = if (stored.toneId == "CODING") {
                stringResource(R.string.behavior_tone_coding)
            } else {
                stringResource(R.string.behavior_tone_chat)
            },
            selectedId = stored.toneId,
            options = listOf(
                "CHAT" to stringResource(R.string.behavior_tone_chat),
                "CODING" to stringResource(R.string.behavior_tone_coding),
            ),
            onSelect = onTone,
        )
    }
    Text(
        text = stringResource(R.string.behavior_tone_hint),
        style = MaterialTheme.typography.bodySmall,
        color = colors.placeholder,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, end = 4.dp, bottom = 8.dp),
    )

    SettingsSectionLabel(stringResource(R.string.behavior_group_thinking))
    SettingsCard {
        SettingsSwitchRow(
            label = stringResource(R.string.behavior_thinking_scroll),
            subtitle = stringResource(R.string.behavior_thinking_scroll_hint),
            checked = stored.thinkingScroll,
            onCheckedChange = onThinkingScroll,
        )
        RowDivider(inset = 16.dp)
        SettingsSwitchRow(
            label = stringResource(R.string.behavior_step_thinking),
            subtitle = stringResource(R.string.behavior_step_thinking_hint),
            checked = stored.stepThinking,
            onCheckedChange = onStepThinking,
        )
        RowDivider(inset = 16.dp)
        SettingsSwitchRow(
            label = stringResource(R.string.behavior_preserve_reasoning),
            subtitle = stringResource(R.string.behavior_preserve_reasoning_hint),
            checked = stored.preserveReasoning,
            onCheckedChange = onPreserveReasoning,
        )
    }

    SettingsSectionLabel(stringResource(R.string.behavior_group_context))
    SettingsCard {
        SettingsChoiceRow(
            label = stringResource(R.string.behavior_context_window),
            value = contextWindowLabel(stored.contextWindowOverride),
            selectedId = stored.contextWindowOverride.toString(),
            options = ContextWindowOptions.map { value -> value.toString() to contextWindowLabel(value) },
            onSelect = { onContextWindow(it.toIntOrNull() ?: 0) },
        )
    }
    Text(
        text = stringResource(R.string.behavior_context_window_hint),
        style = MaterialTheme.typography.labelSmall,
        color = colors.placeholder,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )

    SettingsSectionLabel(stringResource(R.string.behavior_group_mode))
    SettingsCard {
        SettingsSwitchRow(
            label = stringResource(R.string.behavior_learning),
            subtitle = stringResource(R.string.behavior_learning_hint),
            checked = stored.learningMode,
            onCheckedChange = onLearningMode,
        )
        RowDivider(inset = 16.dp)
        SettingsSwitchRow(
            label = stringResource(R.string.behavior_soft_compaction),
            subtitle = stringResource(R.string.behavior_soft_compaction_hint),
            checked = stored.softCompaction,
            onCheckedChange = onSoftCompaction,
        )
        RowDivider(inset = 16.dp)
        SettingsSwitchRow(
            label = stringResource(R.string.behavior_minimal),
            subtitle = stringResource(R.string.behavior_minimal_hint),
            checked = stored.minimalMode,
            onCheckedChange = onMinimalMode,
        )
    }
    Spacer(Modifier.height(24.dp))
}

/* ── 输出与预览 ──────────────────────────────────────────────────────── */

@Composable
private fun OutputPage(
    stored: StoredSettings,
    onCodeWrap: (Boolean) -> Unit,
    onProcessExpand: (Boolean) -> Unit,
    onAutoCollapse: (Boolean) -> Unit,
) {
    val colors = LocalChatColors.current

    SettingsSectionLabel(stringResource(R.string.output_group_code))
    SettingsCard {
        SettingsSwitchRow(
            label = stringResource(R.string.output_code_wrap),
            subtitle = stringResource(R.string.output_code_wrap_hint),
            checked = stored.codeWrap,
            onCheckedChange = onCodeWrap,
        )
    }

    SettingsSectionLabel(stringResource(R.string.output_group_process))
    SettingsCard {
        SettingsSwitchRow(
            label = stringResource(R.string.output_process_expand),
            subtitle = stringResource(R.string.output_process_expand_hint),
            checked = stored.processAutoExpand,
            onCheckedChange = onProcessExpand,
        )
        RowDivider(inset = 16.dp)
        // 思考折叠是反的：存的是「自动折叠」，页面上问的是「默认展开」
        SettingsSwitchRow(
            label = stringResource(R.string.output_thinking_collapse),
            subtitle = stringResource(R.string.output_thinking_collapse_hint),
            checked = stored.autoCollapseThinking,
            onCheckedChange = onAutoCollapse,
        )
    }

    /*
     * 这里原本还有一格「链接打开方式（内置 / 系统浏览器）」—— LineCode 有内置 WebView，
     * Simple-Chat 没有（这是刻意的：见 SourceSheet 的说明）。搬一个选项过来却点不出
     * 第二种行为，比没有这个选项更糟，所以整格撤掉。
     */
    Spacer(Modifier.height(24.dp))
}

/* ── 工具 ────────────────────────────────────────────────────────────── */

@Composable
private fun StatsPage(rows: List<com.simplechat.app.db.ModelUsageRow>?) {
    val colors = LocalChatColors.current

    if (rows == null) {
        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.stats_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.placeholder,
            )
        }
        return
    }
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.stats_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.placeholder,
            )
        }
        return
    }

    val totalRequests = rows.sumOf { it.requests }
    val totalIn = rows.sumOf { it.inputTokens }
    val totalOut = rows.sumOf { it.outputTokens }

    SettingsSectionLabel(stringResource(R.string.stats_group_summary))
    SettingsCard {
        SettingsStatRow(stringResource(R.string.stats_requests), totalRequests.toString())
        RowDivider(inset = 16.dp)
        SettingsStatRow(stringResource(R.string.stats_input), formatTokenCount(totalIn))
        RowDivider(inset = 16.dp)
        SettingsStatRow(stringResource(R.string.stats_output), formatTokenCount(totalOut))
        RowDivider(inset = 16.dp)
        SettingsStatRow(
            label = stringResource(R.string.stats_total),
            value = formatTokenCount(totalIn + totalOut),
        )
    }

    SettingsSectionLabel(stringResource(R.string.stats_group_models))
    SettingsCard {
        rows.forEachIndexed { index, row ->
            if (index > 0) RowDivider(inset = 16.dp)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = row.model,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.stats_model_line,
                        row.requests,
                        formatTokenCount(row.inputTokens),
                        formatTokenCount(row.outputTokens),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.placeholder,
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.stats_note),
        style = MaterialTheme.typography.bodySmall,
        color = colors.placeholder,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, end = 4.dp, bottom = 8.dp),
    )
    Spacer(Modifier.height(24.dp))
}

/* ── 记忆 ────────────────────────────────────────────────────────────── */

@Composable
private fun MemoryPage(
    entries: List<MemoryEntry>,
    onAdd: () -> Unit,
    onEdit: (MemoryEntry) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = LocalChatColors.current

    SettingsSectionLabel(stringResource(R.string.memory_group))
    SettingsCard {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.memory_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.placeholder,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            )
        } else {
            entries.forEachIndexed { index, entry ->
                if (index > 0) RowDivider(inset = 16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(entry) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { onDelete(entry.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = AppIcons.Trash,
                            contentDescription = stringResource(R.string.memory_delete_title),
                            tint = colors.placeholder,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        CapsuleButton(text = stringResource(R.string.memory_add), onClick = onAdd)
    }
    Text(
        text = stringResource(R.string.memory_hint),
        style = MaterialTheme.typography.bodySmall,
        color = colors.placeholder,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, end = 4.dp, bottom = 8.dp),
    )
    Spacer(Modifier.height(24.dp))
}

/* ── 错误日志 ────────────────────────────────────────────────────────── */

@Composable
private fun LogsPage(
    text: String,
    crash: String,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalChatColors.current
    val hasContent = text.isNotBlank() || crash.isNotBlank()

    SettingsSectionLabel(stringResource(R.string.logs_group))
    SettingsCard {
        if (!hasContent) {
            Text(
                text = stringResource(R.string.logs_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.placeholder,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            )
        } else {
            Text(
                text = listOf(text, crash).filter { it.isNotBlank() }.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                color = colors.processText,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        CapsuleButton(text = stringResource(R.string.logs_refresh), onClick = onRefresh)
        CapsuleButton(text = stringResource(R.string.logs_clear), onClick = onClear, enabled = hasContent)
    }
    Text(
        text = stringResource(R.string.logs_note),
        style = MaterialTheme.typography.bodySmall,
        color = colors.placeholder,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, end = 4.dp, bottom = 8.dp),
    )
    Spacer(Modifier.height(24.dp))
}

/* ── 后台保护 ────────────────────────────────────────────────────────── */

@Composable
private fun KeepAlivePage(
    stored: StoredSettings,
    onForeground: (Boolean) -> Unit,
    onWakeLock: (Boolean) -> Unit,
    onBattery: (Boolean) -> Unit,
    onAudio: (Boolean) -> Unit,
) {
    val colors = LocalChatColors.current
    val context = LocalContext.current

    SettingsSectionLabel(stringResource(R.string.keepalive_group_service))
    SettingsCard {
        SettingsSwitchRow(
            label = stringResource(R.string.keepalive_foreground),
            subtitle = stringResource(R.string.keepalive_foreground_hint),
            checked = stored.generationNotification,
            onCheckedChange = onForeground,
        )
        RowDivider(inset = 16.dp)
        SettingsSwitchRow(
            label = stringResource(R.string.keepalive_wakelock),
            subtitle = stringResource(R.string.keepalive_wakelock_hint),
            checked = stored.keepAliveWakeLock,
            onCheckedChange = onWakeLock,
        )
    }

    SettingsSectionLabel(stringResource(R.string.keepalive_group_system))
    SettingsCard {
        SettingsSwitchRow(
            label = stringResource(R.string.keepalive_battery),
            subtitle = stringResource(R.string.keepalive_battery_hint),
            checked = stored.keepAliveIgnoreBattery,
            onCheckedChange = { enabled ->
                onBattery(enabled)
                // 开关只是"提醒我去设置过"的记录；真正要用户点一下系统页
                if (enabled) runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
        RowDivider(inset = 16.dp)
        SettingsSwitchRow(
            label = stringResource(R.string.keepalive_audio),
            subtitle = stringResource(R.string.keepalive_audio_hint),
            checked = stored.keepAliveFakeAudio,
            onCheckedChange = onAudio,
        )
    }
    Text(
        text = stringResource(R.string.keepalive_note),
        style = MaterialTheme.typography.bodySmall,
        color = colors.placeholder,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, end = 4.dp, bottom = 8.dp),
    )
    Spacer(Modifier.height(24.dp))
}

/* ── 安全 ────────────────────────────────────────────────────────────── */

@Composable
private fun SecurityPage(
    stored: StoredSettings,
    onAppLock: (Boolean) -> Unit,
    onSecureScreen: (Boolean) -> Unit,
    onAllowHttp: (Boolean) -> Unit,
) {
    val colors = LocalChatColors.current

    SettingsSectionLabel(stringResource(R.string.security_group_lock))
    SettingsCard {
        SettingsSwitchRow(
            label = stringResource(R.string.security_app_lock),
            subtitle = stringResource(R.string.security_app_lock_hint),
            checked = stored.appLock,
            onCheckedChange = onAppLock,
        )
        RowDivider(inset = 16.dp)
        SettingsSwitchRow(
            label = stringResource(R.string.security_secure_screen),
            subtitle = stringResource(R.string.security_secure_screen_hint),
            checked = stored.secureScreen,
            onCheckedChange = onSecureScreen,
        )
    }

    SettingsSectionLabel(stringResource(R.string.security_group_network))
    SettingsCard {
        SettingsSwitchRow(
            label = stringResource(R.string.security_allow_http),
            subtitle = stringResource(R.string.security_allow_http_hint),
            checked = stored.allowAnyHttp,
            onCheckedChange = onAllowHttp,
        )
    }
    Text(
        text = stringResource(R.string.security_note),
        style = MaterialTheme.typography.bodySmall,
        color = colors.placeholder,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, end = 4.dp, bottom = 8.dp),
    )
    Spacer(Modifier.height(24.dp))
}

/* ── 共用小部件 ──────────────────────────────────────────────────────── */

/** 一行「标签 + 当前值 + 箭头」，点开是菜单。用于语气、浏览器、搜索服务商这类单选。 */
@Composable
private fun SettingsChoiceRow(
    label: String,
    value: String,
    selectedId: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val colors = LocalChatColors.current

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = AppIcons.ChevronDown,
                contentDescription = null,
                tint = colors.placeholder,
                modifier = Modifier.size(16.dp),
            )
        }
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, text) ->
                AppMenuItem(
                    label = text,
                    selected = id == selectedId,
                    onClick = {
                        open = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}

/** 统计页的一行：左边名字，右边数字。 */
@Composable
private fun SettingsStatRow(label: String, value: String) {
    val colors = LocalChatColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.placeholder,
        )
    }
}

/** 千位分隔。手写而不用 `String.format("%,d")`：后者跟系统区域走，有些地区用空格分隔。 */
private fun formatTokenCount(value: Long): String {
    val text = value.toString()
    if (text.length <= 3) return text
    val builder = StringBuilder(text.length + text.length / 3)
    var count = 0
    for (index in text.length - 1 downTo 0) {
        if (count > 0 && count % 3 == 0) builder.append(',')
        builder.append(text[index])
        count++
    }
    return builder.reverse().toString()
}


/**
 * 快捷指令页。
 *
 * 只做"列表 + 增删改"，不做排序：条数一般不超过十条，拖拽排序的收益
 * 抵不上它带来的手势冲突（这一页在竖向滚动容器里）。
 */
/**
 * 等待文案页。
 *
 * 只让用户管**文字**，省略号交给动画补：等待时那三点是逐秒出现的，
 * 用户自己写「思考中…」会变成「思考中....」。
 */
@Composable
private fun LoadingWordsPage(
    words: List<String>,
    useThemeColor: Boolean,
    onUseThemeColor: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = LocalChatColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(
                if (words.isEmpty()) R.string.loading_words_empty else R.string.loading_words_hint,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.placeholder,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        )
        SettingsCard {
            SettingsSwitchRow(
                label = stringResource(R.string.loading_words_use_theme_color),
                subtitle = stringResource(R.string.loading_words_use_theme_color_hint),
                checked = useThemeColor,
                onCheckedChange = onUseThemeColor,
                icon = AppIcons.Palette,
            )
            words.forEach { word ->
                RowDivider(inset = 16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(word) }
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = word,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp),
                    )
                    IconCircleButton(
                        icon = AppIcons.Trash,
                        contentDescription = stringResource(R.string.loading_words_delete_title),
                        onClick = { onDelete(word) },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        CapsuleButton(
            text = stringResource(R.string.loading_words_add),
            onClick = onAdd,
            leadingIcon = AppIcons.PlusCircle,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SlashCommandsPage(
    commands: List<SlashCommand>,
    onAdd: () -> Unit,
    onEdit: (SlashCommand) -> Unit,
    onDelete: (SlashCommand) -> Unit,
) {
    val colors = LocalChatColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSectionLabel(stringResource(R.string.slash_group))
        SettingsCard {
            if (commands.isEmpty()) {
                Text(
                    text = stringResource(R.string.slash_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.placeholder,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                commands.forEachIndexed { index, command ->
                    if (index > 0) RowDivider(inset = 16.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(command) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "/" + command.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = command.text,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.placeholder,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        IconCircleButton(
                            icon = AppIcons.Trash,
                            contentDescription = stringResource(R.string.slash_delete_title),
                            onClick = { onDelete(command) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        CapsuleButton(
            text = stringResource(R.string.slash_add),
            onClick = onAdd,
            leadingIcon = AppIcons.PlusCircle,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.slash_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.placeholder,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 收藏夹。
 *
 * 两段：消息在前（更常用），会话在后。点会话直接切到聊天标签打开它 ——
 * 收藏夹的意义就是"快速回到那段对话"，只显示不能跳等于没有。
 */
@Composable
private fun FavoritesPage(
    messages: List<FavoriteMessageRow>,
    conversations: List<ConversationEntity>,
    onOpenConversation: (String) -> Unit,
    onUnfavoriteMessage: (String) -> Unit,
    onUnfavoriteConversation: (String) -> Unit,
) {
    val colors = LocalChatColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        if (messages.isEmpty() && conversations.isEmpty()) {
            Text(
                text = stringResource(R.string.favorites_empty),
                style = MaterialTheme.typography.bodySmall,
                color = colors.placeholder,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 24.dp),
            )
            return@Column
        }

        if (messages.isNotEmpty()) {
            SettingsSectionLabel(stringResource(R.string.favorites_group_messages))
            SettingsCard {
                messages.forEachIndexed { index, row ->
                    if (index > 0) RowDivider(inset = 16.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenConversation(row.conversationId) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.content.lineSequence().firstOrNull().orEmpty().take(80),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        IconCircleButton(
                            icon = AppIcons.Star,
                            contentDescription = stringResource(R.string.favorite_remove),
                            onClick = { onUnfavoriteMessage(row.id) },
                        )
                    }
                }
            }
        }

        if (conversations.isNotEmpty()) {
            SettingsSectionLabel(stringResource(R.string.favorites_group_conversations))
            SettingsCard {
                conversations.forEachIndexed { index, conversation ->
                    if (index > 0) RowDivider(inset = 16.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenConversation(conversation.id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = conversation.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        IconCircleButton(
                            icon = AppIcons.Star,
                            contentDescription = stringResource(R.string.favorite_remove),
                            onClick = { onUnfavoriteConversation(conversation.id) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 上下文窗口的候选值：0 = 自动（按模型表）。 */
private val ContextWindowOptions = listOf(0, 32_768, 65_536, 131_072, 262_144, 524_288, 1_048_576)

/** 窗口的显示名：131072 → 128K，1048576 → 1M。 */
@Composable
private fun contextWindowLabel(tokens: Int): String = when {
    tokens <= 0 -> stringResource(R.string.behavior_context_auto)
    tokens % 1_048_576 == 0 -> "${tokens / 1_048_576}M"
    tokens % 1024 == 0 -> "${tokens / 1024}K"
    else -> tokens.toString()
}
