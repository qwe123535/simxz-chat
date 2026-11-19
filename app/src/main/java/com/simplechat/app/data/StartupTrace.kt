package com.simplechat.app.data

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * 冷启动打点。
 *
 * 只做一件事：把几个关键时刻**相对进程启动**的毫秒数记下来，一边打 logcat、
 * 一边落到 `Android/data/<包名>/files/startup-trace.txt`。用途只有一个 ——
 * 优化前后能对比，而不是凭"感觉快了"。
 *
 * 刻意保持得极简：
 * - 不引入任何依赖、不碰协程、不做异步聚合（每次 mark 直接 append 一行，几十字节）；
 * - 全部在**主线程**写（这些点本来就都在主线程），避免为打点再加锁；
 * - 出问题可以直接把这个对象删掉，除了几个 `StartupTrace.mark(...)` 调用点，
 *   没有任何地方依赖它。
 *
 * 时间基准用 [Process.getStartElapsedRealtime]，它比 `SystemClock.elapsedRealtime()`
 * 更接近"用户点图标那一刻"——后者是**本对象被加载**的时刻，已经在 Application
 * 起来之后了，会白白少算前面那一段。
 */
object StartupTrace {

    private const val TAG = "SimpleChat/Startup"

    /** 文件名：放在应用自己的外部私有目录，不需要任何存储权限。 */
    private const val FILE_NAME = "startup-trace.txt"

    /** 一次启动最多记这么多行，防止异常路径把文件写爆。 */
    private const val MAX_LINES = 64

    private val origin = Process.getStartElapsedRealtime()

    private val lines = ArrayList<String>(16)

    /** 已经记过的标签。同一个点只记第一次 —— 消息流每来一条都会重跑那个 effect。 */
    private val seen = HashSet<String>(8)

    /** 记一个时刻。[label] 用点分命名，读起来就是一条时间线。 */
    fun mark(context: Context, label: String) {
        if (lines.size >= MAX_LINES) return
        if (!seen.add(label)) return
        val at = SystemClock.elapsedRealtime() - origin
        val line = "$at ms  $label"
        lines += line
        Log.i(TAG, line)
        runCatching { write(context) }
    }

    /** 读回最近一次记录（诊断页 / 排查用）。 */
    fun read(context: Context): String = runCatching {
        file(context).takeIf { it.exists() }?.readText().orEmpty()
    }.getOrDefault("")

    private fun file(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)

    private fun write(context: Context) {
        val target = file(context)
        target.parentFile?.mkdirs()
        // 第一次写时覆盖上一轮的内容，之后追加
        target.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}
