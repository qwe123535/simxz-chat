package com.simplechat.app.ui

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplechat.app.R
import com.simplechat.app.data.AppLock
import com.simplechat.app.ui.common.AppIcons
import com.simplechat.app.ui.common.CapsuleButton
import com.simplechat.app.ui.theme.LocalChatColors

/**
 * 应用锁的遮挡层。
 *
 * 关键取舍：**验证由系统界面完成**，App 只接结果。这样既不接触密码，
 * 也自动支持指纹 / 面容 / PIN 三种方式，还不用申请任何权限。
 *
 * 设备没设锁屏密码时 `createConfirmDeviceCredentialIntent` 返回 null ——
 * 此时"锁"没有意义，直接放行，而不是把用户永久挡在外面。
 */
@Composable
fun LockScreen(modifier: Modifier = Modifier) {
    val colors = LocalChatColors.current
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            AppLock.unlock()
        } else {
            message = context.getString(R.string.lock_cancelled)
        }
    }

    fun requestUnlock() {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            context.getString(R.string.lock_title),
            context.getString(R.string.lock_subtitle),
        )
        if (intent == null) {
            // 没有可用的设备凭据：锁不住，也不该卡住
            AppLock.unlock()
            return
        }
        runCatching { launcher.launch(intent) }
            .onFailure { AppLock.unlock() }
    }

    LaunchedEffect(Unit) { requestUnlock() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                imageVector = AppIcons.Lock,
                contentDescription = null,
                tint = colors.placeholder,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message ?: stringResource(R.string.lock_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = colors.placeholder,
            )
            CapsuleButton(
                text = stringResource(R.string.lock_unlock),
                onClick = { message = null; requestUnlock() },
            )
        }
    }
}
