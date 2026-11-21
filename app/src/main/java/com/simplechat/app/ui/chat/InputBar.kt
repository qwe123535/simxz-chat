package com.simplechat.app.ui.chat

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import com.simplechat.app.R
import com.simplechat.app.ui.common.ContainerShape
import com.simplechat.app.ui.common.layeredShadow
import com.simplechat.app.ui.common.AppIcons
import com.simplechat.app.ui.common.AppShadow
import com.simplechat.app.data.TokenEstimate
import com.simplechat.app.data.Res
import com.simplechat.app.ui.common.IconCircleButton
import com.simplechat.app.ui.common.rememberHaptics
import com.simplechat.app.ui.theme.LocalUiStyle
import com.simplechat.app.ui.theme.UiStyle
import com.simplechat.app.ui.common.MotionEasing
import com.simplechat.app.ui.common.PillShape
import com.simplechat.app.ui.common.ProgressRing
import com.simplechat.app.ui.theme.LocalChatColors
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow

/**
 * 输入区。
 *
 * 第二行布局（对齐 DeepSeek，但做了本项目自己的扩展）：
 *
 * ```
 * [ ⚙ ] [ 深度思考 ⌄ ]  ………………  [ ⊕ ] [ ↑/■ ]
 *   对话设置   思考档位+模型            附件   发送/停止
 * ```
 *
 * 两个入口都会在输入栏下方展开面板（与选图面板同一机制），输入栏随之上移。
 */
@Composable
fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    thinkingEnabled: Boolean,
    thinkingPanelOpen: Boolean,
    /**
     * 智能搜索是否开启。
     *
     * ✦ 是「这一轮怎么生成」的唯一入口（思考档位与联网搜索都在面板里），
     * 所以两者**任一开启**都算激活 —— 否则开了搜索、星星还是黑的，用户会以为没生效。
     */
    webSearchEnabled: Boolean = false,
    onOpenThinkingPanel: () -> Unit,
    onOpenSettingsPanel: () -> Unit,
    /** 触感反馈：发送时轻震（见 `StoredSettings.hapticSend`）。 */
    hapticSend: Boolean = false,
    /** 触感反馈：长按输入框开面板时轻震。 */
    hapticLongPress: Boolean = false,
    /** 回车直接发送（见 `StoredSettings.sendOnEnter`）。 */
    sendOnEnter: Boolean = false,
    streaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    /** 附件入口是否显示。关掉是为了不给"点了没反应"的控件留位置。 */
    canAttach: Boolean = true,
    /** 点 ⊕：由调用方展开附件面板（图片 / 纯文本文件）。 */
    onAttachClick: () -> Unit,
    /** 附件面板是否展开 —— 决定 ⊕ 要不要转成 ✕。 */
    attachPanelOpen: Boolean = false,
    /** 已挂上的附件数量。>0 时即使没打字也允许发送。 */
    pendingCount: Int = 0,
    onTextFieldFocused: () -> Unit = {},
    /** 非空即处于「修改输入」态：顶部显示该标题 + 取消按钮。 */
    editingLabel: String? = null,
    onCancelEdit: () -> Unit = {},
    /** 下方有面板展开时，输入栏的投影淡出（两块合看成一体）。 */
    panelExpanded: Boolean = false,
    /** 上下文占用。圆环 + 长度，点开是统计面板。 */
    contextStats: ContextStats,
    statsPanelOpen: Boolean,
    onOpenStatsPanel: () -> Unit,
    /** 设备有没有可用的语音识别服务。没有就不露麦克风。 */
    showVoice: Boolean = false,
    /** 是否正在录音 —— 按钮换成"结束"状态。 */
    voiceListening: Boolean = false,
    /**
     * 语音模式：输入框整体换成「按住 说话」长条。
     *
     * 与 [voiceListening] 是两件事：前者是"当前处于语音模式"，后者是"手指正按着"。
     */
    voiceMode: Boolean = false,
    onToggleVoice: () -> Unit = {},
    /**
     * 是否开启「长按输入框说话」。
     *
     * 由调用方决定：**设备有识别服务 + 已授权 + 扩展通道可用** 时才为 true。
     * 关掉时输入框一个手势都不多挂。
     */
    holdToTalkEnabled: Boolean = false,
    /** 长按开始（手指按住不放）。 */
    onVoiceHoldStart: () -> Unit = {},
    /** 松手结束。 */
    onVoiceHoldEnd: () -> Unit = {},
    /** 按住后上滑取消：不发送、丢弃识别结果。 */
    onVoiceHoldCancel: () -> Unit = {},
    /** 外部请求「现在发送一次」。值一变就触发，复用同一条发送路径。 */
    sendSignal: Int = 0,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChatColors.current
    val shape = ContainerShape
    /*
     * DeepSeek 皮肤的输入栏是**填充色块**：灰底、无描边、无投影。
     * 我们原版是白卡片 + 描边 + 投影 —— 那是两套不同的语言，不能只换颜色。
     */
    val deepSeek = LocalUiStyle.current == UiStyle.DEEPSEEK
    val haptics = rememberHaptics()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    /**
     * 「空框 + 有语音能力」才挂长按热区。
     *
     * 有字时长按要让位给**选词** —— 那是文本框自己的默认语义，抢掉它编辑会变得很难用。
     */
    val holdToTalkActive = holdToTalkEnabled && text.isEmpty()

    /*
     * 进语音模式时收起键盘：长条没有光标，键盘留着只会把界面顶上去、
     * 还挡住下面的附件面板。
     */
    val focusManager = LocalFocusManager.current
    LaunchedEffect(voiceMode) {
        if (voiceMode) {
            runCatching {
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }
    }

    // 语音说完自动发送：外部把 sendSignal 改一下，这里复用同一条发送路径
    LaunchedEffect(sendSignal) {
        if (sendSignal > 0) onSend()
    }

    // 面板展开 → 投影淡出；收起 → 淡入。否则接缝处会同时压着两条阴影。
    val shadowElevation by animateDpAsState(
        targetValue = if (panelExpanded) 0.dp else AppShadow.PanelElevation,
        animationSpec = tween(durationMillis = 180),
        label = "inputBarShadow",
    )

    /*
     * 进入编辑态时自动聚焦并**拉起输入法**。
     *
     * 只调 requestFocus() 是不够的 —— 它只给焦点，系统输入法不会自己出来，
     * 表现为「点了消息、光标在闪、但键盘没弹」。必须显式 show()，
     * 且要等一帧让焦点真正落到输入框上，否则 show() 会被忽略。
     */
    LaunchedEffect(editingLabel) {
        if (editingLabel == null) return@LaunchedEffect
        runCatching {
            focusRequester.requestFocus()
            withFrameNanos { }
            keyboard?.show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 与菜单同一个投影族：先 shadow 再 clip，投影才不会被自家圆角裁掉。
                // 双层（宽 + 贴边）—— 单层只有一种扩散，大面板的边缘会发虚。
                .layeredShadow(
                    shape = ContainerShape,
                    elevation = if (deepSeek) 0.dp else shadowElevation,
                    ambient = AppShadow.PanelAmbient,
                    spot = AppShadow.PanelSpot,
                    contactElevation = shadowElevation / 4,
                    contactColor = AppShadow.PanelContactColor,
                )
                .clip(shape)
                .background(if (deepSeek) colors.fieldBackground else colors.card)
                /*
                 * 按住说话：挂在**整条输入栏**上，而不是输入框上。
                 *
                 * 挂在输入框上会跟文本框自己的长按（选词 / 光标）打架 ——
                 * 那正是"打字和说话互相抢"的来源。这里只在**空输入框**时响应，
                 * 且**不消费**短按：点一下照常聚焦、光标照常落位。
                 */
                .pointerInput(holdToTalkActive) {
                    if (!holdToTalkActive) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        // 先松手就是普通点击，交给文本框
                        val releasedEarly = withTimeoutOrNull(
                            viewConfiguration.longPressTimeoutMillis,
                        ) { waitForUpOrCancellation() }
                        if (releasedEarly != null) return@awaitEachGesture
                        onVoiceHoldStart()
                        waitForUpOrCancellation()
                        onVoiceHoldEnd()
                    }
                }
                .border(1.dp, if (deepSeek) Color.Transparent else colors.outline, shape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // ── 「修改输入」标题行 ─────────────────────────
            if (editingLabel != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = editingLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.placeholder,
                        modifier = Modifier.weight(1f),
                    )
                    IconCircleButton(
                        icon = AppIcons.Close,
                        contentDescription = stringResource(R.string.input_cancel_edit),
                        onClick = onCancelEdit,
                        size = 26.dp,
                        iconSize = 14.dp,
                        tint = colors.processText,
                    )
                }
            }

            /*
             * 光标：**外部换文本**（恢复草稿 / 切会话）时放到末尾 —— 那是"接着往下写"；
             * 用户自己打字时光标不动。
             *
             * `BasicTextField` 的 `String` 重载不保证换文本后光标落在哪，这里自己钉住。
             */
            var fieldValue by remember { mutableStateOf(TextFieldValue(text = text)) }
            LaunchedEffect(text) {
                if (fieldValue.text != text) {
                    fieldValue = TextFieldValue(text = text, selection = TextRange(text.length))
                }
            }
            if (voiceMode) {
                VoiceHoldBar(
                    listening = voiceListening,
                    enabled = true,
                    onStart = onVoiceHoldStart,
                    onStop = onVoiceHoldEnd,
                    onCancel = onVoiceHoldCancel,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else BasicTextField(
                value = fieldValue,
                onValueChange = {
                    fieldValue = it
                    if (it.text != text) onTextChange(it.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 26.dp, max = 160.dp)
                    .focusRequester(focusRequester)
                    /*
                     * 按住说话：只挂在**空输入框**上，且不消费短按 ——
                     * 点一下照常聚焦、光标照常落位，打字与说话不会互相抢。
                     */
                    .pointerInput(holdToTalkActive) {
                        if (!holdToTalkActive) return@pointerInput
                        val cancelSlop = VoiceCancelSlop.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val releasedEarly = withTimeoutOrNull(
                                viewConfiguration.longPressTimeoutMillis,
                            ) { waitForUpOrCancellation() }
                            if (releasedEarly != null) return@awaitEachGesture

                            onVoiceHoldStart()
                            /*
                             * 按住期间向上滑超过 [VoiceCancelSlop] 就算取消 ——
                             * 松手时不发送、也不保留识别结果。这是"说错了想撤回"
                             * 唯一的即时出口：不给它，用户只能等发出去再删。
                             */
                            var cancelled = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (down.position.y - change.position.y > cancelSlop) {
                                    cancelled = true
                                }
                                change.consume()
                            }
                            if (cancelled) onVoiceHoldCancel() else onVoiceHoldEnd()
                        }
                    }
                    /*
                     * 长按输入框 → 对话设置面板。
                     *
                     * 只在**输入框为空**时接管：
                     * - 有文字时长按是选词 / 移光标，抢过来会让最常用的编辑手势失灵；
                     * - 空框时长按本来什么也不会发生，借它当入口不损失任何东西。
                     * 按住说话开启时（也是空框）让位给说话 —— 那条路径更要紧。
                     */
                    .pointerInput(holdToTalkActive, text.isEmpty(), hapticLongPress) {
                        if (holdToTalkActive || text.isNotEmpty()) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val releasedEarly = withTimeoutOrNull(
                                viewConfiguration.longPressTimeoutMillis,
                            ) { waitForUpOrCancellation() }
                            if (releasedEarly != null) return@awaitEachGesture
                            if (hapticLongPress) haptics.tick()
                            onOpenSettingsPanel()
                            // 吃掉剩下这一段：否则松手会被当成点击，面板刚开就被键盘顶走
                            waitForUpOrCancellation()
                        }
                    }
                    // 用户点回输入框时收起面板，避免键盘与面板同时占屏
                    .onFocusChanged { if (it.isFocused) onTextFieldFocused() },
                /*
                 * 回车发送（设置 → 触感与交互）。
                 *
                 * 默认**不开**：手机输入法上回车键本来就常被用来换行，聊天框里
                 * 换行也很常见。打开后把 IME 的动作键改成「发送」，
                 * 回车就走发送而不是插入换行。
                 */
                keyboardOptions = if (sendOnEnter) {
                    KeyboardOptions(imeAction = ImeAction.Send)
                } else {
                    KeyboardOptions.Default
                },
                keyboardActions = if (sendOnEnter) {
                    KeyboardActions(onSend = { if (text.isNotBlank() && !streaming) onSend() })
                } else {
                    KeyboardActions.Default
                },
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 6,
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                text = when {
                                    voiceListening -> stringResource(R.string.voice_listening)
                                    holdToTalkActive -> stringResource(R.string.input_hint_hold_to_talk)
                                    else -> stringResource(R.string.input_hint)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (voiceListening) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    colors.placeholder
                                },
                            )
                        }
                        inner()
                    }
                },
            )

            Spacer(Modifier.size(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 第二行全部按钮**同高**；纯图标一律正圆，只有「图标 + 箭头」用胶囊。
                // 之前 ⚙ 是 padding 撑出来的椭圆，和 ⊕ / ↑ 的圆并排就很毛糙。

                /*
                 * 原来这里有一个 ⚙「对话设置」按钮。
                 *
                 * 删掉的原因：底部标签栏已经有了设置，抽屉底部那行也删了，
                 * 这里是第三个入口 —— 一排按钮里最不该多出来的就是"又一个设置"。
                 * 面板本身没删，改成**长按输入框**打开（见下面的 BasicTextField）。
                 */

                // ── 思考档位 + 模型 ───────────────────────
                ThinkingToggle(
                    enabled = thinkingEnabled || webSearchEnabled,
                    expanded = thinkingPanelOpen,
                    onClick = onOpenThinkingPanel,
                )
                // ── 上下文占用 ────────────────────────────
                ContextMeterPill(
                    stats = contextStats,
                    expanded = statsPanelOpen,
                    onClick = onOpenStatsPanel,
                )

                Spacer(Modifier.weight(1f))

                /*
                 * 附件入口。图标是**圆圈加号**（⊕），与顶栏的「新建对话」区分开 ——
                 * 后者是气泡加号。同一个符号不要承担两种动作。
                 *
                 * 展开时整体转 45°：加号变成叉，"再点一下是收起"就不用另外说明。
                 * 转的是**整个按钮** —— 圆形是对称的，看起来就是里头的加号在转，
                 * 比"两帧之间换一个图标"顺得多。
                 */
                if (canAttach) {
                    val spin by animateFloatAsState(
                        targetValue = if (attachPanelOpen) 45f else 0f,
                        animationSpec = tween(200, easing = MotionEasing),
                        label = "attachSpin",
                    )
                    IconCircleButton(
                        icon = AppIcons.PlusCircle,
                        contentDescription = if (attachPanelOpen) {
                            stringResource(R.string.input_attach_collapse)
                        } else {
                            stringResource(R.string.input_attach_add)
                        },
                        onClick = onAttachClick,
                        size = RowButtonSize,
                        iconSize = 24.dp,
                        modifier = Modifier.graphicsLayer { rotationZ = spin },
                    )
                }

                SendButton(
                    streaming = streaming,
                    enabled = streaming || text.isNotBlank() || pendingCount > 0,
                    onClick = {
                        if (hapticSend) haptics.tick()
                        if (streaming) onStop() else onSend()
                    },
                )
            }
        }
    }
}

/** 输入栏第二行的统一控件高度。 */
private val RowButtonSize = 32.dp

/** 按住说话时，上滑超过这个距离即视为取消。 */
private val VoiceCancelSlop = 56.dp

/**
 * 思考档位入口。
 *
 * 只留**图标 + 箭头**，不带文字 —— 输入栏第二行本来就窄，
 * 图标（Bubble/Brain）已足够表意，箭头单独承担"可展开"的提示。
 *
 * [enabled] 表示思考已开启；[expanded] 表示面板正在展开 ——
 * 两者独立，因为「面板打开」和「思考已开启」是两件事。
 */
@Composable
private fun ThinkingToggle(
    enabled: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalChatColors.current
    // 未开启也画**黑**（原来是灰的），与 ⊕ / 发送键统一
    val content = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .height(RowButtonSize)
            .clip(PillShape)
            .background(if (enabled) colors.capsuleSelected else Color.Transparent)
            /*
             * 开启态**保留描边**，只换成品牌色 —— 官方那套激活样式是
             * 「淡底 + 同色描边 + 同色内容」。原来激活时把描边设成透明，
             * 并排看会比旁边几个"矮一层"。
             */
            .border(
                width = 1.dp,
                color = if (enabled) MaterialTheme.colorScheme.primary else colors.outline,
                shape = PillShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = AppIcons.Sparkles,
            contentDescription = stringResource(R.string.input_thinking),
            tint = content,
            modifier = Modifier.size(17.dp),
        )
        Icon(
            imageVector = AppIcons.ChevronDown,
            contentDescription = null,
            tint = content,
            // 展开时箭头翻转，给出明确的方向反馈
            modifier = Modifier
                .size(14.dp)
                .rotate(if (expanded) 180f else 0f),
        )
    }
}

/**
 * 智能搜索入口。
 *
 * 与 [ThinkingToggle] 同一套视觉（同高、同圆角、同激活样式），
 * 但**没有箭头**：它是个开关，不是面板入口 —— 点了就开/关，没有下一层。
 *
 * 开启态用「淡底 + 品牌色描边 + 品牌色图标」，与旁边的思考档位一致；
 * 关闭态用描边 + 黑色图标，与 ⊕ / 发送键一致。
 */
@Composable
private fun WebSearchToggle(enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalChatColors.current
    val content = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val label = stringResource(R.string.web_search)

    Row(
        modifier = Modifier
            .height(RowButtonSize)
            .clip(PillShape)
            .background(if (enabled) colors.capsuleSelected else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (enabled) MaterialTheme.colorScheme.primary else colors.outline,
                shape = PillShape,
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
                stateDescription = if (enabled) {
                    Res.get(R.string.web_search_on)
                } else {
                    Res.get(R.string.web_search_off)
                }
            }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AppIcons.Globe,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(17.dp),
        )
    }
}

/**
 * 上下文占用入口：圆环 + 长度（如 `12k`）。
 *
 * 颜色按占用分三档，让"该压缩了"在**不点开面板**的情况下也看得见：
 * 常规 / 接近阈值（≥70%）/ 逼近上限（≥90%）。
 *
 * 压缩生效时环上会出现一个品牌色的小标，提示"发出去的和本地看到的不一样"。
 */
@Composable
private fun ContextMeterPill(
    stats: ContextStats,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalChatColors.current
    val accent = when {
        stats.ratio >= 0.9f -> colors.danger
        stats.ratio >= 0.7f -> colors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    val content = when {
        expanded -> MaterialTheme.colorScheme.primary
        // 接近阈值 / 逼近上限：数字跟着变色。这个信号只留在环上太容易被忽略
        stats.ratio >= 0.7f -> accent
        // 常规态也画**黑**（原来是灰的），与旁边几个按钮统一
        else -> MaterialTheme.colorScheme.onSurface
    }
    val contextUsageLabel = stringResource(R.string.stats_context_usage)

    Row(
        modifier = Modifier
            .height(RowButtonSize)
            .clip(PillShape)
            .background(if (expanded) colors.capsuleSelected else Color.Transparent)
            // 展开时描边换成品牌色（与 ⚙ / ✧ 同一套激活样式）
            .border(
                width = 1.dp,
                color = if (expanded) MaterialTheme.colorScheme.primary else colors.outline,
                shape = PillShape,
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = contextUsageLabel
                role = Role.Button
            }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ProgressRing(
            progress = stats.ratio,
            color = accent,
            trackColor = colors.outline,
            size = 15.dp,
            strokeWidth = 2.dp,
        )
        Text(
            text = TokenEstimate.format(stats.usedTokens),
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
        if (stats.hasCompaction) {
            // 一个极小的小圆点：说明"上下文已被压缩过"
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * 发送按钮。三态：
 * - 空闲且无内容：浅灰，不可点
 * - 有内容：品牌蓝实心，向上箭头
 * - 流式中：品牌蓝实心，停止方块
 */
@Composable
private fun SendButton(
    streaming: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalChatColors.current
    val background = if (enabled) MaterialTheme.colorScheme.primary else colors.fieldBackground
    val content = if (enabled) Color.White else colors.placeholder

    Box(
        modifier = Modifier
            .size(RowButtonSize)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (streaming) AppIcons.Stop else AppIcons.ArrowUp,
            contentDescription = if (streaming) stringResource(R.string.input_stop) else stringResource(R.string.input_send),
            tint = content,
            modifier = Modifier.size(if (streaming) 16.dp else 19.dp),
        )
    }
}

/** 无背景的圆形图标按钮，48dp 触控目标。等价于 [IconCircleButton] 的大尺寸预设。 */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    modifier: Modifier = Modifier,
) {
    IconCircleButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        size = 48.dp,
        iconSize = 24.dp,
        tint = tint,
    )
}

/**
 * 「按住 说话」长条。
 *
 * 为什么是长条而不是小图标：说话是**持续动作**，按钮只表示"一个动作的开关"，
 * 用户得先点开、再说、再点结束 —— 中间那一步很容易漏。长条把"按住 → 说 → 松开"
 * 压成一个连续手势，松手即发。
 *
 * 上滑取消沿用语音输入的通用约定：手指上移超过 [VoiceCancelSlop] 就丢弃这次录音，
 * 这样"说错了"不需要再去找取消按钮。
 */
@Composable
private fun VoiceHoldBar(
    listening: Boolean,
    enabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChatColors.current
    val density = LocalDensity.current
    val cancelSlop = with(density) { VoiceCancelSlop.toPx() }
    var pressing by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    val active = pressing || listening
    val text = when {
        cancelled -> stringResource(R.string.voice_hold_release_cancel)
        active -> stringResource(R.string.voice_hold_release_send)
        else -> stringResource(R.string.voice_hold_to_talk)
    }
    val tint = when {
        cancelled -> colors.danger
        active -> MaterialTheme.colorScheme.primary
        else -> colors.placeholder
    }

    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(PillShape)
            .background(if (active) colors.fieldBackground else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (cancelled) colors.danger else colors.outline,
                shape = PillShape,
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressing = true
                    cancelled = false
                    haptics.tick()
                    onStart()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.position.y - down.position.y < -cancelSlop && !cancelled) {
                            cancelled = true
                            haptics.tick()
                        }
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                    }
                    pressing = false
                    if (cancelled) onCancel() else onStop()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
