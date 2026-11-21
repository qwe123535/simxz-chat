package com.simplechat.app.data

import android.content.Context
import com.simplechat.app.db.ConversationEntity
import com.simplechat.app.db.MessageEntity
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 把一个会话导出成 **DeepSeek 官方导出格式**的 zip。
 *
 * ### 为什么要对齐官方格式
 *
 * 我们自己有 JSON 备份（[Backup]）和 Markdown / 纯文本导出，但那三种别人读不了。
 * 官方格式是唯一一种「导出来还能被别家工具认」的形状 —— 官方前端开源
 * （`deepseek-ai/chat-demo`），`src/services/chat/history/` 里逐字段写着格式，
 * 我们自己的导入器（[com.simplechat.app.data.import.DeepSeekHistory]）也是按它解析的。
 * 所以这里反过来按同一份定义写出去：导出的包能被本 App 原样导回，
 * 也能被认这个格式的其它工具读。
 *
 * ### 归档结构（v3）
 *
 * ```text
 * manifest.json                                  { "export_format_version": 3 }
 * chat_session_index.json                        [{ id, title, pinned, message_count, inserted_at, updated_at }]
 * chat_sessions/{id}.json                        会话本体（含消息树）
 * chat_sessions/{id}_files/{n}_{name}            图片原件
 * ```
 *
 * ### 三个取舍
 *
 * - **只导当前分支**：分支树是官方格式里也能表达的（`parent_id` + `current_message_id`），
 *   但用户点「导出这个对话」时想看的是眼前这一条对话，不是所有废弃的分支。
 * - **id 换成从 1 开始的整数**：官方用整数 id，我们用 UUID。整数是按顺序重新编的，
 *   `parent_id` 跟着重映射，链是完整的。
 * - **图片按原件写进 `_files/`**：官方导出就是这样放的，`export_path` 指向归档内的路径。
 */
internal object DeepSeekExport {

    /** 认得的是第 3 版，与导入器同一个常量。 */
    private const val FORMAT_VERSION = 3

    fun write(
        context: Context,
        out: OutputStream,
        conversation: ConversationEntity,
        messages: List<MessageEntity>,
    ) {
        val path = activePath(messages)
        val sessionId = conversation.id

        ZipOutputStream(out.buffered()).use { zip ->
            zip.putText(
                "manifest.json",
                buildJsonObject {
                    put("export_format_version", FORMAT_VERSION)
                    put("exported_at", iso(System.currentTimeMillis()))
                    put("exporter", "simxz-chat")
                }.toString(),
            )

            zip.putText(
                "chat_session_index.json",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", sessionId)
                            put("title", conversation.title)
                            put("pinned", conversation.pinned)
                            put("message_count", path.size)
                            put("inserted_at", iso(conversation.createdAt))
                            put("updated_at", iso(conversation.updatedAt))
                        },
                    )
                }.toString(),
            )

            // 图片先写，写的同时把 export_path 记下来给消息片段用
            val imagePaths = HashMap<String, String>()
            path.forEachIndexed { index, message ->
                val number = index + 1
                Attachments.decode(message.attachments).forEachIndexed { slot, attachment ->
                    if (!attachment.isImage) return@forEachIndexed
                    val name = "chat_sessions/${sessionId}_files/${number}_$slot" +
                        attachment.name.takeIf { it.isNotBlank() }?.let { "_$it" }.orEmpty()
                    val bytes = runCatching {
                        AttachmentFiles.open(context, attachment).use { it.readBytes() }
                    }.getOrNull() ?: return@forEachIndexed
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                    imagePaths[message.id + "#" + slot] = name
                }
            }

            zip.putText("chat_sessions/$sessionId.json", sessionJson(conversation, path, imagePaths).toString())
        }
    }

    private fun sessionJson(
        conversation: ConversationEntity,
        path: List<MessageEntity>,
        imagePaths: Map<String, String>,
    ): JsonObject {
        val numberById = path.withIndex().associate { (index, message) -> message.id to index + 1 }
        return buildJsonObject {
            put("id", conversation.id)
            put("title", conversation.title)
            put("pinned", conversation.pinned)
            put("inserted_at", iso(conversation.createdAt))
            put("updated_at", iso(conversation.updatedAt))
            put("current_message_id", path.size)
            putJsonArray("messages") {
                path.forEachIndexed { index, message ->
                    add(messageJson(message, index + 1, numberById[message.parentId], imagePaths))
                }
            }
        }
    }

    private fun messageJson(
        message: MessageEntity,
        number: Int,
        parentNumber: Int?,
        imagePaths: Map<String, String>,
    ): JsonObject = buildJsonObject {
        put("id", number)
        if (parentNumber != null) put("parent_id", parentNumber)
        put("role", if (message.role == "user") "user" else "assistant")
        put("status", statusOf(message.status))
        put("inserted_at", iso(message.createdAt))
        putJsonArray("fragments") {
            if (message.role != "user" && !message.reasoning.isNullOrBlank()) {
                add(
                    buildJsonObject {
                        put("type", "THINK")
                        put("content", message.reasoning)
                        put("elapsed_secs", 0)
                    },
                )
            }
            add(
                buildJsonObject {
                    put("type", if (message.role == "user") "REQUEST" else "RESPONSE")
                    put("content", message.content)
                },
            )
            val files = Attachments.decode(message.attachments).mapIndexedNotNull { slot, attachment ->
                val exportPath = imagePaths[message.id + "#" + slot] ?: return@mapIndexedNotNull null
                buildJsonObject {
                    put("is_image", true)
                    put("file_name", attachment.name)
                    put("export_path", exportPath)
                }
            }
            if (files.isNotEmpty()) {
                add(
                    buildJsonObject {
                        put("type", "FILE")
                        putJsonArray("files") { files.forEach { add(it) } }
                    },
                )
            }
        }
    }

    /**
     * 当前分支：从根开始，每一步挑「同父兄弟里 active 的那条」。
     *
     * 与 [MessageTree] 的口径一致 —— 导出的是用户眼前这条对话，不是整棵树。
     */
    private fun activePath(messages: List<MessageEntity>): List<MessageEntity> {
        val byParent = messages.groupBy { it.parentId }
        val path = ArrayList<MessageEntity>(messages.size)
        var current = byParent[MessageEntity.ROOT_PARENT]?.firstOrNull { it.active }
            ?: byParent[MessageEntity.ROOT_PARENT]?.firstOrNull()
        while (current != null && path.size <= messages.size) {
            path += current
            val children = byParent[current.id] ?: break
            current = children.firstOrNull { it.active } ?: children.firstOrNull()
        }
        return path
    }

    /** 我们自己的状态 → 官方的状态词。 */
    private fun statusOf(status: String): String = when (status) {
        "ERROR" -> "FAILED"
        "STOPPED" -> "INCOMPLETE"
        else -> "FINISHED"
    }

    private fun iso(millis: Long): String = Instant.ofEpochMilli(millis).toString()

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
