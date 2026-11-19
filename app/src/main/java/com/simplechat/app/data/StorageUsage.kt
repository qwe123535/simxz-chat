package com.simplechat.app.data

import android.content.Context
import com.simplechat.app.db.AppDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 存储占用统计与清理。
 *
 * 只做两件事：**算清楚**（数据库 / 附件 / 残留 / 缓存各占多少）和**清掉可清的**。
 *
 * ### 清理范围是刻意收窄的
 *
 * 只删两类东西：
 * 1. `filesDir/attachments/` 里**没有任何消息引用**的文件（删会话、换版本、导入失败
 *    留下的残渣 —— 它们永远不会再被显示，但一直占着地方）；
 * 2. `cacheDir` 下的临时文件（导入暂存、截图、缩略图）。
 *
 * **不动数据库、不动仍在引用的附件。** 后者是用户对话里的图片，
 * 删了会在聊天记录里变成"原件缺失"—— 那不是清理，那是数据丢失。
 * 想要更彻底的空间回收，得走「导出备份 → 清空全部数据」，那条路有确认弹窗。
 */
object StorageUsage {

    /** 数据库文件名，与 `AppDatabase.build` 的默认值一致。 */
    private const val DATABASE_NAME = "simplechat.db"

    /** 一次测量的结果。全部是字节数。 */
    data class Report(
        val databaseBytes: Long,
        /** 附件目录里**仍被引用**的部分 —— 只显示，不清理。 */
        val attachmentBytes: Long,
        /** 附件目录里没人引用的残留。 */
        val orphanBytes: Long,
        val orphanCount: Int,
        /** 临时缓存目录。 */
        val cacheBytes: Long,
    ) {
        /** 这次清理能释放的字节数。 */
        val cleanableBytes: Long get() = orphanBytes + cacheBytes
    }

    suspend fun measure(context: Context, db: AppDatabase): Report = withContext(Dispatchers.IO) {
        val referenced = referencedNames(context, db)
        val dir = attachmentDir(context)

        var kept = 0L
        var orphanBytes = 0L
        var orphanCount = 0
        dir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            if (file.name in referenced) {
                kept += file.length()
            } else {
                orphanBytes += file.length()
                orphanCount++
            }
        }

        Report(
            databaseBytes = databaseBytes(context),
            attachmentBytes = kept,
            orphanBytes = orphanBytes,
            orphanCount = orphanCount,
            cacheBytes = directoryBytes(context.cacheDir),
        )
    }

    /**
     * 执行清理，返回实际释放的字节数。
     *
     * 重新算一遍引用集而不是复用界面上那份快照：用户可能在这之间又发了消息。
     */
    suspend fun clear(context: Context, db: AppDatabase): Long = withContext(Dispatchers.IO) {
        val referenced = referencedNames(context, db)
        var freed = 0L

        attachmentDir(context).listFiles()?.forEach { file ->
            if (file.isFile && file.name !in referenced) {
                freed += file.length()
                if (!file.delete()) freed -= file.length()
            }
        }

        context.cacheDir.listFiles()?.forEach { file ->
            freed += directoryBytes(file)
            if (deleteTree(file)) {
                // 删掉了才算数（已加过大小）
            } else {
                freed -= directoryBytes(file)
            }
        }

        freed.coerceAtLeast(0L)
    }

    /** 被任何一条消息引用的落盘文件名。 */
    private suspend fun referencedNames(context: Context, db: AppDatabase): Set<String> =
        db.messageDao().allAttachmentBlobs()
            .flatMap { Attachments.decode(it) }
            .mapNotNull { it.path?.takeIf { path -> path.isNotBlank() } }
            .toSet()

    private fun attachmentDir(context: Context): File = File(context.filesDir, "attachments")

    /** 数据库本体 + `-wal` / `-shm` 边车文件。 */
    private fun databaseBytes(context: Context): Long {
        val path = context.getDatabasePath(DATABASE_NAME)
        val base = path.parentFile ?: return 0L
        return listOf(path, File(base, "$DATABASE_NAME-wal"), File(base, "$DATABASE_NAME-shm"))
            .sumOf { it.takeIf { file -> file.isFile }?.length() ?: 0L }
    }

    private fun directoryBytes(root: File): Long {
        if (!root.exists()) return 0L
        if (root.isFile) return root.length()
        return root.listFiles()?.sumOf { directoryBytes(it) } ?: 0L
    }

    /**
     * 人类可读的大小。
     *
     * 与 [Attachments.formatSize] 分开：那个吃 `Int`（单张图），这里要处理 GB 级的总量，
     * 而且 0 必须说"0 B"而不是"0.0 MB"。
     */
    fun formatBytes(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format("%.0f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
        else -> String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
    }

    private fun deleteTree(root: File): Boolean {
        if (!root.exists()) return true
        if (root.isFile) return root.delete()
        root.listFiles()?.forEach { deleteTree(it) }
        return root.delete()
    }
}
