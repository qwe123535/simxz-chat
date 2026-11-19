package com.simplechat.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 界面风格。
 *
 * 两套皮肤共用同一套数据与逻辑，只换**看得见的那一层**：气泡形状、配色、
 * 消息排布、列表密度。切换只影响渲染，不动任何存储与请求。
 */
enum class UiStyle(val id: String) {
    /** 原版：气泡 + 方格设置页。 */
    ORIGINAL("ORIGINAL"),

    /** DeepSeek 风格：圆角大、用户气泡淡蓝、助手消息不加气泡、设置页走列表。 */
    DEEPSEEK("DEEPSEEK"),
    ;

    companion object {
        fun fromId(id: String?): UiStyle = entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}

/** 当前界面风格。默认原版，由 [SimpleChatTheme] 提供。 */
val LocalUiStyle = staticCompositionLocalOf { UiStyle.ORIGINAL }

/**
 * DeepSeek 风格下要不要显示"高级功能"。
 *
 * 用户选的是"极简"：思考块、每句用量、操作栏、`/` 指令这些在 DeepSeek 皮肤里
 * 默认藏起来 —— 它们不是那套界面的语言。想要就在设置里打开，而不是干脆没有。
 */
val LocalShowExtras = staticCompositionLocalOf { true }
