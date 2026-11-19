package com.simplechat.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.simplechat.app.MainActivity
import com.simplechat.app.data.PendingChatAction

/**
 * 接通知栏上的两个动作。
 *
 * 收到之后做两件事：
 * 1. 把动作放进 [PendingChatAction]（ViewModel 起来后会取走执行）；
 * 2. 把 App 拉到前台 —— 流式请求必须跑在 `ChatViewModel` 的协程里，
 *    进程不在前台时没人能发这一轮请求。
 *
 * 广播是显式 intent（指定了类名），所以 manifest 里 `exported=false` 就够，
 * 外部应用调不到它。
 */
class ReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra(CompletionNotifier.EXTRA_CONVERSATION_ID)
        if (conversationId.isNullOrBlank()) return

        when (intent.action) {
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(CompletionNotifier.KEY_REPLY_TEXT)
                    ?.toString()
                    ?.trim()
                // 空文本不拉起 App：用户可能只是误触了输入框
                if (text.isNullOrEmpty()) return
                PendingChatAction.post(
                    PendingChatAction.Action(
                        conversationId = conversationId,
                        text = text,
                        continueGeneration = false,
                    ),
                )
            }

            ACTION_CONTINUE -> PendingChatAction.post(
                PendingChatAction.Action(
                    conversationId = conversationId,
                    text = null,
                    continueGeneration = true,
                ),
            )

            else -> return
        }

        NotificationManagerCompat.from(context).cancel(CompletionNotifier.NOTIFICATION_ID)

        // 从后台广播里拉起 Activity：通知动作属于用户操作，不受后台启动限制
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    ),
            )
        }
    }

    companion object {
        const val ACTION_REPLY = "com.simplechat.app.action.REPLY"
        const val ACTION_CONTINUE = "com.simplechat.app.action.CONTINUE"
    }
}
