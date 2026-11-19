package com.simplechat.app.ui.chat

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * 把一段对话画成一张长图。
 *
 * 为什么不用 Compose 截图：要分享的往往是**整段**对话，屏幕上只显示了一屏，
 * 截图只能拿到看得见的部分。这里按内容重新排版，多长都能画。
 *
 * 用 `Canvas` + `StaticLayout` 而不是 Compose 的 `drawToBitmap`：
 * 后者要把界面真的组合一遍、还得先量尺寸，在后台线程上做不了；
 * 这里纯排版 + 绘制，可以在 IO 线程跑。
 *
 * 不追求和界面像素级一致 —— 长图是**分享出去给人看**的，要的是清楚，
 * 不是把 App 的样子原样搬出去。所以固定浅色底、深色字，深色模式分享出去也看得清。
 */
object ConversationImage {

    private const val WIDTH = 1080
    private const val PADDING = 56
    private const val BUBBLE_PADDING = 30
    private const val GAP = 26
    private const val RADIUS = 26f

    private val background = Color.parseColor("#FFFFFF")
    private val pageText = Color.parseColor("#1A1A1A")
    private val mutedText = Color.parseColor("#8A8A8E")
    private val userBubble = Color.parseColor("#DCE6FF")
    private val assistantBubble = Color.parseColor("#F2F2F4")
    private val divider = Color.parseColor("#E5E5E7")

    data class Turn(val text: String, val fromUser: Boolean)

    /**
     * 画图并返回文件。失败返回 null（磁盘满、内存不足都算）——
     * 调用方据此提示一句「生成长图失败」，而不是崩掉。
     */
    fun render(context: Context, title: String, turns: List<Turn>): File? = runCatching {
        if (turns.isEmpty()) return null

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pageText
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedText
            textSize = 26f
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pageText
            textSize = 34f
            // 行高靠倍数：中文行距太挤会糊成一团
            isSubpixelText = true
        }

        val contentWidth = WIDTH - PADDING * 2
        val bubbleTextWidth = contentWidth - BUBBLE_PADDING * 2

        // 先排版、后画：高度要先知道才能建 Bitmap
        val blocks = turns.map { turn ->
            val layout = StaticLayout.Builder
                .obtain(turn.text, 0, turn.text.length, bodyPaint, bubbleTextWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.32f)
                .setIncludePad(false)
                .build()
            turn to layout
        }

        val titleLayout = StaticLayout.Builder
            .obtain(title, 0, title.length, titlePaint, contentWidth)
            .setLineSpacing(0f, 1.2f)
            .build()

        var height = PADDING + titleLayout.height + 12 + 30 + 24
        blocks.forEach { (_, layout) -> height += layout.height + BUBBLE_PADDING * 2 + GAP }
        height += PADDING

        val bitmap = Bitmap.createBitmap(WIDTH, height.coerceAtLeast(320), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)

        var y = PADDING.toFloat()
        canvas.save()
        canvas.translate(PADDING.toFloat(), y)
        titleLayout.draw(canvas)
        canvas.restore()
        y += titleLayout.height + 12

        canvas.drawText(
            "${turns.size} 条消息",
            PADDING.toFloat(),
            y + 26f,
            metaPaint,
        )
        y += 30 + 24

        canvas.drawRect(
            PADDING.toFloat(),
            y,
            (WIDTH - PADDING).toFloat(),
            y + 1f,
            Paint().apply { color = divider },
        )
        y += 24

        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        blocks.forEach { (turn, layout) ->
            val bubbleHeight = layout.height + BUBBLE_PADDING * 2
            val bubbleWidth = bubbleTextWidth + BUBBLE_PADDING * 2
            val left = if (turn.fromUser) (WIDTH - PADDING - bubbleWidth).toFloat() else PADDING.toFloat()

            bubblePaint.color = if (turn.fromUser) userBubble else assistantBubble
            canvas.drawRoundRect(
                RectF(left, y, left + bubbleWidth, y + bubbleHeight),
                RADIUS,
                RADIUS,
                bubblePaint,
            )

            canvas.save()
            canvas.translate(left + BUBBLE_PADDING, y + BUBBLE_PADDING)
            layout.draw(canvas)
            canvas.restore()

            y += bubbleHeight + GAP
        }

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "chat-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        // 只保留最近几张：分享完就不需要了，攒着会一直占 cache
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }

        file
    }.getOrNull()

    /** 拉起系统分享面板。 */
    fun share(context: Context, file: File, chooserTitle: String) {
        runCatching {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle))
        }
    }
}
