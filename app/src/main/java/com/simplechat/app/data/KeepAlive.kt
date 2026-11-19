package com.simplechat.app.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.PowerManager
import android.util.Log

/**
 * 「生成时别被系统掐断」的两种补充手段。
 *
 * 前台服务（[BackgroundGeneration]）已经能把进程提到较高优先级，但有两处它管不到：
 *
 * 1. **息屏后 CPU 睡眠**：前台服务不阻止设备进入挂起，`PARTIAL_WAKE_LOCK` 才会；
 * 2. **国产 ROM 的后台清理**：不少系统把"正在播放音频"当作活跃信号，
 *    这也是很多播放器能在后台活下来的原因 —— 于是有一个"播放静音"的开关。
 *
 * 两者都默认关：它们要额外权限 / 额外耗电，而绝大多数人不需要。
 * 全部失败也**不报错**：这是锦上添花，不是功能前置条件。
 */
internal object KeepAlive {

    private const val TAG = "SimpleChat/KeepAlive"
    private const val WAKE_LOCK_TAG = "SimpleChat:keepalive"

    /** 唤醒锁的保险时长，正常路径会主动释放，这个上限只防"忘了释放"。 */
    private const val WAKE_LOCK_TIMEOUT_MS = 30L * 60L * 1000L

    private var wakeLock: PowerManager.WakeLock? = null
    private var track: AudioTrack? = null
    private var audioThread: Thread? = null

    /** 按当前设置同步。幂等：状态没变就什么都不做。 */
    fun sync(context: Context, wakeLock: Boolean, silentAudio: Boolean) {
        syncWakeLock(context, wakeLock)
        syncSilentAudio(silentAudio)
    }

    fun stopAll() {
        releaseWakeLock()
        stopSilentAudio()
    }

    private fun syncWakeLock(context: Context, enabled: Boolean) {
        if (!enabled) {
            releaseWakeLock()
            return
        }
        if (wakeLock?.isHeld == true) return
        runCatching {
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }.onFailure { Log.w(TAG, "wake lock failed: ${it.javaClass.simpleName}") }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        wakeLock = null
    }

    /**
     * 播放静音。
     *
     * 用 `AudioTrack` 写零而不是找一个静音音频文件：不用带资源、不用解码，
     * 8kHz 单声道 16bit 每秒只有 16KB 的写入，对耗电的影响可以忽略。
     */
    private fun syncSilentAudio(enabled: Boolean) {
        if (!enabled) {
            stopSilentAudio()
            return
        }
        if (track != null) return
        runCatching {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(BUFFER_BYTES)

            val created = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSize)
                .build()
            created.play()
            track = created

            val silence = ByteArray(BUFFER_BYTES)
            audioThread = Thread {
                runCatching {
                    while (!Thread.currentThread().isInterrupted && track === created) {
                        created.write(silence, 0, silence.size)
                        // 睡够"刚写完这段"的时间，别把 CPU 占满
                        Thread.sleep(20)
                    }
                }
            }.apply {
                isDaemon = true
                name = "SimpleChat-silent-audio"
                start()
            }
        }.onFailure { Log.w(TAG, "silent audio failed: ${it.javaClass.simpleName}") }
    }

    private fun stopSilentAudio() {
        val current = track ?: return
        track = null
        audioThread?.interrupt()
        audioThread = null
        runCatching {
            current.stop()
            current.release()
        }
    }

    private const val SAMPLE_RATE = 8_000
    private const val BUFFER_BYTES = 800
}
