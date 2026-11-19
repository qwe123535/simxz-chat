package com.simplechat.app.ui.common

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.simplechat.app.R
import com.simplechat.app.ui.theme.LocalChatColors

/**
 * 取色弹窗：色相 / 饱和度 / 明度三条滑块。
 *
 * 为什么不做调色盘（色轮 + 明度条）：手机上色轮的可点面积很小，
 * 想选一个"刚好的灰"比三条滑块难得多；而三条滑块每一条都精确、
 * 可以微调，也更容易单手操作。
 *
 * 三条滑块是 **HSV** 而不是 RGB：RGB 三个通道互相影响，
 * 调亮度会把颜色调得发白；HSV 里"亮度"就是纯亮度，直觉对得上。
 */
@Composable
fun AppColorPickerDialog(
    title: String,
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val colors = LocalChatColors.current

    // 转成 HSV 作为初始值。`remember(initial)`：外部换了一个颜色就重新取一次，
    // 否则同一个弹窗第二次打开会停在上一轮调过的位置。
    val start = remember(initial) {
        FloatArray(3).also { AndroidColor.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember(initial) { mutableStateOf(start[0]) }
    var saturation by remember(initial) { mutableStateOf(start[1]) }
    var brightness by remember(initial) { mutableStateOf(start[2]) }

    val current = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContainerShape)
                .background(colors.card)
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(current)
                        .border(1.dp, colors.outline, CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = hexOf(current),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = colors.placeholder,
                )
            }

            Spacer(Modifier.height(12.dp))

            ColorSlider(
                label = stringResource(R.string.color_picker_hue),
                value = hue,
                range = 0f..360f,
                valueText = hue.toInt().toString(),
                onValueChange = { hue = it },
            )
            ColorSlider(
                label = stringResource(R.string.color_picker_saturation),
                value = saturation,
                range = 0f..1f,
                valueText = "${(saturation * 100).toInt()}%",
                onValueChange = { saturation = it },
            )
            ColorSlider(
                label = stringResource(R.string.color_picker_brightness),
                value = brightness,
                range = 0f..1f,
                valueText = "${(brightness * 100).toInt()}%",
                onValueChange = { brightness = it },
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogAction(
                    text = stringResource(R.string.action_cancel),
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                DialogAction(
                    text = stringResource(R.string.action_save),
                    modifier = Modifier.weight(1f),
                    primary = true,
                    onClick = { onConfirm(current) },
                )
            }
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    val colors = LocalChatColors.current
    Column(modifier = Modifier.padding(bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = colors.placeholder,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun DialogAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val colors = LocalChatColors.current
    Box(
        modifier = modifier
            .clip(PillShape)
            .background(if (primary) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (primary) Color.White else colors.placeholder,
        )
    }
}

/** `#RRGGBB`（丢掉 alpha —— 这几处颜色都是不透明的，显示 alpha 只会让人困惑）。 */
private fun hexOf(color: Color): String {
    val rgb = color.toArgb() and 0xFFFFFF
    return "#" + rgb.toString(16).uppercase().padStart(6, '0')
}
