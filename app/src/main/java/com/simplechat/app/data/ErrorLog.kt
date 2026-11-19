package com.simplechat.app.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 错误日志：一个环形文本文件，给「设置 → 错误日志」页读。
 *
 * 为什么是文件而不是数据库：崩溃发生在**任何**线程上，包括数据库还没打开、
 * 甚至 Room 自身出错的时候。写文件是最不容易连带失败的一条路。
 *
 * 为什么要有上限：日志只在排查时有用，留 300 行足够定位一次事故，
 * 无限增长会把存储页里的"临时文件"变成第二号问题。
 */
object ErrorLog {

    private const val TAG = "SimpleChat/ErrorLog"
    private const val FILE_NAME = "errors.log"
    private const val MAX_LINES = 300

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    private fun fileOf(context: Context): File = File(context.filesDir, FILE_NAME)

    /** 记一条。任何异常都吞掉 —— 记日志本身不该把 App 弄崩。 */
    fun record(context: Context, tag: String, message: String) {
        runCatching {
            val line = "${timeFormat.format(Date())}  [$tag]  ${message.replace('\n', ' ')}"
            Log.w(TAG, line)
            val file = fileOf(context)
            val existing = if (file.exists()) file.readLines() else emptyList()
            val kept = (existing + line).takeLast(MAX_LINES)
            file.writeText(kept.joinToString("\n"))
        }
    }

    /** 记一条异常，带异常类型与首帧位置 —— 堆栈全文太长，日志页放不下。 */
    fun record(context: Context, tag: String, error: Throwable) {
        val where = error.stackTrace.firstOrNull()?.let { "${it.className}.${it.methodName}:${it.lineNumber}" }
        val detail = buildString {
            append(error::class.java.simpleName)
            error.message?.let { append(": ").append(it) }
            where?.let { append(" @ ").append(it) }
        }
        record(context, tag, detail)
    }

    fun read(context: Context): String = runCatching {
        val file = fileOf(context)
        if (file.exists()) file.readText() else ""
    }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { fileOf(context).delete() }
    }
}
