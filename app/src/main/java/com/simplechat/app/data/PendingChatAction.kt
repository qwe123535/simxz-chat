package com.simplechat.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 从通知栏带进来的一次操作。
 *
 * 通知上的「回复」和「继续生成」都会先把 App 拉到前台，再把这个动作放进这里 ——
 * 因为真正发请求的是 `ChatViewModel` 里的协程，广播接收器跑完就结束了，
 * 它没法自己发起一轮流式请求。
 *
 * 用 `StateFlow` 而不是 `SharedFlow`：接收器可能在 ViewModel 还没创建时就把动作放下了
 * （冷启动场景），状态留着，ViewModel 起来后还能读到。
 */
object PendingChatAction {

    data class Action(
        val conversationId: String,
        /** 要发出去的文本；[continueGeneration] 为 true 时为 null。 */
        val text: String?,
        /** 续写最后一条回复，而不是发新消息。 */
        val continueGeneration: Boolean,
    )

    private val _pending = MutableStateFlow<Action?>(null)

    val pending: StateFlow<Action?> = _pending.asStateFlow()

    fun post(action: Action) {
        _pending.value = action
    }

    /** 取走并清空。取走之后同一个动作不会再被执行第二次。 */
    fun consume(): Action? {
        val action = _pending.value
        _pending.value = null
        return action
    }
}
