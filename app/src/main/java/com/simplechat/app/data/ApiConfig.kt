package com.simplechat.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 接口配置的独立导出格式。
 *
 * ### 为什么单独一份，而不是塞进全量备份
 *
 * 全量备份是**给人看、给别的工具读**的：它刻意不带任何凭据。而这里要带 API Key
 * 的明文 —— 两者放一起，等于让每个想备份聊天记录的人都顺手把 Key 写进文件。
 *
 * 分开之后语义清楚：这份文件就是钥匙串，导出时自己决定放哪儿。
 *
 * ### 为什么要导出 Key
 *
 * 换机、重装、以及「debug 签名装不上正式包只能卸载重装」这类场景下，
 * 密文没有意义：`SecretBox` 的密钥在本机 Keystore 里，换一台机器解不开。
 * 所以导出的是明文，导入时再用新机器的密钥重新加密。
 */
@Serializable
data class ApiConfigFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long = 0,
    /** 导出时正在用的那套配置，导入后会自动切过去。 */
    val activeProfileId: String? = null,
    val profiles: List<ApiConfigProfile> = emptyList(),
) {
    companion object {
        const val FORMAT = "simxz-chat-api"
        const val VERSION = 1
    }
}

/** 一套接口配置。字段与 [ApiProfile] 一一对应，只有 Key 是明文。 */
@Serializable
data class ApiConfigProfile(
    val id: String,
    val name: String,
    val providerId: String,
    val baseUrl: String,
    val modelId: String,
    val apiKey: String = "",
    val apiStyleId: String = "AUTO",
    val modelIds: List<String> = emptyList(),
    val reasoningLevelId: String = "OFF",
    val temperature: Float = 1f,
    val topP: Float = 1f,
    val maxTokens: Int = 0,
    val webSearch: Boolean = false,
)

internal val apiConfigJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}
