package com.simplechat.app.data

import android.content.Context
import androidx.annotation.StringRes
import com.simplechat.app.R
import com.simplechat.app.net.ApiStyle
import com.simplechat.app.net.ProviderKind
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "simplechat_settings",
)

/** 语言档位（[AppLanguage] 的枚举名）。抽到文件级，供 [readStoredLanguageSync] 共用。 */
private val LANGUAGE_KEY = stringPreferencesKey("language_id")

/**
 * 「这个端点有哪些模型」的持久化格式（换行分隔）。
 *
 * 为什么不存 JSON：这里只有一串 id，没有结构；换行在模型 id 里不可能出现
 * （OpenAI 兼容端点用 `[a-zA-Z0-9._:/-]`），于是编解码就是两行 `map`，
 * 而 JSON 要引解析器、还要处理坏数据。读取侧对任何异常输入都只说"没有列表"。
 */
internal object ModelIdList {

    /**
     * 上限。正常端点几十个，这里只为拦住"网关把整张目录表倒出来"的极端情况 ——
     * 几千个 id 会撑大 DataStore，而选择器里也没人翻得到底。
     */
    const val MAX_IDS = 500

    fun encode(ids: List<String>): String = ids
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(MAX_IDS)
        .joinToString("\n")

    fun decode(raw: String?): List<String> = raw
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.distinct()
        ?.take(MAX_IDS)
        ?.toList()
        .orEmpty()
}

/**
 * 同步读语言档位 —— 只为 `attachBaseContext`：那时一切异步入口都还没起来。
 * 值就一行，阻塞读在毫秒级；读不出来（IO 异常）按 [AppLanguage.SYSTEM] 处理，不拦启动。
 */
internal fun readStoredLanguageSync(context: Context): AppLanguage = runCatching {
    runBlocking {
        AppLanguage.fromId(context.settingsDataStore.data.first()[LANGUAGE_KEY])
    }
}.getOrDefault(AppLanguage.SYSTEM)

/** 持久化的全局配置。 */
data class StoredSettings(
    val providerId: String = "DEEPSEEK",
    val baseUrl: String = "https://api.deepseek.com",
    val modelId: String = "deepseek-flash",
    /**
     * 手动指定上下文窗口；0 = 用模型表里的默认值。
     *
     * 为什么需要：模型表只认得"有名的"那些模型，自定义 / 本地部署的模型一律
     * 按 128K 算。有人把 Ollama 的 num_ctx 开到 512K、或者服务商实际给了 32K，
     * 预算条与自动压缩都得按真实值算，否则要么早压、要么压晚了被服务端 400。
     */
    val contextWindowOverride: Int = 0,
    val apiKey: String = "",
    val apiProfiles: List<ApiProfile> = emptyList(),
    val activeApiProfileId: String = "legacy",
    val autoReadAloud: Boolean = false,
    val showImageSkipNotice: Boolean = true,
    val reasoningLevelId: String = "OFF",
    val temperature: Float = 1.0f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 0,
    /** 外观：SYSTEM / LIGHT / DARK。 */
    val themeModeId: String = "SYSTEM",
    /** 主题色：AccentColor.name，见 ui/theme/Theme.kt。 */
    val accentColorId: String = "BLUE",
    /** 字号倍率。 */
    val fontScale: Float = 1.0f,
    /** 字号是否跟随系统辅助功能设置。 */
    val followSystemFont: Boolean = true,
    /** 思考内容是否自动折叠（关闭则默认展开，需手动收起）。 */
    val autoCollapseThinking: Boolean = true,

    /** 等待首 token 时轮换显示的短句；空 = 用内置默认那组。 */
    val loadingWords: List<String> = emptyList(),

    /** 等待文案是否改用强调色（默认跟思考栏同色，显得安静）。 */
    val loadingWordsUseThemeColor: Boolean = false,

    /**
     * 界面风格。见 `UiStyle`：`ORIGINAL` 原版，`DEEPSEEK` 是 DeepSeek 那套皮肤。
     *
     * 存字符串而不是枚举：DataStore 里存枚举要自定义 Serializer，而这里
     * 唯一的消费者就是主题层，多一个转换层没有收益。
     */
    val uiStyleId: String = "ORIGINAL",
    /**
     * 无痕会话的 id。
     *
     * 这些会话在抽屉里看不到、不进搜索与统计；离开时会问一句「保留还是删除」。
     * 为什么不用"内存里根本不存在"的做法：消息、附件、分支、压缩全都要落库才能工作，
     * 真正不落库等于把半个数据层重写一遍。这里退一步 —— 临时落库、离开即删，
     * 用对话框保证用户知情。
     */
    val incognitoIds: Set<String> = emptySet(),

    /**
     * DeepSeek 皮肤下是否显示"高级功能"（思考块、每句用量、操作栏、`/` 指令）。
     *
     * 那套界面本来没有这些东西，默认藏起来；想留着用就在这里打开。
     */
    val showExtrasInDeepSeek: Boolean = false,
    /**
     * 回车直接发送。
     *
     * 默认 false —— 手机输入法上回车键本来就常被用来换行，聊天框里换行也很常见；
     * 想"打完就发"的人自己开。
     */
    val sendOnEnter: Boolean = false,
    /**
     * 正文字号之外的**行高**倍率（1 = 默认）。
     *
     * 与 [fontScale] 分开：字号是"看得清"，行高是"读得松"。长文阅读时把行高
     * 拉到 1.3 比把字号放大一档更舒服，而两者的取值理由完全不同。
     */
    val lineHeightScale: Float = 1f,
    /** 正文用等宽字体（代码块本来就是等宽，这里只管正文）。 */
    val bodyMonospace: Boolean = false,
    /**
     * 生成时挂一条通知（前台服务）。
     *
     * 关掉之后生成仍在进行，只是不再起前台服务：进程被杀就停。
     * 这是"不想在通知栏看到它"和"锁屏后也一定写完"之间的取舍，交给用户。
     */
    val generationNotification: Boolean = true,
    /** 应用锁：打开 App 或从后台回来时先验证设备密码 / 指纹。 */
    val appLock: Boolean = false,
    /** 禁止截屏与多任务预览（`FLAG_SECURE`）。 */
    val secureScreen: Boolean = false,

    /** 首次启动引导是否已经走过（或跳过）。只影响引导弹层，不影响任何功能。 */
    val onboardingDone: Boolean = false,

    /*
     * ── AI 行为 ──────────────────────────────────────────────
     * 对应 LineCode 的 BehaviorSettingsPage。这里放的是"怎么想、怎么显示"，
     * 与服务商无关；推理强度本身仍跟着 API 配置走（见 ApiProfile.reasoningLevelId）。
     */
    /** 语气：`CHAT`（闲聊，松弛）/ `CODING`（严谨，默认）。 */
    val toneId: String = "CHAT",
    /** 生成时视图跟随最新内容（关掉则停在原位，方便边看边往回翻）。 */
    val thinkingScroll: Boolean = true,
    /** 分步思考：要求模型先列步骤再作答。 */
    val stepThinking: Boolean = false,
    /** 把上一轮的 reasoning 回传给服务商 —— 部分模型多轮时依赖它。 */
    val preserveReasoning: Boolean = false,
    /** 学习模式：回答附带解释与延伸，慢一点但更适合学新东西。 */
    val learningMode: Boolean = false,
    /** 软压缩：接近窗口上限时先自动摘要，而不是等报错再压。 */
    val softCompaction: Boolean = true,
    /** 极简模式：只留正文，藏掉头像、时间戳与操作栏。 */
    val minimalMode: Boolean = false,

    /* ── 输出与预览 ── */
    /** 代码块自动换行（关掉则横向滚动）。 */
    val codeWrap: Boolean = true,
    /** 思考 / 过程块默认展开。 */
    val processAutoExpand: Boolean = false,
    /** 链接打开方式：`BUILTIN`（内置浏览器）/ `EXTERNAL`（系统浏览器）。 */
    val browserModeId: String = "EXTERNAL",

    /* ── 工具 ── */
    /** 联网搜索服务商：`OFFICIAL`（服务商自带）/ `BING_RSS`（免费必应）。 */
    val searchProviderId: String = "OFFICIAL",
    /** 图像理解用的模型 id（空 = 用当前对话模型）。 */
    val imageModelId: String = "",

    /* ── 后台保护 ── */
    /** 生成时持有 WakeLock，锁屏后继续跑。 */
    val keepAliveWakeLock: Boolean = false,
    /** 引导把 App 加进电池优化白名单。 */
    val keepAliveIgnoreBattery: Boolean = false,
    /** 静音播放一段音频保活（部分 ROM 对音频进程更宽容）。 */
    val keepAliveFakeAudio: Boolean = false,

    /* ── 安全 ── */
    /** 允许非本机的明文 HTTP 端点。 */
    val allowAnyHttp: Boolean = true,
    /*
     * 自定义配色（ARGB 整数；null = 跟随主题色 / 默认色板）。
     *
     * 逐项而不是"整套配色方案"：用户常常只想改一个气泡色，其余保持默认。
     * 存 int 而不是十六进制字符串 —— `Color.value` 本来就是 32 位，
     * 存字符串还要自己解析、还要处理 `#` 有没有写。
     *
     * 注意这些颜色**不区分明暗**：用户选了什么就是什么，深色模式下也照用。
     * 自动按明暗改色会把用户刚选的颜色改掉，那就不叫自定义了。
     */
    val customPrimary: Int? = null,
    val customUserBubble: Int? = null,
    val customPageBackground: Int? = null,
    val customCard: Int? = null,
    val customText: Int? = null,
    /** 发送消息时轻震一下。 */
    val hapticSend: Boolean = true,
    /** 长按输入框打开对话设置面板时轻震。 */
    val hapticLongPress: Boolean = true,
    /** 左滑删除会话、确认删除时轻震。 */
    val hapticSwipeDelete: Boolean = true,
    /** 新对话默认模式：CREATIVE / ROLEPLAY。 */
    val defaultModeId: String = "CREATIVE",
    /** 界面语言：AppLanguage 的枚举名，见 data/LocaleHelper.kt。 */
    val languageId: String = "SYSTEM",
    /**
     * 协议通道：`AUTO` / `OPENAI` / `ANTHROPIC`。
     *
     * `AUTO` 按端点解析 —— DeepSeek 官方走扩展通道，其余走 OpenAI 兼容（见 `ApiStyle`）。
     */
    val apiStyleId: String = "AUTO",
    /**
     * 智能搜索（联网搜索）开关。
     *
     * 默认**关**：搜索会让这一轮变慢、变贵，而长文创作绝大多数轮次用不上。
     * 与官方 Chat Demo 的默认值一致。
     */
    val webSearch: Boolean = false,
    /**
     * 地区（用于「时间地点提醒」里的地点那一项），自由文本，留空则不填。
     *
     * 不做省/市二级联动：那套 13KB 的数据与三级选择器，收益远小于维护成本，
     * 而提醒里这一项本来就只是给模型一个「用户在哪儿」的粗略印象。
     */
    val locationRegion: String = "",
    /**
     * 朗读音色。
     *
     * - [ttsVoiceName]：系统 TTS 引擎里的 `Voice.name`（机器标识，用来精确选中）。
     *   空串 = 跟随系统默认（按界面语言挑一个本地音色）。
     * - [ttsVoiceLabel]：选中时给用户看的名字（地区 + 是否联网）。**单独存一份**，
     *   因为音色名是 `zh-cn-x-ccc-local` 这种机器 ID，界面不可能现算，而每次进设置页
     *   都去初始化一次 TTS 引擎只为显示一个名字也不划算。
     */
    val ttsVoiceName: String = "",
    val ttsVoiceLabel: String = "",

    /**
     * 当前端点 `/models` 拉回来的模型 id（[ModelIdList] 编解码）。
     *
     * 存下来而不是只放内存：拉取动作发生在「测试连接」或对话面板的刷新里，
     * 而用户随后进设置页选模型可能已经过了一次重启 —— 列表一丢，选择器就只剩
     * 内置能力表（自定义端点干脆是空的），于是"能拉到模型，却选不到模型"。
     * 端点（服务商 / Base URL）一换就清空，见 [SettingsStore.setProvider]。
     */
    val modelIds: List<String> = emptyList(),

) {
    val hasKey: Boolean get() = apiKey.isNotBlank()

    /**
     * 这一套配置**实际走的是不是扩展通道**（联网搜索等能力的唯一前提）。
     *
     * 判据与请求时用的是同一个 [`ApiStyle.resolve`]：配置档位 + 端点共同决定。
     * 用在设置页与输入面板上"要不要显示联网搜索" —— 只有扩展通道有
     * `deepseek_web_search` 这个工具，OpenAI 兼容通道没有对应入口，
     * 那就别把一个打开了也没用的开关摆在用户面前。
     */
    val resolvedApiStyle: ApiStyle
        get() = ApiStyle.resolve(
            kind = runCatching { ProviderKind.valueOf(providerId) }.getOrDefault(ProviderKind.DEEPSEEK),
            configuredId = apiStyleId,
            baseUrl = baseUrl,
        )

    /** 当前通道是否支持联网搜索。 */
    val supportsWebSearch: Boolean get() = resolvedApiStyle == ApiStyle.ANTHROPIC
}

/**
 * 「最近搜索」的持久化格式（换行分隔，最新的在前）。
 *
 * 与 [ModelIdList] 同一种思路：只有几条短字符串，换行在关键词里不可能出现
 * （输入框是多行的，但真正的换行会被 [SettingsStore.addRecentSearch] 之前
 * 的 `trim` 之外的步骤处理不到 —— 所以编码时把换行替换成空格）。
 */
internal object RecentSearches {

    fun encode(items: List<String>): String = items
        .map { it.replace('\n', ' ').trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

    fun decode(raw: String?): List<String> = raw
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toList()
        .orEmpty()
}

/**
 * 一条长期记忆。
 *
 * 存成 `id\ttimestamp\t正文` 的文本行（[MemoryCodec]），不建表：
 * 记忆条目通常几十条，改一次要写整份也无所谓，而建表就得升 schema、
 * 写迁移、处理老备份 —— 为了几十行字不值当。
 */
data class MemoryEntry(
    val id: String,
    val text: String,
    val createdAt: Long,
)

internal object MemoryCodec {

    fun encode(items: List<MemoryEntry>): String = items
        .filter { it.text.isNotBlank() }
        .joinToString("\n") { "${it.id}\t${it.createdAt}\t${it.text.replace('\n', ' ')}" }

    fun decode(raw: String?): List<MemoryEntry> = raw
        ?.lineSequence()
        ?.mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size < 3) return@mapNotNull null
            val text = parts[2].trim()
            if (text.isEmpty()) null else MemoryEntry(parts[0], text, parts[1].toLongOrNull() ?: 0L)
        }
        ?.toList()
        .orEmpty()
}

/** 一条 `/` 快捷指令：[name] 是打斜杠后看到的短名，[text] 是真正发出去的内容。 */
data class SlashCommand(val name: String, val text: String)

/**
 * 快捷指令的存盘格式：一条一行，`名字\t内容`。
 *
 * 与 [MemoryCodec] 同思路 —— 换行和制表符会破坏分隔，存之前统一替换成空格。
 */
internal object SlashCommandCodec {

    fun encode(commands: List<SlashCommand>): String =
        commands.joinToString("\n") { "${oneLine(it.name)}\t${oneLine(it.text)}" }

    fun decode(raw: String?): List<SlashCommand> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.lineSequence()
            .mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val tab = line.indexOf('\t')
                if (tab <= 0) return@mapNotNull null
                val name = line.substring(0, tab).trim()
                val text = line.substring(tab + 1).trim()
                if (name.isEmpty() || text.isEmpty()) null else SlashCommand(name, text)
            }
            .toList()
    }

    private fun oneLine(value: String) = value.replace('\n', ' ').replace('\t', ' ').trim()
}

/**
 * 配置存储（DataStore Preferences）。
 *
 * **API Key 不落明文**：加解密走 [SecretBox]（Android Keystore 里的
 * 不可导出 AES-256 密钥 + AES-GCM），DataStore 里只存密文。
 * 密钥随应用卸载 / 清除数据一起消失，届时解不开只会当"未设置"处理，
 * 不会崩，也不会把密文当 Key 发出去。
 */
class SettingsStore(
    private val context: Context,
    private val preferences: DataStore<Preferences> = context.settingsDataStore,
) {

    /**
     * 最近一次读到的设置。
     *
     * 给 Activity 生命周期（`onStop`）这种**没有协程、也不该等 IO** 的地方用：
     * 判断"退到后台要不要上锁"不能等一次 DataStore 读取完成。
     */
    @Volatile
    var lastKnown: StoredSettings? = null
        private set

    val flow: Flow<StoredSettings> = preferences.data.map { prefs ->
        val value = StoredSettings(
            providerId = prefs[Keys.PROVIDER] ?: StoredSettings().providerId,
            baseUrl = prefs[Keys.BASE_URL] ?: StoredSettings().baseUrl,
            modelId = prefs[Keys.MODEL] ?: StoredSettings().modelId,
            contextWindowOverride = prefs[Keys.CONTEXT_WINDOW] ?: StoredSettings().contextWindowOverride,
            apiKey = readApiKey(prefs),
            apiProfiles = profileRows(prefs),
            activeApiProfileId = prefs[Keys.ACTIVE_PROFILE] ?: "legacy",
            autoReadAloud = prefs[Keys.AUTO_READ_ALOUD] ?: false,
            showImageSkipNotice = prefs[Keys.IMAGE_SKIP_NOTICE] ?: true,
            reasoningLevelId = prefs[Keys.REASONING] ?: StoredSettings().reasoningLevelId,
            temperature = prefs[Keys.TEMPERATURE] ?: StoredSettings().temperature,
            topP = prefs[Keys.TOP_P] ?: StoredSettings().topP,
            maxTokens = prefs[Keys.MAX_TOKENS] ?: StoredSettings().maxTokens,
            themeModeId = prefs[Keys.THEME_MODE] ?: StoredSettings().themeModeId,
            accentColorId = prefs[Keys.ACCENT_COLOR] ?: StoredSettings().accentColorId,
            fontScale = prefs[Keys.FONT_SCALE] ?: StoredSettings().fontScale,
            followSystemFont = prefs[Keys.FOLLOW_SYSTEM_FONT]
                ?: StoredSettings().followSystemFont,
            autoCollapseThinking = prefs[Keys.AUTO_COLLAPSE_THINKING]
                ?: StoredSettings().autoCollapseThinking,
            loadingWords = RecentSearches.decode(prefs[Keys.LOADING_WORDS]),
            loadingWordsUseThemeColor = prefs[Keys.LOADING_WORDS_THEME]
                ?: StoredSettings().loadingWordsUseThemeColor,
            uiStyleId = prefs[Keys.UI_STYLE] ?: StoredSettings().uiStyleId,
            incognitoIds = prefs[Keys.INCOGNITO_IDS] ?: emptySet(),
            showExtrasInDeepSeek = prefs[Keys.DEEPSEEK_EXTRAS]
                ?: StoredSettings().showExtrasInDeepSeek,
            hapticSend = prefs[Keys.HAPTIC_SEND] ?: StoredSettings().hapticSend,
            hapticLongPress = prefs[Keys.HAPTIC_LONG_PRESS] ?: StoredSettings().hapticLongPress,
            hapticSwipeDelete = prefs[Keys.HAPTIC_SWIPE_DELETE]
                ?: StoredSettings().hapticSwipeDelete,
            sendOnEnter = prefs[Keys.SEND_ON_ENTER] ?: StoredSettings().sendOnEnter,
            lineHeightScale = prefs[Keys.LINE_HEIGHT] ?: StoredSettings().lineHeightScale,
            bodyMonospace = prefs[Keys.BODY_MONOSPACE] ?: StoredSettings().bodyMonospace,
            toneId = prefs[Keys.TONE] ?: StoredSettings().toneId,
            thinkingScroll = prefs[Keys.THINKING_SCROLL] ?: StoredSettings().thinkingScroll,
            stepThinking = prefs[Keys.STEP_THINKING] ?: StoredSettings().stepThinking,
            preserveReasoning = prefs[Keys.PRESERVE_REASONING]
                ?: StoredSettings().preserveReasoning,
            learningMode = prefs[Keys.LEARNING_MODE] ?: StoredSettings().learningMode,
            softCompaction = prefs[Keys.SOFT_COMPACTION] ?: StoredSettings().softCompaction,
            minimalMode = prefs[Keys.MINIMAL_MODE] ?: StoredSettings().minimalMode,
            codeWrap = prefs[Keys.CODE_WRAP] ?: StoredSettings().codeWrap,
            processAutoExpand = prefs[Keys.PROCESS_AUTO_EXPAND]
                ?: StoredSettings().processAutoExpand,
            browserModeId = prefs[Keys.BROWSER_MODE] ?: StoredSettings().browserModeId,
            searchProviderId = prefs[Keys.SEARCH_PROVIDER] ?: StoredSettings().searchProviderId,
            imageModelId = prefs[Keys.IMAGE_MODEL] ?: StoredSettings().imageModelId,
            keepAliveWakeLock = prefs[Keys.KEEP_ALIVE_WAKELOCK]
                ?: StoredSettings().keepAliveWakeLock,
            keepAliveIgnoreBattery = prefs[Keys.KEEP_ALIVE_BATTERY]
                ?: StoredSettings().keepAliveIgnoreBattery,
            keepAliveFakeAudio = prefs[Keys.KEEP_ALIVE_AUDIO]
                ?: StoredSettings().keepAliveFakeAudio,
            allowAnyHttp = prefs[Keys.ALLOW_ANY_HTTP] ?: StoredSettings().allowAnyHttp,
            generationNotification = prefs[Keys.GENERATION_NOTIFICATION]
                ?: StoredSettings().generationNotification,
            appLock = prefs[Keys.APP_LOCK] ?: StoredSettings().appLock,
            secureScreen = prefs[Keys.SECURE_SCREEN] ?: StoredSettings().secureScreen,
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: StoredSettings().onboardingDone,
            customPrimary = prefs[Keys.CUSTOM_PRIMARY],
            customUserBubble = prefs[Keys.CUSTOM_USER_BUBBLE],
            customPageBackground = prefs[Keys.CUSTOM_PAGE_BG],
            customCard = prefs[Keys.CUSTOM_CARD],
            customText = prefs[Keys.CUSTOM_TEXT],
            defaultModeId = prefs[Keys.DEFAULT_MODE] ?: StoredSettings().defaultModeId,
            languageId = prefs[LANGUAGE_KEY] ?: StoredSettings().languageId,
            apiStyleId = prefs[Keys.API_STYLE] ?: StoredSettings().apiStyleId,
            webSearch = prefs[Keys.WEB_SEARCH] ?: StoredSettings().webSearch,
            locationRegion = prefs[Keys.LOCATION] ?: StoredSettings().locationRegion,
            ttsVoiceName = prefs[Keys.TTS_VOICE] ?: StoredSettings().ttsVoiceName,
            ttsVoiceLabel = prefs[Keys.TTS_VOICE_LABEL] ?: StoredSettings().ttsVoiceLabel,
            modelIds = ModelIdList.decode(prefs[Keys.MODEL_IDS]),

        )
        lastKnown = value
        value
    }

    suspend fun setProvider(providerId: String, baseUrl: String, modelId: String) {
        editApi { prefs ->
            // 换了端点，旧端点拉到的模型列表就不再成立
            val endpointChanged = prefs[Keys.PROVIDER] != providerId ||
                prefs[Keys.BASE_URL] != baseUrl.trim()
            if (endpointChanged) prefs.remove(Keys.MODEL_IDS)
            prefs[Keys.PROVIDER] = providerId
            prefs[Keys.BASE_URL] = baseUrl.trim()
            prefs[Keys.MODEL] = modelId
        }
    }

    suspend fun setBaseUrl(baseUrl: String) {
        val trimmed = baseUrl.trim()
        editApi { prefs ->
            // 只改大小写 / 前后空格不算换端点；真的改了才丢掉旧列表
            if (prefs[Keys.BASE_URL].orEmpty().trim() != trimmed) prefs.remove(Keys.MODEL_IDS)
            prefs[Keys.BASE_URL] = trimmed
        }
    }

    suspend fun setModel(modelId: String, expectedProfileId: String? = null) {
        editApi(expectedProfileId) { it[Keys.MODEL] = modelId }
    }

    /** 记下这个端点 `GET /models` 返回的 id（测试连接 / 对话面板刷新时调用）。 */
    suspend fun setModelIds(ids: List<String>, expectedProfileId: String? = null, expectedBaseUrl: String? = null) {
        editApi(expectedProfileId) { prefs ->
            if (expectedBaseUrl != null && prefs[Keys.BASE_URL] != expectedBaseUrl) return@editApi
            val encoded = ModelIdList.encode(ids)
            if (encoded.isEmpty()) {
                prefs.remove(Keys.MODEL_IDS)
            } else {
                prefs[Keys.MODEL_IDS] = encoded
            }
        }
    }

    suspend fun setApiKey(apiKey: String) {
        // 清洗在**入口**做一次：粘贴带进来的换行/空格是 OkHttp 报
        // `Unexpected char 0x0a in Authorization value` 的头号原因
        // （见 ApiKey 的说明）。非 ASCII 不在这里静默丢弃，交给界面拦。
        val trimmed = ApiKey.normalize(apiKey)
        editApi { prefs ->
            // Different credentials at the same URL can expose a different model catalog.
            prefs.remove(Keys.MODEL_IDS)
            if (trimmed.isEmpty()) {
                prefs.remove(Keys.API_KEY_ENC)
            } else {
                prefs[Keys.API_KEY_ENC] = SecretBox.encrypt(trimmed)
                    ?: throw AppTextException(com.simplechat.app.R.string.api_profile_save_failed)
            }
            // 明文槽位彻底弃用，写入时顺手清掉历史遗留
            prefs.remove(Keys.API_KEY)
        }
    }

    /**
     * 把 v1.7 之前留下的**明文** Key 就地加密（一次性）。
     *
     * 启动时调用一次即可。老版本直接把 Key 写在 `api_key` 里，
     * 升级后那份明文必须尽快消失，否则"加密存储"只是新数据的待遇。
     */
    suspend fun migrateLegacyApiKey() {
        preferences.edit { prefs ->
            val legacy = prefs[Keys.API_KEY]
            if (!legacy.isNullOrBlank() && prefs[Keys.API_KEY_ENC] == null) {
                prefs[Keys.API_KEY_ENC] = SecretBox.encrypt(legacy)
                    ?: throw AppTextException(com.simplechat.app.R.string.api_profile_save_failed)
            }
            prefs.remove(Keys.API_KEY)
        }
    }

    val incognitoIds: Flow<Set<String>> =
        preferences.data.map { it[Keys.INCOGNITO_IDS] ?: emptySet() }

    suspend fun markIncognito(id: String) {
        preferences.edit { prefs ->
            prefs[Keys.INCOGNITO_IDS] = (prefs[Keys.INCOGNITO_IDS] ?: emptySet()) + id
        }
    }

    suspend fun unmarkIncognito(id: String) {
        preferences.edit { prefs ->
            prefs[Keys.INCOGNITO_IDS] = (prefs[Keys.INCOGNITO_IDS] ?: emptySet()) - id
        }
    }

    /** 0 = 自动（按模型表）。 */
    suspend fun setContextWindowOverride(tokens: Int) {
        preferences.edit { it[Keys.CONTEXT_WINDOW] = tokens.coerceIn(0, MAX_CONTEXT_WINDOW) }
    }

    suspend fun setReasoningLevel(levelId: String, expectedProfileId: String? = null) {
        editApi(expectedProfileId) { it[Keys.REASONING] = levelId }
    }

    suspend fun setTemperature(value: Float, expectedProfileId: String? = null) {
        editApi(expectedProfileId) { it[Keys.TEMPERATURE] = value }
    }

    suspend fun setTopP(value: Float, expectedProfileId: String? = null) {
        editApi(expectedProfileId) { it[Keys.TOP_P] = value }
    }

    suspend fun setMaxTokens(value: Int, expectedProfileId: String? = null) {
        editApi(expectedProfileId) { it[Keys.MAX_TOKENS] = value }
    }

    suspend fun setThemeMode(modeId: String) {
        preferences.edit { it[Keys.THEME_MODE] = modeId }
    }

    suspend fun setAccentColor(colorId: String) {
        preferences.edit { it[Keys.ACCENT_COLOR] = colorId }
    }

    suspend fun setFontScale(scale: Float) {
        preferences.edit { it[Keys.FONT_SCALE] = scale }
    }

    suspend fun setFollowSystemFont(follow: Boolean) {
        preferences.edit { it[Keys.FOLLOW_SYSTEM_FONT] = follow }
    }

    suspend fun setAutoCollapseThinking(enabled: Boolean) {
        preferences.edit { it[Keys.AUTO_COLLAPSE_THINKING] = enabled }
    }

    /**
     * 触感反馈：三个动作各一个开关。
     *
     * 拆成三项而不是一个总开关 —— 有人只讨厌滑删时的震动（像误操作提示），
     * 但发送时的那一下很受用。合成一个开关只会逼人全关。
     */
    suspend fun setHapticSend(enabled: Boolean) {
        preferences.edit { it[Keys.HAPTIC_SEND] = enabled }
    }

    suspend fun setHapticLongPress(enabled: Boolean) {
        preferences.edit { it[Keys.HAPTIC_LONG_PRESS] = enabled }
    }

    suspend fun setHapticSwipeDelete(enabled: Boolean) {
        preferences.edit { it[Keys.HAPTIC_SWIPE_DELETE] = enabled }
    }

    suspend fun setSendOnEnter(enabled: Boolean) {
        preferences.edit { it[Keys.SEND_ON_ENTER] = enabled }
    }

    suspend fun setGenerationNotification(enabled: Boolean) {
        preferences.edit { it[Keys.GENERATION_NOTIFICATION] = enabled }
    }

    suspend fun setAppLock(enabled: Boolean) {
        preferences.edit { it[Keys.APP_LOCK] = enabled }
    }

    suspend fun setSecureScreen(enabled: Boolean) {
        preferences.edit { it[Keys.SECURE_SCREEN] = enabled }
    }

    /** 引导只在装完第一次出现，之后不再打扰（设置里也没有入口去重开）。 */
    suspend fun setOnboardingDone(done: Boolean) {
        preferences.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    /** 行高倍率夹到 [MIN_LINE_HEIGHT]..[MAX_LINE_HEIGHT]：滑块的边界在 UI 上，这里再兜一次。 */
    suspend fun setLineHeightScale(scale: Float) {
        preferences.edit { it[Keys.LINE_HEIGHT] = scale.coerceIn(MIN_LINE_HEIGHT, MAX_LINE_HEIGHT) }
    }

    suspend fun setBodyMonospace(enabled: Boolean) {
        preferences.edit { it[Keys.BODY_MONOSPACE] = enabled }
    }

    suspend fun setTone(toneId: String) {
        preferences.edit { it[Keys.TONE] = toneId }
    }

    suspend fun setThinkingScroll(enabled: Boolean) {
        preferences.edit { it[Keys.THINKING_SCROLL] = enabled }
    }

    suspend fun setStepThinking(enabled: Boolean) {
        preferences.edit { it[Keys.STEP_THINKING] = enabled }
    }

    suspend fun setPreserveReasoning(enabled: Boolean) {
        preferences.edit { it[Keys.PRESERVE_REASONING] = enabled }
    }

    suspend fun setLearningMode(enabled: Boolean) {
        preferences.edit { it[Keys.LEARNING_MODE] = enabled }
    }

    suspend fun setSoftCompaction(enabled: Boolean) {
        preferences.edit { it[Keys.SOFT_COMPACTION] = enabled }
    }

    suspend fun setMinimalMode(enabled: Boolean) {
        preferences.edit { it[Keys.MINIMAL_MODE] = enabled }
    }

    suspend fun setCodeWrap(enabled: Boolean) {
        preferences.edit { it[Keys.CODE_WRAP] = enabled }
    }

    suspend fun setProcessAutoExpand(enabled: Boolean) {
        preferences.edit { it[Keys.PROCESS_AUTO_EXPAND] = enabled }
    }

    suspend fun setBrowserMode(modeId: String) {
        preferences.edit { it[Keys.BROWSER_MODE] = modeId }
    }

    suspend fun setSearchProvider(providerId: String) {
        preferences.edit { it[Keys.SEARCH_PROVIDER] = providerId }
    }

    suspend fun setImageModel(modelId: String) {
        preferences.edit { it[Keys.IMAGE_MODEL] = modelId.trim() }
    }

    suspend fun setKeepAliveWakeLock(enabled: Boolean) {
        preferences.edit { it[Keys.KEEP_ALIVE_WAKELOCK] = enabled }
    }

    suspend fun setKeepAliveIgnoreBattery(enabled: Boolean) {
        preferences.edit { it[Keys.KEEP_ALIVE_BATTERY] = enabled }
    }

    suspend fun setKeepAliveFakeAudio(enabled: Boolean) {
        preferences.edit { it[Keys.KEEP_ALIVE_AUDIO] = enabled }
    }

    suspend fun setAllowAnyHttp(enabled: Boolean) {
        preferences.edit { it[Keys.ALLOW_ANY_HTTP] = enabled }
    }

    /**
     * 设置 / 清除一项自定义配色。
     *
     * [color] 传 null 就是"这一项回到跟随主题"，所以一个方法覆盖设置与重置两种操作。
     */
    suspend fun setCustomColor(slot: CustomColorSlot, color: Int?) {
        preferences.edit { prefs ->
            if (color == null) prefs.remove(slot.key) else prefs[slot.key] = color
        }
    }

    /** 五项一起恢复默认。 */
    suspend fun resetCustomColors() {
        preferences.edit { prefs -> CustomColorSlot.entries.forEach { prefs.remove(it.key) } }
    }

    // ── 最近搜索 ──────────────────────────────────────────

    /**
     * 最近搜过的关键词，最新的在前。
     *
     * 存 DataStore 而不是数据库：它只有几条字符串、读写都很轻，
     * 为它建一张表要连带 schema 版本和迁移。
     */
    val recentSearches: Flow<List<String>> =
        preferences.data.map { RecentSearches.decode(it[Keys.RECENT_SEARCHES]) }

    /**
     * 记一条搜索。
     *
     * 去重时按**原样**比较（区分大小写）：用户搜 "Kotlin" 和 "kotlin"
     * 得到的结果可能不同，把它们合并成一条反而丢信息。
     */
    suspend fun addRecentSearch(keyword: String) {
        val cleaned = keyword.trim()
        if (cleaned.isEmpty()) return
        preferences.edit { prefs ->
            val current = RecentSearches.decode(prefs[Keys.RECENT_SEARCHES])
            val next = (listOf(cleaned) + current.filterNot { it == cleaned })
                .take(MAX_RECENT_SEARCHES)
            prefs[Keys.RECENT_SEARCHES] = RecentSearches.encode(next)
        }
    }

    suspend fun clearRecentSearches() {
        preferences.edit { it.remove(Keys.RECENT_SEARCHES) }
    }

    /**
     * 改等待文案。
     *
     * 用"传一个变换函数"而不是传整份列表：调用方（设置页）是在当前列表上做
     * 增 / 改 / 删，而 `StoredSettings` 是快照，拿快照算完再写回会把并发改动吞掉。
     */
    suspend fun editLoadingWords(update: (List<String>) -> List<String>) {
        preferences.edit { prefs ->
            val words = update(RecentSearches.decode(prefs[Keys.LOADING_WORDS]))
                .map { normalizeLoadingWord(it) }
                .filter { it.isNotEmpty() }
                .distinct()
            if (words.isEmpty()) prefs.remove(Keys.LOADING_WORDS)
            else prefs[Keys.LOADING_WORDS] = RecentSearches.encode(words)
        }
    }

    suspend fun setUiStyle(id: String) {
        preferences.edit { it[Keys.UI_STYLE] = id }
    }

    suspend fun setShowExtrasInDeepSeek(enabled: Boolean) {
        preferences.edit { it[Keys.DEEPSEEK_EXTRAS] = enabled }
    }

    suspend fun setLoadingWordsUseThemeColor(enabled: Boolean) {
        preferences.edit { it[Keys.LOADING_WORDS_THEME] = enabled }
    }

    /** 长期记忆：最新的在前。 */
    val memories: Flow<List<MemoryEntry>> =
        preferences.data.map { MemoryCodec.decode(it[Keys.MEMORIES]) }

    suspend fun addMemory(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        val entry = MemoryEntry(
            id = java.util.UUID.randomUUID().toString(),
            text = cleaned,
            createdAt = System.currentTimeMillis(),
        )
        preferences.edit { prefs ->
            val current = MemoryCodec.decode(prefs[Keys.MEMORIES])
            prefs[Keys.MEMORIES] = MemoryCodec.encode(listOf(entry) + current)
        }
    }

    suspend fun updateMemory(id: String, text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        preferences.edit { prefs ->
            val current = MemoryCodec.decode(prefs[Keys.MEMORIES])
            prefs[Keys.MEMORIES] = MemoryCodec.encode(
                current.map { if (it.id == id) it.copy(text = cleaned) else it },
            )
        }
    }

    suspend fun deleteMemory(id: String) {
        preferences.edit { prefs ->
            val current = MemoryCodec.decode(prefs[Keys.MEMORIES])
            prefs[Keys.MEMORIES] = MemoryCodec.encode(current.filterNot { it.id == id })
        }
    }

    suspend fun clearMemories() {
        preferences.edit { it.remove(Keys.MEMORIES) }
    }

    /** `/` 快捷指令，顺序即列表顺序。 */
    val slashCommands: Flow<List<SlashCommand>> =
        preferences.data.map { SlashCommandCodec.decode(it[Keys.SLASH_COMMANDS]) }

    suspend fun addSlashCommand(name: String, text: String) {
        val cleanName = name.trim().removePrefix("/")
        val cleanText = text.trim()
        if (cleanName.isEmpty() || cleanText.isEmpty()) return
        preferences.edit { prefs ->
            val current = SlashCommandCodec.decode(prefs[Keys.SLASH_COMMANDS])
            // 同名覆盖：改一条指令时不该多出一条重名的
            val next = current.filterNot { it.name.equals(cleanName, ignoreCase = true) } +
                SlashCommand(cleanName, cleanText)
            prefs[Keys.SLASH_COMMANDS] = SlashCommandCodec.encode(next)
        }
    }

    suspend fun deleteSlashCommand(name: String) {
        preferences.edit { prefs ->
            val current = SlashCommandCodec.decode(prefs[Keys.SLASH_COMMANDS])
            prefs[Keys.SLASH_COMMANDS] =
                SlashCommandCodec.encode(current.filterNot { it.name == name })
        }
    }

    /** 收藏的消息 id。 */
    val favoriteMessages: Flow<Set<String>> =
        preferences.data.map { it[Keys.FAVORITE_MESSAGES] ?: emptySet() }

    /** 收藏的会话 id。 */
    val favoriteConversations: Flow<Set<String>> =
        preferences.data.map { it[Keys.FAVORITE_CONVERSATIONS] ?: emptySet() }

    /**
     * 切换一条消息的收藏。
     *
     * 收藏存 id 而不是复制正文：正文会随着「重新生成」「编辑」变化，
     * 复制一份就会出现两份对不上的历史。
     */
    suspend fun toggleFavoriteMessage(id: String) {
        preferences.edit { prefs ->
            val current = prefs[Keys.FAVORITE_MESSAGES] ?: emptySet()
            prefs[Keys.FAVORITE_MESSAGES] =
                if (id in current) current - id else current + id
        }
    }

    suspend fun toggleFavoriteConversation(id: String) {
        preferences.edit { prefs ->
            val current = prefs[Keys.FAVORITE_CONVERSATIONS] ?: emptySet()
            prefs[Keys.FAVORITE_CONVERSATIONS] =
                if (id in current) current - id else current + id
        }
    }

    suspend fun clearFavorites() {
        preferences.edit {
            it.remove(Keys.FAVORITE_MESSAGES)
            it.remove(Keys.FAVORITE_CONVERSATIONS)
        }
    }

    suspend fun setDefaultMode(modeId: String) {
        preferences.edit { it[Keys.DEFAULT_MODE] = modeId }
    }

    suspend fun setLanguage(languageId: String) {
        preferences.edit { it[LANGUAGE_KEY] = languageId }
    }

    suspend fun setApiStyle(styleId: String) {
        editApi { it[Keys.API_STYLE] = styleId }
    }

    suspend fun setWebSearch(enabled: Boolean) {
        editApi { it[Keys.WEB_SEARCH] = enabled }
    }

    suspend fun setLocationRegion(region: String) {
        // 存**洗过**的值：逗号会把时间地点提醒的五个字段挤成六段（见 normalizeRegion）
        preferences.edit { it[Keys.LOCATION] = normalizeRegion(region) }
    }

    /** 朗读音色：名字用来精确选中，标签用来在设置页显示（见 [StoredSettings.ttsVoiceName]）。 */
    suspend fun setTtsVoice(name: String, label: String) {
        preferences.edit { prefs ->
            if (name.isBlank()) {
                prefs.remove(Keys.TTS_VOICE)
                prefs.remove(Keys.TTS_VOICE_LABEL)
            } else {
                prefs[Keys.TTS_VOICE] = name
                prefs[Keys.TTS_VOICE_LABEL] = label
            }
        }
    }



    /**
     * 把当前所有接口配置（含**明文** Key）导出成 JSON。
     *
     * 明文是有意的：密文绑在本机 Keystore 上，换机 / 卸载重装之后解不开，
     * 那样导出的文件对"换台机器接着用"这个唯一用途毫无价值。
     */
    suspend fun exportApiConfig(): String {
        val prefs = preferences.data.first()
        val rows = prefs[Keys.PROFILES]?.let(::decodeApiProfiles) ?: profileRows(prefs)
        return apiConfigJson.encodeToString(
            ApiConfigFile(
                exportedAt = System.currentTimeMillis(),
                activeProfileId = prefs[Keys.ACTIVE_PROFILE],
                profiles = rows.map { profile ->
                    ApiConfigProfile(
                        id = profile.id,
                        name = profile.name,
                        providerId = profile.providerId,
                        baseUrl = profile.baseUrl,
                        modelId = profile.modelId,
                        apiKey = runCatching { SecretBox.decrypt(profile.apiKeyEncrypted) }.getOrDefault(""),
                        apiStyleId = profile.apiStyleId,
                        modelIds = profile.modelIds,
                        reasoningLevelId = profile.reasoningLevelId,
                        temperature = profile.temperature,
                        topP = profile.topP,
                        maxTokens = profile.maxTokens,
                        webSearch = profile.webSearch,
                    )
                },
            ),
        )
    }

    /**
     * 导入接口配置：整表替换，并把「当时正在用的那套」切成当前。
     *
     * 整表替换而不是合并：同名不同 key 的情况没法猜谁对，混在一起只会更难查。
     * 返回导入的套数。
     */
    suspend fun importApiConfig(text: String): Int {
        val file = runCatching { apiConfigJson.decodeFromString<ApiConfigFile>(text) }
            .getOrElse { throw AppTextException(com.simplechat.app.R.string.api_config_invalid) }
        if (file.format != ApiConfigFile.FORMAT) {
            throw AppTextException(com.simplechat.app.R.string.api_config_invalid)
        }
        if (file.profiles.isEmpty()) return 0
        preferences.edit { prefs ->
            val rows = file.profiles.map { profile ->
                ApiProfile(
                    id = profile.id.ifBlank { java.util.UUID.randomUUID().toString() },
                    name = profile.name,
                    providerId = profile.providerId,
                    baseUrl = profile.baseUrl,
                    modelId = profile.modelId,
                    apiKeyEncrypted = if (profile.apiKey.isBlank()) ""
                    else SecretBox.encrypt(profile.apiKey).orEmpty(),
                    apiStyleId = profile.apiStyleId,
                    modelIds = profile.modelIds,
                    reasoningLevelId = profile.reasoningLevelId,
                    temperature = profile.temperature,
                    topP = profile.topP,
                    maxTokens = profile.maxTokens,
                    webSearch = profile.webSearch,
                    lastUsedAt = System.currentTimeMillis(),
                )
            }
            prefs[Keys.PROFILES] = apiProfileJson.encodeToString(rows)
            val active = rows.firstOrNull { it.id == file.activeProfileId } ?: rows.first()
            activate(prefs, active)
        }
        return file.profiles.size
    }

    /** Seed the registry in one edit, preserving the existing active configuration and encrypted key. */
    suspend fun migrateApiProfiles() { preferences.edit { ensureProfiles(it) } }

    suspend fun setShowImageSkipNotice(enabled: Boolean) {
        preferences.edit { it[Keys.IMAGE_SKIP_NOTICE] = enabled }
    }

    suspend fun setAutoReadAloud(enabled: Boolean) {
        preferences.edit { it[Keys.AUTO_READ_ALOUD] = enabled }
    }

    private fun profileRows(prefs: Preferences): List<ApiProfile> =
        prefs[Keys.PROFILES]?.let(::decodeApiProfiles)
            ?: listOf(snapshotProfile(prefs, "legacy", com.simplechat.app.net.ProviderKind.entries
                .firstOrNull { it.name == prefs[Keys.PROVIDER] }?.displayName
                ?: com.simplechat.app.net.ProviderKind.DEEPSEEK.displayName))

    private fun snapshotProfile(prefs: Preferences, id: String, name: String, lastUsedAt: Long = 0): ApiProfile = ApiProfile(
        id = id, name = name,
        providerId = prefs[Keys.PROVIDER] ?: "DEEPSEEK",
        baseUrl = prefs[Keys.BASE_URL] ?: "https://api.deepseek.com",
        modelId = prefs[Keys.MODEL] ?: "deepseek-flash",
        apiKeyEncrypted = prefs[Keys.API_KEY_ENC].orEmpty(),
        apiStyleId = prefs[Keys.API_STYLE] ?: "AUTO",
        modelIds = ModelIdList.decode(prefs[Keys.MODEL_IDS]),
        reasoningLevelId = prefs[Keys.REASONING] ?: "OFF",
        temperature = prefs[Keys.TEMPERATURE] ?: 1f, topP = prefs[Keys.TOP_P] ?: 1f,
        maxTokens = prefs[Keys.MAX_TOKENS] ?: 0, webSearch = prefs[Keys.WEB_SEARCH] ?: false,
        lastUsedAt = lastUsedAt,
    )

    private fun ensureProfiles(prefs: MutablePreferences): List<ApiProfile> {
        prefs[Keys.PROFILES]?.let { return decodeApiProfiles(it) }
        val legacy = prefs[Keys.API_KEY]
        if (!legacy.isNullOrBlank() && prefs[Keys.API_KEY_ENC] == null) {
            prefs[Keys.API_KEY_ENC] = SecretBox.encrypt(legacy)
                ?: throw AppTextException(com.simplechat.app.R.string.api_profile_save_failed)
        }
        prefs.remove(Keys.API_KEY)
        val profiles = profileRows(prefs)
        prefs[Keys.PROFILES] = apiProfileJson.encodeToString(profiles)
        prefs[Keys.ACTIVE_PROFILE] = profiles.first().id
        return profiles
    }

    private suspend fun editApi(expectedProfileId: String? = null, block: (MutablePreferences) -> Unit) {
        preferences.edit { prefs ->
            val profiles = ensureProfiles(prefs)
            val activeId = checkNotNull(prefs[Keys.ACTIVE_PROFILE])
            if (expectedProfileId != null && expectedProfileId != activeId) return@edit
            block(prefs)
            prefs[Keys.PROFILES] = apiProfileJson.encodeToString(profiles.map {
                if (it.id == activeId) snapshotProfile(prefs, it.id, it.name, it.lastUsedAt) else it
            })
        }
    }

    private fun activate(prefs: MutablePreferences, profile: ApiProfile) {
        prefs[Keys.ACTIVE_PROFILE] = profile.id
        prefs[Keys.PROVIDER] = profile.providerId
        prefs[Keys.BASE_URL] = profile.baseUrl
        prefs[Keys.MODEL] = profile.modelId
        if (profile.apiKeyEncrypted.isEmpty()) prefs.remove(Keys.API_KEY_ENC)
        else prefs[Keys.API_KEY_ENC] = profile.apiKeyEncrypted
        prefs.remove(Keys.API_KEY)
        prefs[Keys.API_STYLE] = profile.apiStyleId
        prefs[Keys.MODEL_IDS] = ModelIdList.encode(profile.modelIds)
        prefs[Keys.REASONING] = profile.reasoningLevelId
        prefs[Keys.TEMPERATURE] = profile.temperature
        prefs[Keys.TOP_P] = profile.topP
        prefs[Keys.MAX_TOKENS] = profile.maxTokens
        prefs[Keys.WEB_SEARCH] = profile.webSearch
    }

    suspend fun selectApiProfile(id: String) {
        preferences.edit { prefs ->
            val profiles = ensureProfiles(prefs)
            val selected = profiles.firstOrNull { it.id == id } ?: return@edit
            val current = selected.copy(lastUsedAt = System.currentTimeMillis())
            activate(prefs, current)
            prefs[Keys.PROFILES] = apiProfileJson.encodeToString(profiles.map { if (it.id == id) current else it })
        }
    }

    /** Provider picker recalls the last used profile for that provider, including its own key. */
    suspend fun selectProvider(provider: ProviderKind) {
        preferences.edit { prefs ->
            val profiles = ensureProfiles(prefs)
            if (prefs[Keys.PROVIDER] == provider.name) return@edit
            val selected = profiles.filter { it.providerId == provider.name }.maxByOrNull { it.lastUsedAt }
                ?: ApiProfile(java.util.UUID.randomUUID().toString(), provider.displayName,
                    provider.name, provider.defaultBaseUrl, provider.defaultModel)
            val current = selected.copy(lastUsedAt = System.currentTimeMillis())
            activate(prefs, current)
            prefs[Keys.PROFILES] = apiProfileJson.encodeToString(profiles.filterNot { it.id == current.id } + current)
        }
    }

    suspend fun createApiProfile(name: String, copyCurrent: Boolean = false) {
        val title = name.trim().take(80)
        require(title.isNotEmpty())
        preferences.edit { prefs ->
            val profiles = ensureProfiles(prefs)
            val id = java.util.UUID.randomUUID().toString()
            val profile = if (copyCurrent) snapshotProfile(prefs, id, title, System.currentTimeMillis())
                else ApiProfile(id, title, "CUSTOM", "", "", lastUsedAt = System.currentTimeMillis())
            prefs[Keys.PROFILES] = apiProfileJson.encodeToString(profiles + profile)
            activate(prefs, profile)
        }
    }

    suspend fun renameApiProfile(id: String, name: String) {
        val title = name.trim().take(80)
        require(title.isNotEmpty())
        preferences.edit { prefs ->
            prefs[Keys.PROFILES] = apiProfileJson.encodeToString(ensureProfiles(prefs).map {
                if (it.id == id) it.copy(name = title) else it
            })
        }
    }

    suspend fun deleteApiProfile(id: String) {
        preferences.edit { prefs ->
            val profiles = ensureProfiles(prefs)
            require(profiles.size > 1)
            val remaining = profiles.filterNot { it.id == id }
            if (prefs[Keys.ACTIVE_PROFILE] == id) activate(prefs, remaining.maxBy { it.lastUsedAt })
            prefs[Keys.PROFILES] = apiProfileJson.encodeToString(remaining)
        }
    }

    /** 清空全部配置（「删除所有数据」时一并调用）。 */
    suspend fun clear() {
        preferences.edit { it.clear() }
    }

    /** 读 API Key：优先密文，其次兼容老版本的明文。 */
    private fun readApiKey(prefs: Preferences): String {
        prefs[Keys.API_KEY_ENC]?.takeIf { it.isNotBlank() }?.let { return SecretBox.decrypt(it) }
        return prefs[Keys.API_KEY].orEmpty()
    }

    /**
     * 可自定义的颜色槽位。
     *
     * 只有五项：再多就是"配色编辑器"了 —— 那五处之外的颜色要么是从主色派生
     * （选中态、气泡底），要么是中性灰（描边、分隔线），单独改它们收益很低。
     */
    enum class CustomColorSlot(@StringRes val labelRes: Int, internal val key: Preferences.Key<Int>) {
        PRIMARY(R.string.settings_custom_primary, Keys.CUSTOM_PRIMARY),
        USER_BUBBLE(R.string.settings_custom_bubble, Keys.CUSTOM_USER_BUBBLE),
        PAGE_BACKGROUND(R.string.settings_custom_page_bg, Keys.CUSTOM_PAGE_BG),
        CARD(R.string.settings_custom_card, Keys.CUSTOM_CARD),
        TEXT(R.string.settings_custom_text, Keys.CUSTOM_TEXT),
        ;
    }

    companion object {
        /** 最近搜索：最多留几条。再多也翻不到，而清空按钮旁边一屏塞不下。 */
        const val MAX_RECENT_SEARCHES = 8

        /** 上下文窗口上限：2M，够覆盖现在所有长上下文模型，又不至于填错一个 0 就把内存算爆。 */
        const val MAX_CONTEXT_WINDOW = 2_000_000

        /** 行高倍率的下限 / 上限。 */
        const val MIN_LINE_HEIGHT = 0.9f
        const val MAX_LINE_HEIGHT = 1.6f
    }

    private object Keys {
        val PROFILES = stringPreferencesKey("api_profiles_v1")
        val ACTIVE_PROFILE = stringPreferencesKey("active_api_profile")
        val IMAGE_SKIP_NOTICE = booleanPreferencesKey("show_image_skip_notice")
        val AUTO_READ_ALOUD = booleanPreferencesKey("auto_read_aloud")
        val PROVIDER = stringPreferencesKey("provider_id")
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model_id")
        val CONTEXT_WINDOW = intPreferencesKey("context_window_override")

        /** API Key 的**密文**（base64）。 */
        val API_KEY_ENC = stringPreferencesKey("api_key_enc")

        /** 历史遗留的明文槽位，只读一次用于迁移，之后不再写入。 */
        val API_KEY = stringPreferencesKey("api_key")
        val REASONING = stringPreferencesKey("reasoning_level")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_P = floatPreferencesKey("top_p")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val FOLLOW_SYSTEM_FONT = booleanPreferencesKey("follow_system_font")
        val AUTO_COLLAPSE_THINKING = booleanPreferencesKey("auto_collapse_thinking")
        val HAPTIC_SEND = booleanPreferencesKey("haptic_send")
        val HAPTIC_LONG_PRESS = booleanPreferencesKey("haptic_long_press")
        val HAPTIC_SWIPE_DELETE = booleanPreferencesKey("haptic_swipe_delete")
        val SEND_ON_ENTER = booleanPreferencesKey("send_on_enter")
        val GENERATION_NOTIFICATION = booleanPreferencesKey("generation_notification")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val LINE_HEIGHT = floatPreferencesKey("line_height_scale")
        val BODY_MONOSPACE = booleanPreferencesKey("body_monospace")
        val CUSTOM_PRIMARY = intPreferencesKey("custom_primary")
        val CUSTOM_USER_BUBBLE = intPreferencesKey("custom_user_bubble")
        val CUSTOM_PAGE_BG = intPreferencesKey("custom_page_bg")
        val CUSTOM_CARD = intPreferencesKey("custom_card")
        val CUSTOM_TEXT = intPreferencesKey("custom_text")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val LOADING_WORDS = stringPreferencesKey("loading_words")
        val LOADING_WORDS_THEME = booleanPreferencesKey("loading_words_theme_color")
        val UI_STYLE = stringPreferencesKey("ui_style")
        val INCOGNITO_IDS = stringSetPreferencesKey("incognito_ids")
        val DEEPSEEK_EXTRAS = booleanPreferencesKey("deepseek_extras")
        val TONE = stringPreferencesKey("ai_tone")
        val THINKING_SCROLL = booleanPreferencesKey("thinking_scroll")
        val STEP_THINKING = booleanPreferencesKey("step_thinking")
        val PRESERVE_REASONING = booleanPreferencesKey("preserve_reasoning")
        val LEARNING_MODE = booleanPreferencesKey("learning_mode")
        val SOFT_COMPACTION = booleanPreferencesKey("soft_compaction")
        val MINIMAL_MODE = booleanPreferencesKey("minimal_mode")
        val CODE_WRAP = booleanPreferencesKey("code_wrap")
        val PROCESS_AUTO_EXPAND = booleanPreferencesKey("process_auto_expand")
        val BROWSER_MODE = stringPreferencesKey("browser_mode")
        val SEARCH_PROVIDER = stringPreferencesKey("search_provider")
        val IMAGE_MODEL = stringPreferencesKey("image_model")
        val KEEP_ALIVE_WAKELOCK = booleanPreferencesKey("keep_alive_wakelock")
        val KEEP_ALIVE_BATTERY = booleanPreferencesKey("keep_alive_battery")
        val KEEP_ALIVE_AUDIO = booleanPreferencesKey("keep_alive_audio")
        val ALLOW_ANY_HTTP = booleanPreferencesKey("allow_any_http")
        val MEMORIES = stringPreferencesKey("memories")
        val SLASH_COMMANDS = stringPreferencesKey("slash_commands")
        val FAVORITE_MESSAGES = stringSetPreferencesKey("favorite_messages")
        val FAVORITE_CONVERSATIONS = stringSetPreferencesKey("favorite_conversations")
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val API_STYLE = stringPreferencesKey("api_style")
        val WEB_SEARCH = booleanPreferencesKey("web_search")
        val LOCATION = stringPreferencesKey("location_region")

        /** 朗读音色：机器标识；空 = 系统默认。 */
        val TTS_VOICE = stringPreferencesKey("tts_voice_name")

        /** 朗读音色的人类可读名字（仅用于显示）。 */
        val TTS_VOICE_LABEL = stringPreferencesKey("tts_voice_label")

        /** 当前端点 `/models` 拉回来的 id，换行分隔（[ModelIdList]）。 */
        val MODEL_IDS = stringPreferencesKey("model_ids")

    }
}
