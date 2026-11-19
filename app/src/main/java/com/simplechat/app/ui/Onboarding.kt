package com.simplechat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simplechat.app.R
import com.simplechat.app.data.Res
import com.simplechat.app.data.userMessage
import com.simplechat.app.net.ChatApi
import com.simplechat.app.data.SettingsStore
import com.simplechat.app.net.ProviderKind
import com.simplechat.app.ui.common.AppIcons
import com.simplechat.app.ui.common.CapsuleButton
import com.simplechat.app.ui.common.ContainerShape
import com.simplechat.app.ui.theme.LocalChatColors
import com.simplechat.app.ui.common.MenuShape
import com.simplechat.app.ui.common.PillShape
import kotlinx.coroutines.launch

/**
 * 首次启动引导：填 Key → 选模型 → 发第一条消息。
 *
 * 只对**全新安装**出现（`StoredSettings.onboardingDone`），而且每一屏都能跳过 ——
 * 用本地模型、或者已经知道自己要干嘛的人，不该被三屏教程拦在外面。
 *
 * 三步的顺序不是随便排的：没有 Key 就拉不到模型列表，没有模型发不出消息。
 * 所以第 2 步的列表在第 1 步填完 Key 之后才去拉。
 */
@Composable
fun OnboardingScreen(
    stored: com.simplechat.app.data.StoredSettings,
    provider: ProviderKind,
    chatApi: ChatApi,
    onSaveKey: suspend (String) -> Unit,
    onSaveModel: suspend (String) -> Unit,
    onDone: suspend () -> Unit,
) {
    val colors = LocalChatColors.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(stored.modelId) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    /** 拉一次模型列表。失败不拦路 —— 手填照样能继续。 */
    suspend fun loadModels() {
        loading = true
        hint = null
        val keyNow = key.ifBlank { stored.apiKey }
        chatApi.fetchModels(
            baseUrl = stored.baseUrl.ifBlank { provider.defaultBaseUrl },
            apiKey = keyNow,
        ).onSuccess { models = it }
            .onFailure { hint = runCatching { it.userMessage() }.getOrNull() }
        loading = false
    }

    fun finish() {
        scope.launch {
            if (key.isNotBlank()) onSaveKey(key.trim())
            if (model.isNotBlank()) onSaveModel(model.trim())
            onDone()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        StepDots(step)
        Spacer(Modifier.height(28.dp))

        when (step) {
            0 -> {
                OnboardTitle(
                    title = Res.get(R.string.onboarding_key_title),
                    body = Res.get(R.string.onboarding_key_body),
                )
                Spacer(Modifier.height(20.dp))
                Field(
                    value = key,
                    onValueChange = { key = it },
                    placeholder = Res.get(R.string.onboarding_key_placeholder),
                    secret = true,
                )
                if (provider.apiKeyOptional) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = Res.get(R.string.onboarding_key_optional, provider.displayName),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.placeholder,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CapsuleButton(
                        text = Res.get(R.string.onboarding_next),
                        onClick = { step = 1 },
                        leadingIcon = null,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = Res.get(R.string.onboarding_skip),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.placeholder,
                        modifier = Modifier
                            .clip(PillShape)
                            .clickable { finish() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            1 -> {
                OnboardTitle(
                    title = Res.get(R.string.onboarding_model_title),
                    body = Res.get(R.string.onboarding_model_body),
                )
                Spacer(Modifier.height(16.dp))
                if (loading) {
                    Text(
                        text = Res.get(R.string.onboarding_model_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.placeholder,
                    )
                }
                if (models.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        models.forEach { id ->
                            ModelRow(id = id, selected = id == model) {
                                model = id
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Field(
                    value = model,
                    onValueChange = { model = it },
                    placeholder = Res.get(R.string.onboarding_model_placeholder),
                    secret = false,
                )
                hint?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.danger,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CapsuleButton(
                        text = Res.get(R.string.onboarding_next),
                        onClick = { step = 2 },
                    )
                    Spacer(Modifier.width(10.dp))
                    CapsuleButton(
                        text = Res.get(R.string.onboarding_model_fetch),
                        onClick = { scope.launch { loadModels() } },
                        enabled = !loading,
                        leadingIcon = AppIcons.Refresh,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = Res.get(R.string.onboarding_skip),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.placeholder,
                        modifier = Modifier
                            .clip(PillShape)
                            .clickable { finish() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                // 进第二屏就顺手拉一次，省掉一次点击；失败静默，用户可手动点。
                LaunchedEffect(Unit) {
                    if (models.isEmpty() && stored.modelIds.isEmpty()) loadModels()
                    if (models.isEmpty()) models = stored.modelIds
                }
            }

            else -> {
                OnboardTitle(
                    title = Res.get(R.string.onboarding_done_title),
                    body = Res.get(R.string.onboarding_done_body),
                )
                Spacer(Modifier.height(28.dp))
                CapsuleButton(
                    text = Res.get(R.string.onboarding_start),
                    onClick = { finish() },
                )
            }
        }
    }
}

@Composable
private fun StepDots(step: Int) {
    val colors = LocalChatColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            Box(
                Modifier
                    .size(if (index == step) 22.dp else 6.dp, 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (index <= step) MaterialTheme.colorScheme.primary else colors.outline,
                    ),
            )
        }
    }
}

@Composable
private fun OnboardTitle(title: String, body: String) {
    val colors = LocalChatColors.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.placeholder,
    )
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    secret: Boolean,
) {
    val colors = LocalChatColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MenuShape)
            .background(colors.fieldBackground)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.placeholder,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            visualTransformation = if (secret) {
                PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ModelRow(id: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalChatColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MenuShape)
            .background(if (selected) colors.fieldBackground else colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = id,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
