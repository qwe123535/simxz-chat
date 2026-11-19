package com.simplechat.app.data

/**
 * 应用是否在前台。
 *
 * 只为一件事存在：生成完成时判断要不要发通知 —— 用户正盯着屏幕，
 * 还弹一条「回复已生成」是打扰。
 *
 * 用 `onStart` / `onStop` 而不是 `onResume` / `onPause`：下拉通知栏、
 * 系统权限弹窗都会触发 `onPause`，那些时刻用户仍然"在 App 里"。
 */
object AppForeground {

    @Volatile
    var visible: Boolean = false
        private set

    fun update(visible: Boolean) {
        this.visible = visible
    }
}
