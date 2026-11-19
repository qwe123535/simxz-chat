package com.simplechat.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 触感反馈的唯一入口。
 *
 * 为什么包一层而不是各处直接 `LocalHapticFeedback.current.performHapticFeedback(...)`：
 * ① 类型只用一种（[HapticFeedbackType.LongPress]）—— 换来换去手感就不一致了；
 * ② 将来想换强度 / 换实现（比如改用 `Vibrator` 精确控制毫秒），只改这一处。
 *
 * 用哪个 [HapticFeedbackType] 是刻意的：`LongPress` 落在系统 `LONG_PRESS` 常量上，
 * 几乎所有 ROM 都有对应实现（部分国产 ROM 把 `TEXT_HANDLE_MOVE` 之类映射成"无"）。
 * 更"轻"的类型在这里反而会**完全静音**，那不是我们想要的"轻"，是"没有"。
 *
 * 开关在调用点判断（见 `StoredSettings.hapticSend` 等三个布尔），这里不持有任何设置。
 */
@Stable
class Haptics(private val performer: HapticFeedback) {

    /** 一下干脆的轻震。 */
    fun tick() {
        performer.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val performer = LocalHapticFeedback.current
    return remember(performer) { Haptics(performer) }
}
