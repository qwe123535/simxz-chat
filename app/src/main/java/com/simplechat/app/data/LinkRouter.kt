package com.simplechat.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 链接的去处。
 *
 * 项目里点链接的地方不止一处（正文里的 Markdown 链接、来源列表、时间线里的网页步骤），
 * 以前各写各的 `LocalUriHandler`，于是"用不用内置浏览器"这件事散落在三个文件里。
 * 现在统一走这里：谁想打开链接就 [open]，由顶层决定交给内置浏览器还是系统。
 *
 * 只存一个槽位：同时点两个链接，后点的覆盖先点的 —— 这是期望行为，
 * 用户点第二下时已经不想看第一个了。
 */
object LinkRouter {
    private val _url = MutableStateFlow<String?>(null)

    val url: StateFlow<String?> = _url

    fun open(url: String) {
        if (url.isNotBlank()) _url.value = url
    }

    fun close() {
        _url.value = null
    }

    /** 只有 http(s) 走内置浏览器；`mailto:`、`tel:` 这类仍交给系统。 */
    fun isWebUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
}
