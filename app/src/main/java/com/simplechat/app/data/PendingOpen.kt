package com.simplechat.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 通知点进来时"要打开哪个会话的哪条消息"。
 *
 * 为什么不用 `Intent` 一路传下去：点通知时 App 可能已经在后台活着，
 * 系统走的是 `onNewIntent`，而 `RootScreen` 早就组合完了 —— 一个只读一次的
 * Intent extra 根本传不进去。这里放一个单槽邮箱，UI 层自己来取。
 *
 * 只存一条：连点两条通知时，最后点的那个才是用户想看的。
 */
object PendingOpen {

    private val _target = MutableStateFlow<String?>(null)

    /** 待打开的会话 id；null 表示没有。 */
    val target: StateFlow<String?> = _target

    /** 待滚动到的消息 id（生成完成的那条）。 */
    @Volatile
    var messageId: String? = null
        private set

    fun post(conversationId: String, messageId: String?) {
        this.messageId = messageId
        _target.value = conversationId
    }

    /** 取走并清空 —— 同一条通知不该被打开两次。 */
    fun consume(): Pair<String, String?>? {
        val id = _target.value ?: return null
        _target.value = null
        val message = messageId
        messageId = null
        return id to message
    }
}
