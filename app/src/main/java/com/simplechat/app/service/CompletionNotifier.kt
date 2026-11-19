package com.simplechat.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.simplechat.app.MainActivity
import com.simplechat.app.R

/**
 * 「回复写完了」的通知。
 *
 * 只有应用不在前台时才发（判断在 `BackgroundGeneration.notifyDone`）。
 *
 * 通知上挂两个动作：
 * - **回复**：一个输入框，写完直接发出去 —— 用户不用先回到 App 再打字；
 * - **继续生成**：回复被截断时点一下接着写。
 *
 * 两个动作最终都会把 App 拉到前台（[ReplyReceiver] 发广播 → 启动 Activity），
 * 因为**流的执行者活在 ChatViewModel 里**，进程不在就没有人能跑这一轮请求。
 * 能做到的是"少打一次字、少点两下"，而不是"完全不用打开 App"。
 */
object CompletionNotifier {

    const val CHANNEL_ID = "completion"
    const val NOTIFICATION_ID = 0x5C02
    const val KEY_REPLY_TEXT = "reply_text"
    const val EXTRA_CONVERSATION_ID = "conversation_id"
    const val EXTRA_MESSAGE_ID = "message_id"

    fun show(
        context: Context,
        conversationId: String,
        messageId: String?,
        title: String,
        preview: String,
    ) {
        runCatching {
            createChannel(context)

            val open = PendingIntent.getActivity(
                context,
                conversationId.hashCode(),
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_CONVERSATION_ID, conversationId)
                    .putExtra(EXTRA_MESSAGE_ID, messageId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val replyIntent = Intent(context, ReplyReceiver::class.java)
                .setAction(ReplyReceiver.ACTION_REPLY)
                .putExtra(EXTRA_CONVERSATION_ID, conversationId)
            // RemoteInput 需要可变的 PendingIntent（系统要往里塞输入内容）
            val replyPending = PendingIntent.getBroadcast(
                context,
                conversationId.hashCode(),
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            val replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_reply),
                replyPending,
            ).addRemoteInput(
                RemoteInput.Builder(KEY_REPLY_TEXT)
                    .setLabel(context.getString(R.string.notification_reply_hint))
                    .build(),
            ).build()

            val continuePending = PendingIntent.getBroadcast(
                context,
                conversationId.hashCode() + 1,
                Intent(context, ReplyReceiver::class.java)
                    .setAction(ReplyReceiver.ACTION_CONTINUE)
                    .putExtra(EXTRA_CONVERSATION_ID, conversationId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title.ifBlank { context.getString(R.string.app_name) })
                .setContentText(preview)
                .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(replyAction)
                .addAction(
                    R.drawable.ic_notification,
                    context.getString(R.string.notification_action_continue),
                    continuePending,
                )
                .build()

            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_done),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { setShowBadge(true) }
        manager.createNotificationChannel(channel)
    }
}
