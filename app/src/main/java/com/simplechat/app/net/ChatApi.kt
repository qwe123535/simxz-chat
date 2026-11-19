package com.simplechat.app.net

import com.simplechat.app.BuildConfig
import com.simplechat.app.net.anthropic.AnthropicApi
import com.simplechat.app.net.anthropic.AnthropicRequest
import com.simplechat.app.net.anthropic.anthropicMessagesUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 对话接口门面。
 *
 * 负责请求构造与**参数条件化**（设计文档 §5.4）—— 这是协议层的硬约束，
 * 不做的话用户会以为参数生效了，其实被服务端静默忽略。
 */
class ChatApi(
    private val client: OkHttpClient,
    private val sseClient: SseClient,
    private val anthropicApi: AnthropicApi,
    private val json: Json,
) {

    /**
     * 走 Anthropic 兼容通道发起流式对话（扩展模式：搜索 / 读链接 / 引用 / 服务端标题）。
     *
     * 与 [streamChat] 并列而不是合并：两条通道的请求体、事件模型、结束信号都不同，
     * 合并只会让两套判断缠在一起（见 `AnthropicApi` 的注释）。
     */
    fun streamMessage(
        baseUrl: String,
        apiKey: String,
        request: AnthropicRequest,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Flow<StreamEvent> = anthropicApi.streamMessage(
        url = anthropicMessagesUrl(baseUrl),
        apiKey = apiKey,
        request = request,
        extraHeaders = extraHeaders,
    )

    /**
     * 发起流式对话。
     *
     * 参数条件化规则：
     * - 模型不支持思考 → 完全不带 `thinking` / `reasoning_effort` 字段
     * - 思考开启 → **省略 `temperature`**（思考模式下它会被静默忽略）
     * - 思考关闭 → **省略 `top_p`**（非思考模式下它恒为 1.0）
     */
    fun streamChat(
        baseUrl: String,
        apiKey: String,
        model: ModelInfo,
        messages: List<OutMessageDto>,
        thinkingEnabled: Boolean,
        reasoningEffort: String? = null,
        maxTokens: Int? = null,
        temperature: Double? = null,
        topP: Double? = null,
        tools: kotlinx.serialization.json.JsonElement? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Flow<StreamEvent> {
        val thinkingActive = model.supportsThinking && thinkingEnabled

        val request = ChatRequestDto(
            model = model.id,
            messages = messages,
            stream = true,
            thinking = when {
                !model.supportsThinking -> null
                thinkingEnabled -> ThinkingDto.Enabled
                else -> ThinkingDto.Disabled
            },
            reasoningEffort = if (thinkingActive) reasoningEffort else null,
            maxTokens = maxTokens,
            temperature = if (thinkingActive) null else temperature,
            topP = if (thinkingActive) topP else null,
            // 工具只在没有思考的普通轮次里给：思考 + 工具同时开时，
            // 部分网关会把 tool_calls 塞进 reasoning 里，解析出来是空的
            tools = tools,
            toolChoice = if (tools != null) "auto" else null,
        )

        return sseClient.stream(
            url = chatCompletionsUrl(baseUrl),
            apiKey = apiKey,
            request = request,
            extraHeaders = extraHeaders,
        )
    }

    /**
     * 拉取模型列表。
     *
     * OpenCode Go 的 `/models` 无需鉴权（设计文档 §5.2），本地服务（Ollama / LM Studio）多数也不校验。
     * 失败不抛异常，由调用方决定怎么显示 —— 但**文案在这里就定好**：
     * 状态码 + 服务端原文交给 [httpErrorText]，本地地址再补"少了 /v1""服务没启动"
     * 这类具体指向（见 [modelListFailureText]）。
     *
     * 早先这里是 `error("HTTP ${response.code}")`，用户看到的就是一句 `HTTP 404` ——
     * 既没说清是谁的问题，也没说下一步改哪里。
     */
    suspend fun fetchModels(baseUrl: String, apiKey: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            // 地址本身写错（少协议、多了空格）也要给人话，而不是 IllegalArgumentException 原文
            val url = runCatching { modelsUrl(baseUrl).toHttpUrl() }.getOrElse {
                return@withContext Result.failure(it)
            }
            runCatching {
                val builder = Request.Builder().url(url).get()
                if (apiKey.isNotBlank()) {
                    builder.header("Authorization", "Bearer $apiKey")
                }
                client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = runCatching { response.body?.string() }.getOrNull()
                        throw NetworkTextException(
                            message = modelListFailureText(
                                base = httpErrorText(response.code, errorMessageFromBody(body)),
                                code = response.code,
                                host = url.host,
                            ),
                            code = response.code,
                        )
                    }
                    val text = response.body?.string().orEmpty()
                    json.decodeFromString(ModelListDto.serializer(), text)
                        .data
                        .map { it.id }
                }
            }.recoverCatching { error ->
                /*
                 * 连接层失败（连不上 / 超时 / DNS）：本地端点补一句"服务起了吗"。
                 *
                 * 只在本地地址上补：公网端点连不上时，多半是网络或代理的问题，
                 * 那句话反而会误导。
                 */
                if (error is IOException && error !is NetworkTextException) {
                    val hint = modelListFailureText(base = "", code = null, host = url.host)
                    if (hint.isNotEmpty()) throw NetworkTextException(error.networkText() + hint)
                }
                throw error
            }
        }

    /**
     * 从错误响应体里取出服务端自己写的那句。
     *
     * 兼容三种常见形状：`{"error":{"message":"…"}}`（OpenAI 系）、`{"message":"…"}`、
     * `{"error":"…"}`；都认不出就退回原文（截断），至少把服务端说的话留给用户。
     */
    private fun errorMessageFromBody(body: String?): String? {
        val raw = body?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val obj = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
        if (obj == null) return raw.take(MAX_ERROR_BODY_CHARS)
        val candidates = listOfNotNull(
            (obj["error"] as? JsonObject)?.get("message"),
            obj["message"],
            obj["error"],
        )
        val text = candidates.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull }
        return text?.trim()?.takeIf { it.isNotEmpty() } ?: raw.take(MAX_ERROR_BODY_CHARS)
    }

    companion object {

        /** 认不出结构的错误原文，最多留这么多字（诊断够用，又不至于糊满屏）。 */
        private const val MAX_ERROR_BODY_CHARS = 300

        /**
         * 官方要求客户端明确标识自身，不要使用宽泛（如 okhttp/x.y）的 User-Agent。
         *
         * 版本号**从 BuildConfig 取**，不写死 —— 写死之后每次改版本都会忘记同步，
         * 服务端看到的客户端版本就成了假的（改包名 / 定 0.9.0-beta 那次就漏了）。
         */
        val USER_AGENT: String = "SimpleChat/${BuildConfig.VERSION_NAME} (Android)"

        /**
         * 本地 OkHttpClient。
         *
         * 流式响应不能设 readTimeout —— 模型思考时可能几十秒不吐一个字节，
         * 设了会被误判断线。只保留连接与写超时。
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            // 明文 HTTP 的收口要排在 UA 之前：被拦的请求没必要再加工
            .addInterceptor(NetworkPolicy.interceptor)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build(),
                )
            }
            .build()

        fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            // 必须为 true：`stream` / `thinking.type` 这类字段一旦被当作默认值省略，
            // 请求语义就完全变了（见 ChatRequestDto.stream 的注释）。
            encodeDefaults = true
            isLenient = true
            explicitNulls = false
        }
    }
}

/** 构造消息 `content`。文本 → 字符串；带图 → content block 数组。 */
object MessageContent {

    fun text(text: String): JsonElement = JsonPrimitive(text)

    /**
     * 带图片的 content。
     *
     * ⚠️ 图片**只允许出现在 user 消息中**，放在 system / assistant 会返回 400（§11.3）。
     */
    fun withImages(text: String, images: List<ImageInput>): JsonElement = buildJsonArray {
        if (text.isNotBlank()) {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", text)
                },
            )
        }
        images.forEach { image ->
            add(
                buildJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") {
                        put("url", image.dataUrl)
                        put("detail", image.detail)
                    }
                },
            )
        }
    }
}

/**
 * 图片输入。[dataUrl] 形如 `data:image/jpeg;base64,...`。
 *
 * `detail` 的实际取值由调用方决定（见 `ui/chat/RequestContent.kt` 的 `ImageDetail`）——
 * 本项目固定用 `high`：`low` 会把图缩到 512×512，截图里的小字会糊掉，
 * 而"看截图里的文字"恰恰是最常用的场景。
 */
data class ImageInput(
    val dataUrl: String,
    val detail: String = "high",
)
