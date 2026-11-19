package com.simplechat.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 应用锁的开关状态（设置 → 安全 → 应用锁）。
 *
 * 只记「现在锁没锁」这一个布尔值：真正的验证交给系统
 * （`KeyguardManager.createConfirmDeviceCredentialIntent`），
 * 所以这里不碰密码、不存指纹、也不需要任何权限。
 *
 * 为什么是全局单例而不是 ViewModel 里的状态：锁的是**整个 App**，
 * 换 Activity、重建、从后台回来都要看到同一个答案。
 */
object AppLock {

    /**
     * 当前是否处于锁定态。
     *
     * 冷启动时保持 false —— 初始上锁由 `MainActivity.onStop` 与首帧判断负责，
     * 直接在 `Application.onCreate` 里锁会让"没开应用锁"的用户也看到一帧锁屏。
     */
    var locked by mutableStateOf(false)
        private set

    fun lock() {
        locked = true
    }

    fun unlock() {
        locked = false
    }
}
