package com.simplechat.app.data

import android.content.Context
import com.simplechat.app.service.CompletionNotifier
import com.simplechat.app.service.GenerationService

/**
 * 「后台继续生成」的接线。
 *
 * 这是**默认行为，不是一个选项**：用户在等长回复时回个消息、锁个屏，
 * 回来发现回复停在一半，只会被理解成"这软件又坏了"。
 *
 * 放在数据层做成一个**单例**，是为了让 `ChatViewModel` 不必持有 Context：
 * 它只需要在流开始 / 结束时说一句"现在在生成"或"生成完了"，
 * 至于要不要起服务、起哪种服务，都由这里决定。
 */
object BackgroundGeneration {

    private var appContext: Context? = null

    /** 是否已经起过服务（避免每次 token 都调一次 startService）。 */
    private var running = false

    /**
     * 用户是否要「生成时保持前台服务」（设置 → 后台保护）。
     *
     * 关掉不等于进程立刻会被杀，只是不再挂通知、不再主动保活。
     * 由 `ChatViewModel.applyStored` 写入，所以改完设置下一次生成就生效。
     */
    @Volatile
    var enabled: Boolean = true

    /** 设置 → 后台保护：生成时持有 PARTIAL_WAKE_LOCK（息屏也不停 CPU）。 */
    @Volatile
    var keepAwake: Boolean = false

    /** 设置 → 后台保护：生成时播放静音，让部分系统把 App 当"活跃播放器"。 */
    @Volatile
    var silentAudio: Boolean = false

    /** 在 `App.onCreate` 里调用一次。 */
    fun install(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * 同步当前状态。[generating] = 是否正在生成回复。
     *
     * 幂等：状态没变就什么都不做，所以可以放心地在流的每次状态变化时调用。
     */
    /**
     * 生成完成的通知（应用在后台时才发）。
     *
     * 通知带一个输入框和「继续生成」两个动作，用户不用回 App 就能接上话。
     * 应用在前台时**什么都不发**：用户正看着屏幕，通知只是噪音。
     */
    fun notifyDone(conversationId: String, title: String, preview: String) {
        val context = appContext ?: return
        if (AppForeground.visible) return
        CompletionNotifier.show(context, conversationId, title, preview)
    }

    fun sync(generating: Boolean) {
        val context = appContext ?: return
        if (!enabled) {
            // 关掉之后如果服务还开着，立刻收掉，别留一条撤不掉的常驻通知
            if (running) {
                running = false
                GenerationService.stop(context)
            }
            KeepAlive.stopAll()
            return
        }
        // 保活手段按设置随时同步：改了开关下一次生成就生效
        KeepAlive.sync(context, wakeLock = generating && keepAwake, silentAudio = generating && silentAudio)
        if (generating == running) return
        running = generating
        if (generating) GenerationService.start(context) else GenerationService.stop(context)
    }
}
