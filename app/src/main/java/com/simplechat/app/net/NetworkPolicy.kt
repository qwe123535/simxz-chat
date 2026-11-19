package com.simplechat.app.net

import com.simplechat.app.R
import com.simplechat.app.data.Res
import okhttp3.Interceptor

/**
 * 明文 HTTP 的放行策略（设置 → 安全 → 允许明文 HTTP）。
 *
 * 为什么需要它：Manifest 里 `usesCleartextTraffic="true"` 是**全局**开口，
 * 一旦打开，任何 http 地址都不会再被系统拦。本地模型必须走 http，
 * 但一个公网 http 端点意味着 API Key 在链路上裸奔 —— 所以在这里补一道
 * 只针对"非本机、非局域网"的收口。
 *
 * 默认放行：老用户可能已经在用 http 端点，升级不该让他们的配置突然失效。
 */
object NetworkPolicy {

    /** 由 `ChatViewModel.applyStored` 写入，改完设置下一次请求就生效。 */
    @Volatile
    var allowAnyHttp: Boolean = true

    val interceptor: Interceptor = Interceptor { chain ->
        val url = chain.request().url
        if (url.scheme == "http" && !allowAnyHttp && !url.host.isPrivateHost()) {
            // 文案在抛出点拼好，userMessage() 会原样透出
            throw NetworkTextException(Res.get(R.string.error_http_blocked))
        }
        chain.proceed(chain.request())
    }
}
