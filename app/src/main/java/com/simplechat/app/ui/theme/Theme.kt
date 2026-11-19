package com.simplechat.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.simplechat.app.R
import com.simplechat.app.data.Res

/** 主题模式，对应设置页的「外观」三项。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 主题色。设置页「个性化 → 主题色」。
 *
 * 只影响**品牌色那一族**：主色（按钮 / 链接 / 选中态 / 发送键 / 进度环）、
 * 选中态的浅底、以及用户气泡。页面底色、正文颜色、功能色（危险 / 警告）都不动 ——
 * 换主题色不该把"有错误"这类语义色也换掉。
 *
 * 取值存的是 `name`；认不出来就回落到 [BLUE]（见 `MainActivity`）。
 */
enum class AccentColor(@StringRes private val labelRes: Int) {
    BLUE(R.string.theme_color_blue),
    GREEN(R.string.theme_color_green),
    RED(R.string.theme_color_red),
    PINK(R.string.theme_color_pink),
    PURPLE(R.string.theme_color_purple),
    BROWN(R.string.theme_color_brown),
    GRAY(R.string.theme_color_gray),
    ;

    /** 颜色名，随界面语言取（`theme_color_*`）。 */
    val label: String get() = Res.get(labelRes)
}

/** 该主题色在指定明暗下的**主色**。 */
fun AccentColor.primary(dark: Boolean): Color = when (this) {
    AccentColor.BLUE -> if (dark) BrandBlueDark else BrandBlue
    AccentColor.GREEN -> if (dark) AccentGreenDark else AccentGreenLight
    AccentColor.RED -> if (dark) AccentRedDark else AccentRedLight
    AccentColor.PINK -> if (dark) AccentPinkDark else AccentPinkLight
    AccentColor.PURPLE -> if (dark) AccentPurpleDark else AccentPurpleLight
    AccentColor.BROWN -> if (dark) AccentBrownDark else AccentBrownLight
    AccentColor.GRAY -> if (dark) AccentGrayDark else AccentGrayLight
}

/**
 * 由主色**派生**出来的浅色底：选中态填充 + 用户气泡。
 *
 * 刻意不逐个手写色值 —— 7 个主题色 × 2 档明暗 = 28 个手调颜色，
 * 既难保证它们跟主色"是同一个色相"，改一个还要跟着改一片。
 * 这里按固定比例把主色掺进页面底色，整套自动跟着主色走。
 *
 * 比例（浅 0.16 / 深 0.22）是照原来那版蓝色反推的：算出来与
 * `#E8EDFF` / `#243056` 基本重合，所以**默认的蓝色观感不变**。
 */
private fun tintOf(primary: Color, dark: Boolean): Color =
    lerp(if (dark) PageBgDark else Color.White, primary, if (dark) 0.22f else 0.16f)

/**
 * Material3 ColorScheme 之外的扩展语义色。
 * 这些是 DeepSeek 观感的关键，Material 没有对应槽位。
 */
@Immutable
data class ChatColors(
    val userBubble: Color,
    val onUserBubble: Color,
    val processText: Color,
    val processBar: Color,
    val placeholder: Color,
    val pageBackground: Color,
    val settingsBackground: Color,
    val card: Color,
    val outline: Color,
    val divider: Color,
    val danger: Color,
    /** 上下文接近阈值这类「注意」型状态，不是错误。 */
    val warning: Color,
    val capsuleSelected: Color,
    val fieldBackground: Color,
    /** 长按菜单等浮层表面。 */
    val menuBackground: Color,
    /**
     * Markdown 专用配色（对齐官方 Web 端的 token，见 [MarkdownCodeBgLight]）。
     *
     * 与 [fieldBackground] 分开是刻意的：输入框底色偏深一档才压得住，
     * 而代码块/行内代码要更浅 —— 直接复用会让整段代码"发灰"。
     */
    val markdownCodeBlock: Color,
    val markdownInlineCode: Color,
    val markdownQuoteBar: Color,
    val markdownRule: Color,
    /**
     * 抽屉 / 弹层背后的遮罩。
     *
     * 放在这里而不是用 Material 的 `colorScheme.scrim`：那个值在两套主题下
     * 都是同一个黑，**深色模式下盖在近黑底上等于没有**。这里按主题分别取值。
     */
    val scrim: Color,
)

private val LightChatColors = ChatColors(
    userBubble = UserBubbleLight,
    onUserBubble = TextPrimaryLight,
    processText = ProcessTextLight,
    processBar = ProcessBarLight,
    placeholder = PlaceholderLight,
    pageBackground = PageBgLight,
    settingsBackground = SettingsBgLight,
    card = CardLight,
    outline = OutlineLight,
    divider = DividerLight,
    danger = DangerRed,
    warning = WarningAmber,
    capsuleSelected = CapsuleSelectedLight,
    fieldBackground = FieldBgLight,
    menuBackground = MenuBgLight,
    markdownCodeBlock = MarkdownCodeBgLight,
    markdownInlineCode = MarkdownInlineCodeBgLight,
    markdownQuoteBar = MarkdownQuoteBarLight,
    markdownRule = MarkdownRuleLight,
    scrim = Color.Black.copy(alpha = 0.45f),
)

private val DarkChatColors = ChatColors(
    userBubble = UserBubbleDark,
    onUserBubble = TextPrimaryDark,
    processText = ProcessTextDark,
    processBar = ProcessBarDark,
    placeholder = PlaceholderDark,
    pageBackground = PageBgDark,
    settingsBackground = SettingsBgDark,
    card = CardDark,
    outline = OutlineDark,
    divider = DividerDark,
    danger = DangerRedDark,
    warning = WarningAmberDark,
    capsuleSelected = CapsuleSelectedDark,
    fieldBackground = FieldBgDark,
    menuBackground = MenuBgDark,
    markdownCodeBlock = MarkdownCodeBgDark,
    markdownInlineCode = MarkdownInlineCodeBgDark,
    markdownQuoteBar = MarkdownQuoteBarDark,
    markdownRule = MarkdownRuleDark,
    scrim = Color.Black.copy(alpha = 0.62f),
)

val LocalChatColors = staticCompositionLocalOf { LightChatColors }

/**
 * DeepSeek 皮肤的品牌色与表面色。
 *
 * 取自官方 App 的视觉：主色是偏冷的蓝（`rgb(57,100,254)`），用户气泡是很淡的蓝，
 * 助手消息**不加气泡**、直接铺在页面底色上 —— 这两条是那套界面的骨架，
 * 少了它们就只是"换了个蓝"。
 */
private val DeepSeekBlue = Color(0xFF3964FE)
private val DeepSeekBubbleLight = Color(0xFFEDF3FE)
private val DeepSeekBubbleDark = Color(0xFF2A3556)
private val DeepSeekPageLight = Color(0xFFFFFFFF)
private val DeepSeekPageDark = Color(0xFF191919)
private val DeepSeekCardLight = Color(0xFFF7F7F8)
private val DeepSeekCardDark = Color(0xFF232323)
private val DeepSeekFieldLight = Color(0xFFF2F3F5)
private val DeepSeekFieldDark = Color(0xFF2A2A2A)

/**
 * DeepSeek 皮肤下的 [ChatColors]。
 *
 * 刻意**不叠加**用户的强调色与自定义配色：那是一套跟原版配套的调色，
 * 混进来只会得到"半蓝半绿"的四不像。要自定义就切回原版。
 */
private fun deepSeekChatColors(dark: Boolean): ChatColors {
    val base = if (dark) DarkChatColors else LightChatColors
    return base.copy(
        userBubble = if (dark) DeepSeekBubbleDark else DeepSeekBubbleLight,
        onUserBubble = if (dark) Color(0xFFE8E8EA) else Color(0xFF1A1A1A),
        capsuleSelected = if (dark) Color(0xFF2C3550) else DeepSeekBubbleLight,
        pageBackground = if (dark) DeepSeekPageDark else DeepSeekPageLight,
        card = if (dark) DeepSeekCardDark else DeepSeekCardLight,
        fieldBackground = if (dark) DeepSeekFieldDark else DeepSeekFieldLight,
    )
}

/** DeepSeek 皮肤下的 Material 配色。只换主色那一族，功能色（危险/警告）照旧。 */
private fun deepSeekScheme(dark: Boolean): ColorScheme {
    val tint = if (dark) DeepSeekBlue.copy(alpha = 0.24f) else Color(0xFFEDF3FE)
    val page = if (dark) DeepSeekPageDark else DeepSeekPageLight
    val card = if (dark) DeepSeekCardDark else DeepSeekCardLight
    val onSurface = if (dark) TextPrimaryDark else TextPrimaryLight
    return if (dark) {
        darkColorScheme(
            primary = DeepSeekBlue,
            onPrimary = Color.White,
            primaryContainer = tint,
            onPrimaryContainer = DeepSeekBlue,
            background = page,
            onBackground = onSurface,
            surface = card,
            onSurface = onSurface,
            surfaceVariant = DeepSeekFieldDark,
            onSurfaceVariant = TextSecondaryDark,
            outline = OutlineDark,
            outlineVariant = DividerDark,
            error = DangerRedDark,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = DeepSeekBlue,
            onPrimary = Color.White,
            primaryContainer = tint,
            onPrimaryContainer = DeepSeekBlue,
            background = page,
            onBackground = onSurface,
            surface = card,
            onSurface = onSurface,
            surfaceVariant = DeepSeekFieldLight,
            onSurfaceVariant = TextSecondaryLight,
            outline = OutlineLight,
            outlineVariant = DividerLight,
            error = DangerRed,
            onError = Color.White,
        )
    }
}

/**
 * 把主题色套到基础色板上。
 *
 * 只换两处"品牌色表面"：**选中态填充**与**用户气泡**。
 * 它们原来是写死的淡蓝（`CapsuleSelectedLight` / `UserBubbleLight`），
 * 换成绿色主题后若还留着蓝，一眼就是"没换干净"。
 */
private fun chatColors(
    dark: Boolean,
    accent: AccentColor,
    custom: CustomColors,
): ChatColors {
    val tint = tintOf(custom.primary ?: accent.primary(dark), dark)
    val base = if (dark) DarkChatColors else LightChatColors
    return base.copy(
        userBubble = custom.userBubble ?: tint,
        capsuleSelected = tint,
        pageBackground = custom.pageBackground ?: base.pageBackground,
        card = custom.card ?: base.card,
    )
}

/**
 * 用户自定义的那几项颜色（设置 → 自定义配色）。
 *
 * 每一项都是 `null` 表示"跟随主题" —— 用可空而不是另建一套默认色板：
 * 默认值本来就在 [LightChatColors] / [DarkChatColors] 与 [AccentColor] 里，
 * 抄一份到这里，改一处忘一处就又出现"两套默认色不同步"。
 *
 * **不区分明暗**：用户选了什么就是什么。自动按明暗改色会把用户刚挑的颜色
 * 悄悄改掉 —— 那就不叫自定义了。
 */
@Immutable
data class CustomColors(
    val primary: Color? = null,
    val userBubble: Color? = null,
    val pageBackground: Color? = null,
    val card: Color? = null,
    val text: Color? = null,
) {
    val isEmpty: Boolean
        get() = primary == null && userBubble == null && pageBackground == null &&
            card == null && text == null

    companion object {
        val NONE = CustomColors()
    }
}

/**
 * 当前是不是深色。
 *
 * 存在的理由只有一个：**系统栏（状态栏图标 / 导航栏「小白条」）要跟 App 的主题走，
 * 而不是跟系统的**。设置页可以强制浅色或深色，系统的深浅完全可能是另一个值 ——
 * 不看着这个值，就会出现「深色 App 顶着一排看不见的状态栏图标 / 底下一条小白条」。
 *
 * 见 [ApplySystemBarAppearance]。
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * 品牌色色板。
 *
 * **刻意不做动态取色（Monet / 莫奈取色）。**
 *
 * 动态取色会把背景、表面、描边全部染上壁纸的颜色 —— 阅读类应用里
 * 正文底色的色偏直接影响长时间阅读的舒适度，而"跟随壁纸"并不带来
 * 任何功能价值，只是让界面看起来像别人的应用。
 *
 * 这里要的是**稳定的中性底 + 一个**（由用户在设置里选定的）**品牌色**：
 * 正文区永远是干净的近白 / 近黑，品牌色永远可辨认 —— 换的只是**色相**，
 * 而不是"跟着壁纸跑"。见 [AccentColor]。
 */
private fun staticScheme(
    dark: Boolean,
    accent: AccentColor,
    custom: CustomColors,
): ColorScheme {
    val primary = custom.primary ?: accent.primary(dark)
    val tint = tintOf(primary, dark)
    val pageBackground = custom.pageBackground ?: if (dark) PageBgDark else PageBgLight
    val card = custom.card ?: if (dark) CardDark else CardLight
    val onSurface = custom.text ?: if (dark) TextPrimaryDark else TextPrimaryLight
    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = tint,
            onPrimaryContainer = primary,
            background = pageBackground,
            onBackground = onSurface,
            surface = card,
            onSurface = onSurface,
            surfaceVariant = FieldBgDark,
            onSurfaceVariant = TextSecondaryDark,
            outline = OutlineDark,
            outlineVariant = DividerDark,
            error = DangerRedDark,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = tint,
            onPrimaryContainer = primary,
            background = pageBackground,
            onBackground = onSurface,
            surface = card,
            onSurface = onSurface,
            surfaceVariant = FieldBgLight,
            onSurfaceVariant = TextSecondaryLight,
            outline = OutlineLight,
            outlineVariant = DividerLight,
            error = DangerRed,
            onError = Color.White,
        )
    }
}

/**
 * @param accent 主题色（设置页「个性化 → 主题色」）。只影响品牌色那一族，
 *   页面底色与功能色不动 —— 见 [AccentColor]。
 * @param fontScale 全局字号倍率（设置页「字体大小」）。
 *
 * 用 `LocalDensity.fontScale` 实现，而不是逐个改 `TextStyle`：
 * `sp` 换算受影响、`dp` 布局不受影响 —— 正是"只调字号"的语义，
 * 且对 `MarkdownBodyStyle`、`CodeTextStyle` 这些散落的样式一并生效，
 * 不会漏改。
 */
@Composable
fun SimpleChatTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentColor = AccentColor.BLUE,
    fontScale: Float = 1f,
    custom: CustomColors = CustomColors.NONE,
    lineHeightScale: Float = 1f,
    bodyMonospace: Boolean = false,
    uiStyle: UiStyle = UiStyle.ORIGINAL,
    showExtras: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val deepSeek = uiStyle == UiStyle.DEEPSEEK
    val scheme = if (deepSeek) deepSeekScheme(dark) else staticScheme(dark, accent, custom)

    val overrides = TextOverrides(lineHeightScale, bodyMonospace)
    val typography = remember(overrides) { SimpleChatTypography.withReadingOverrides(overrides) }

    val baseDensity = LocalDensity.current

    CompositionLocalProvider(
        LocalChatColors provides if (deepSeek) deepSeekChatColors(dark) else chatColors(dark, accent, custom),
        LocalUiStyle provides uiStyle,
        LocalShowExtras provides showExtras,
        LocalIsDarkTheme provides dark,
        LocalTextOverrides provides overrides,
        LocalDensity provides Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * fontScale,
        ),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            content = content,
        )
    }
}
