package com.simplechat.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.simplechat.app.R
import com.simplechat.app.ui.common.AppIcons
import com.simplechat.app.ui.theme.LocalUiStyle
import com.simplechat.app.ui.theme.UiStyle

/** 顶栏：`☰` 标题 ｜ 自动朗读 · 新建对话。设置入口在抽屉底部。 */
@Composable
fun ChatTopBar(
    title: String,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    autoReadAloud: Boolean = false,
    readAloudAvailable: Boolean = true,
    onToggleAutoReadAloud: () -> Unit = {},
    /** 标题下方的次要信息，目前只用来显示分支位置。 */
    subtitle: String? = null,
) {
    val deepSeek = LocalUiStyle.current == UiStyle.DEEPSEEK
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            // 官方顶栏是 60dp，我们原版 56dp —— 差这 4dp 决定"松"还是"挤"
            .height(if (deepSeek) 60.dp else 56.dp)
            .padding(horizontal = if (deepSeek) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopBarAction(AppIcons.Menu, stringResource(R.string.topbar_history), onMenu)
        if (deepSeek) {
            // DeepSeek 的标题**居中**，两侧图标对称压边
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                TitleBlock(
                    title = title,
                    subtitle = subtitle,
                    centered = true,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
            }
        } else {
            TitleBlock(
                title = title,
                subtitle = subtitle,
                centered = false,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
        if (readAloudAvailable) {
            val label = stringResource(if (autoReadAloud) R.string.auto_read_aloud_on else R.string.auto_read_aloud_off)
            TopBarAction(
                if (autoReadAloud) AppIcons.Volume else AppIcons.VolumeOff,
                label, onToggleAutoReadAloud,
                tint = if (autoReadAloud) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                state = label,
            )
        }
        // 与长按菜单的「从这里新建对话」同一个图标 —— 同一个动作，同一个符号。
        // 圆圈加号留给输入栏的附件按钮（⊕ 加图片/文件），两者别混用。
        TopBarAction(AppIcons.MessageSquarePlus, stringResource(R.string.topbar_new_chat), onNewChat)
    }
}

/** 标题 +（可选）副标题。有副标题时才多占一行，没有时版式和以前完全一致。 */
@Composable
private fun TitleBlock(
    title: String,
    subtitle: String?,
    centered: Boolean,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = com.simplechat.app.ui.theme.LocalChatColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = title,
            style = style,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 顶栏图标按钮，48dp 触控目标。 */
@Composable
private fun TopBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    state: String? = null,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics { if (state != null) stateDescription = state }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}
